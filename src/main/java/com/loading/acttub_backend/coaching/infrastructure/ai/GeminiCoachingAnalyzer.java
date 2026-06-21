package com.loading.acttub_backend.coaching.infrastructure.ai;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loading.acttub_backend.coaching.application.model.CoachingInput;
import com.loading.acttub_backend.coaching.application.model.VideoInput;
import com.loading.acttub_backend.coaching.application.port.CoachingAnalysisTimeoutException;
import com.loading.acttub_backend.coaching.application.port.CoachingAnalyzer;
import com.loading.acttub_backend.coaching.domain.CoachFeedback;
import com.loading.acttub_backend.coaching.domain.CoachingAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "app.ai.coaching.stub-enabled", havingValue = "false", matchIfMissing = true)
public class GeminiCoachingAnalyzer implements CoachingAnalyzer {

	private static final Logger log = LoggerFactory.getLogger(GeminiCoachingAnalyzer.class);

	private final ObjectMapper objectMapper;
	private final RestClient restClient;
	private final String apiKey;
	private final String model;
	private final BigDecimal temperature;
	private final String promptVersion;
	private final long fileProcessingTimeoutMillis;

	public GeminiCoachingAnalyzer(
			ObjectMapper objectMapper,
			@Value("${app.ai.coaching.gemini.api-key}") String apiKey,
			@Value("${app.ai.coaching.gemini.base-url}") String baseUrl,
			@Value("${app.ai.coaching.gemini.model}") String model,
			@Value("${app.ai.coaching.gemini.temperature}") BigDecimal temperature,
			@Value("${app.ai.coaching.gemini.prompt-version}") String promptVersion,
			@Value("${app.ai.coaching.gemini.file-processing-timeout-millis}") long fileProcessingTimeoutMillis
	) {
		this.objectMapper = objectMapper;
		this.apiKey = apiKey;
		this.model = model;
		this.temperature = temperature;
		this.promptVersion = promptVersion;
		this.fileProcessingTimeoutMillis = fileProcessingTimeoutMillis;
		this.restClient = RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory())
				.build();
	}

	@Override
	public CoachingAnalysisResult analyze(VideoInput video, CoachingInput input) {
		requireApiKey();
		UploadedFile uploadedFile = null;
		try {
			uploadedFile = upload(video);
			waitUntilActive(uploadedFile);
			String response = generate(uploadedFile, input);
			return new CoachingAnalysisResult("gemini", model, temperature, promptVersion, parseFeedback(response));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new CoachingAnalysisTimeoutException("Interrupted while waiting for Gemini file processing.", e);
		} catch (ResourceAccessException e) {
			throw new CoachingAnalysisTimeoutException("Gemini analysis timed out.", e);
		} catch (RestClientResponseException e) {
			throw new IllegalStateException("Gemini analysis request failed: " + e.getResponseBodyAsString(), e);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read Gemini analysis response.", e);
		} catch (RestClientException e) {
			throw new IllegalStateException("Gemini analysis request failed.", e);
		} finally {
			deleteFile(uploadedFile);
		}
	}

	private UploadedFile upload(VideoInput video) throws IOException {
		ResponseEntity<String> startResponse = restClient.post()
				.uri("/upload/v1beta/files")
				.header("x-goog-api-key", apiKey)
				.header("X-Goog-Upload-Protocol", "resumable")
				.header("X-Goog-Upload-Command", "start")
				.header("X-Goog-Upload-Header-Content-Length", String.valueOf(video.sizeBytes()))
				.header("X-Goog-Upload-Header-Content-Type", video.contentType())
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("file", Map.of("display_name", displayName(video))))
				.retrieve()
				.toEntity(String.class);

		String uploadUrl = startResponse.getHeaders().getFirst("x-goog-upload-url");
		if (uploadUrl == null || uploadUrl.isBlank()) {
			throw new IllegalStateException("Gemini file upload URL was not returned.");
		}

		String fileResponse;
		try (InputStream inputStream = video.openStream()) {
			fileResponse = restClient.post()
					.uri(uploadUrl)
					.header(HttpHeaders.CONTENT_LENGTH, String.valueOf(video.sizeBytes()))
					.header("X-Goog-Upload-Offset", "0")
					.header("X-Goog-Upload-Command", "upload, finalize")
					.body(new VideoInputResource(inputStream, video.sizeBytes(), displayName(video)))
					.retrieve()
					.body(String.class);
		}

		JsonNode file = objectMapper.readTree(fileResponse).path("file");
		return new UploadedFile(
				file.path("name").asText(),
				file.path("uri").asText(),
				file.path("mimeType").asText(video.contentType())
		);
	}

	private void waitUntilActive(UploadedFile uploadedFile) throws IOException, InterruptedException {
		long deadline = System.currentTimeMillis() + fileProcessingTimeoutMillis;
		String lastState = "";
		while (System.currentTimeMillis() < deadline) {
			JsonNode file = getFile(uploadedFile.name());
			String state = file.path("state").asText();
			if ("ACTIVE".equals(state)) {
				return;
			}
			if ("FAILED".equals(state)) {
				throw new IllegalStateException("Gemini file processing failed.");
			}
			lastState = state;
			Thread.sleep(2_000L);
		}
		throw new CoachingAnalysisTimeoutException("Gemini file processing timed out. lastState=" + lastState, null);
	}

	private JsonNode getFile(String name) throws IOException {
		String response = restClient.get()
				.uri("/v1beta/" + name)
				.header("x-goog-api-key", apiKey)
				.retrieve()
				.body(String.class);
		JsonNode root = objectMapper.readTree(response);
		if (root.has("file")) {
			return root.path("file");
		}
		return root;
	}

	private void deleteFile(UploadedFile uploadedFile) {
		if (uploadedFile == null || uploadedFile.name() == null || uploadedFile.name().isBlank()) {
			return;
		}
		try {
			restClient.delete()
					.uri("/v1beta/" + uploadedFile.name())
					.header("x-goog-api-key", apiKey)
					.retrieve()
					.toBodilessEntity();
		} catch (RestClientException e) {
			log.warn("Failed to delete Gemini uploaded file: {}", uploadedFile.name(), e);
		}
	}

	private String generate(UploadedFile uploadedFile, CoachingInput input) {
		return restClient.post()
				.uri("/v1beta/models/{model}:generateContent", model)
				.header("x-goog-api-key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of(
						"contents", List.of(Map.of(
								"role", "user",
								"parts", List.of(
										Map.of("file_data", Map.of(
												"mime_type", uploadedFile.mimeType(),
												"file_uri", uploadedFile.uri()
										)),
										Map.of("text", prompt(input))
								)
						)),
						"generationConfig", Map.of(
								"temperature", temperature,
								"responseMimeType", "application/json"
						)
				))
				.retrieve()
				.body(String.class);
	}

	private CoachFeedback parseFeedback(String response) throws IOException {
		String text = responseText(response);
		JsonNode root = objectMapper.readTree(stripJsonFence(text));
		return new CoachFeedback(
				new CoachFeedback.OverallStrength(requiredText(root, "overallStrength", "text")),
				requiredFeedbackCards(root)
		);
	}

	private String responseText(String response) throws IOException {
		JsonNode root = objectMapper.readTree(response);
		JsonNode text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
		if (text.isMissingNode() || text.asText().isBlank()) {
			throw new IllegalStateException("Gemini response did not contain text.");
		}
		return text.asText();
	}

	private String prompt(CoachingInput input) {
		return """
				당신은 배우를 돕는 수석 연기 코치입니다. 연기 영상 하나와 배우가 알려준 설정을 받아, 배우가 바로 읽을 피드백 카드 하나를 만듭니다.

				가장 중요한 원칙: 출력은 영상에서 "실제로 보이고 들린 것"에만 근거합니다. 추측, 어림짐작한 시간, 지어낸 대사, 일반론("보통 이런 장면에서는")은 카드 전체의 신뢰를 무너뜨립니다. 확실하지 않으면 적지 않습니다 — 적게 적더라도 맞는 것만.

				[목표 — 이 작업의 결과물]
				배우가 이 카드를 읽고 다음 테이크에서 바로 하나의 행동을 바꿀 수 있게 만듭니다. 좋은 카드는 두 가지를 동시에 합니다.
				- (호감) 배우가 "이 코치가 내 연기를 정확히 봤다"고 느끼게 한다 — 구체적인 관찰과 진짜 강점으로.
				- (성장) 듣기 편한 방향이 아니라 배우에게 실제로 필요한 방향을 준다 — 증거가 가리키는 쪽으로.
				좋은 카드는 많이 분석한 카드가 아니라, 영상 근거가 분명하고 처방이 즉시 실행되는 카드입니다. 이어지는 모든 관찰과 진단은 오직 이 한 장의 코치 카드를 만들기 위한 내부 작업입니다.

				최종 결과물은 아래를 모두 갖춘 피드백 카드 하나입니다.
				- 충돌 1개: 배우의 설계(상황·인물·서브텍스트)가 실제 연기에서 가장 덜 살아난 지점 하나.
				- 관찰 근거: 그 충돌이 영상의 어느 순간에 보이는지(지어내지 않은 사실).
				- 실행가능한 처방: 다음 테이크에 글만 보고 따라할 수 있는 행동 훈련.
				- 강점 1개: 영상 전반에서 잘 작동한 선택 1개(근거 없으면 격려).
				- 배우가 다시 찍고 싶어지는 코치의 톤.

				[입력] — 배우가 알려준 설정입니다. 영상에서 추론해 바꾸지 말고 판단 기준으로 그대로 씁니다.
				- 매체/장르: %s     (결함 기준 보정: 연극·뮤지컬=객석 끝까지 전달이 기본 / 영화·드라마=절제·미세표정이 정답 / 기타·공백=보정 없음)
				- 상황: %s       (배우가 정한 장면 = 주어진 설정)
				- 인물 설정: %s  (배우가 정한 인물 = 주어진 설정)
				- 서브텍스트(속마음): %s   (이 장면의 목표. 비어 있으면 상황·인물에서 추론)
				- 영상: 첨부됨

				입력이 비어 있음을 나타내는 문구("비어 있음", "추론" 등)는 실제 배우 설정으로 간주하지 않습니다. 그런 문구는 판단 기준이 아니라 결측 표시입니다.

				[1단계 — 관찰] → observations
				영상을 처음부터 끝까지 시간 순서로 훑되, 카드 판단에 실제로 쓰일 수 있는 핵심 관찰만 적습니다. 목표는 많은 관찰이 아니라 정확한 관찰입니다. 보통 6~9개. 충돌을 뒷받침할 관찰 2~3개와 강점을 뒷받침할 관찰이 있으면 더 채우려고 만들지 않습니다.

				진단 방향을 한쪽으로 치우치지 않게 하려면, 두 종류를 모두 봅니다.
				- 표면(가면): 장면 내내 지속되는 표현 경향(예: 처음부터 끝까지 유지된 밝은 미소·톤).
				- 누수: 그 표면이 깨지고 속이 새어나온 순간(예: 한숨·가슴치기·갑자기 높아진 톤·찌푸림).
				둘 중 하나만 보면 같은 연기를 정반대로 진단하게 됩니다. 표면과 누수를 모두 관찰에 남깁니다.

				4축(①정서 신호 ②대사 전달(발음·음량·말끝·속도·호흡) ③표정·시선 ④몸(자세·움직임))에서 실제로 보이거나 들린 사실만 시간순으로 적습니다. 이건 뒤의 모든 판단이 딛고 설 공통 근거입니다.
				각 observation = { timecode, axis(감정|대사|표정|몸), fact }.
				- 해석어("긴장한", "불안한", "위축된")를 쓰지 않는다 → 몸·소리·표정의 사실만 쓴다. 예: "어깨가 귀 쪽으로 올라감", "끝음절 '발'의 음량이 줄어 거의 안 들림".
				- 영상에서 실제로 지목 가능한 timecode(M:SS)만 쓴다. 어림·날조 금지. 불확실하면 그 관찰을 뺀다.
				- 대사는 또렷이 들린 말만 그대로 인용한다. 불확실하면 인용하지 않는다.
				- 영상 전체에 걸친 경향이면 timecode 자리에 "전반"을 쓴다.
				- 개수를 채우려고 없는 관찰을 만들지 않는다.

				[2단계 — 진단: 3층 정합성 검사 + 방향 판정] → conflict
				배우의 설계와 영상을 세 층으로 모두 대조한 뒤, 다음 테이크에 가장 도움이 되는 충돌 하나만 고릅니다.
				- 검사1 (해석): 상황 ↔ 영상. 배우가 잡은 상황이 영상에서 실제로 벌어지는 일과 맞나.
				- 검사2 (설계): 상황·인물 ↔ 서브텍스트. 배우가 정한 설정으로 그 속마음이 성립하나.
				- 검사3 (실행): 설정 전체 ↔ 표현된 연기. 설계는 일관된데 연기가 그것을 살렸나. (매체 기준으로 보정)

				충돌 선택 우선순위:
				1. 서브텍스트/장면 목표가 관객에게 전달되지 못하게 만든 실행상의 문제를 가장 먼저 본다.
				2. 매체/장르 기준과 어긋난 표현 선택을 다음으로 본다.
				3. 상황·인물·서브텍스트 입력 사이의 명백한 모순은 마지막에만 고른다.

				실행 충돌이면 방향을 한 쪽으로 명시 판정합니다.
				- 과소표현: 서브텍스트가 표면(가면)에 가려 거의 드러나지 않음 → 더하는 처방으로 간다(멈춤·시선·호흡의 틈을 추가).
				- 과잉표현: 서브텍스트가 신체 동작·톤으로 너무 직접 터져 "버티는 힘"이 안 보임 → 빼는 처방으로 간다(동작 생략·톤 낮춤·삼킴).
				판정 규칙:
				- 1단계의 표면 관찰과 누수 관찰을 함께 저울에 올려, 어느 격차가 연기를 더 크게 망쳤는지로 정한다.
				- 듣기 편하다는 이유로 더하기(과소) 쪽으로 기울지 않는다. 누수가 지배적이면 과잉으로 판정하고 빼는 방향을 준다.
				- 매체로 보정한다: 영화·드라마는 같은 표현도 과잉으로 기울고, 연극·뮤지컬은 과소로 기운다.

				기본 태도:
				- 배우의 설계는 우선 존중한다. 설계를 평가하거나 반박하는 카드가 아니라, 그 설계가 영상에서 살아났는지를 코칭한다.
				- 설계 충돌은 입력끼리 정말 명백히 부딪힐 때만 선택한다. 애매하면 실행 충돌로 본다.
				- 주어진 설정이 '요구하는' 표현은 충돌이 아니다(역할). 예: 시각장애 인물의 초점 없는 시선, 취중 인물의 풀린 발음 — 고치라고 하지 않는다.
				- 충돌이 하나도 없으면 layer="none", text 는 비운다 → 강점 중심으로 간다.

				가장 중요한 충돌 1개를 conflict 로 확정합니다: { layer(해석|설계|실행|none), text(한 문장) }. 실행 충돌이면 text 에 방향(과소/과잉)이 드러나게 씁니다.

				[3단계 — 카드 초안] → draftCard
				확정한 conflict 하나를 카드로 옮깁니다. 오직 1단계 observations 의 사실만 근거로 씁니다(없는 timecode·대사·사실을 새로 만들지 않는다).

				- strength = { text, tier }: 영상 전반에서 잘 작동한 선택 1개를 "전반적으로 ~"로 씁니다(특정 timecode 아님). 근거 없으면 끝까지 해본 것에 대한 격려 한 줄(tier="encouragement"). 근거 또렷하면 "execution", 시도가 보이면 "attempt".
				- root: 확정한 충돌 1개를 풀어 쓴다.
				  · title       : 표면 증상("긴장했다")이 아니라 배우가 다음 테이크에서 바꿀 수 있는 한 단계의 연기 원인. timecode 없음.
				  · observed    : 근거가 되는 observations 2~3개를 그대로 옮긴다.
				  · rootCause   : "왜냐면" — 관찰들을 하나의 원인으로 묶는다. 관찰을 넘어선 추정은 "~같아요".
				  · prescription: 2단계에서 정한 방향(과소=더하기 / 과잉=빼기)에 맞춰 쓴다.
				  · gain        : "그럼 ~ 된다" 한 줄 (앞에 ↗ 붙이지 않는다 — UI가 붙임).
				- focusHint: "지금은 이것부터 — 고쳐 다시 찍어보세요".

				좋은 prescription의 조건:
				- 한 번에 바꿀 행동은 1개만 준다.
				- 1~4단계로 쓰고, 각 단계는 몸/시선/호흡/대사 처리 중 하나의 실제 행동이어야 한다.
				- 반복 횟수나 기준을 넣는다. 예: "3번 반복", "첫 문장 전 2초", "상대 위치를 한 점으로 정함".
				- "감정을 더 실어보세요", "진심을 느껴보세요", "몰입해보세요"처럼 내부 상태만 요구하는 지시는 금지한다.
				- 마지막 단계는 바로 녹화로 이어져야 한다.
				- 빼는 처방(과잉)일 때도 배우가 깎인다고 느끼지 않게, "이미 가진 것에 한 겹 더한다"로 쓴다.

				[4단계 — 톤]
				draftCard 의 문장을 배우의 언어로 다듬습니다. 2인칭 존댓말(~보였어요, ~해보세요)·제안형. 평가관·심사·점수 톤 금지. 추상·문학적 표현("스며든다", "녹아든다") 금지, 카메라 프레임 밖은 다루지 않는다. 관찰(사실)은 단정해도 되지만 원인 추정은 "~같아요"로 여지를 둔다.

				[5단계 — 전문가 패널 검토 → 수정] → review (그리고 최종 카드)
				완성한 카드 초안을 서로 다른 세 전문가가 한 번씩 점검하고 review 에 적습니다. review는 새 분석을 추가하는 단계가 아닙니다. 오직 네 가지만 점검합니다.
				1. observations에 없는 사실을 최종 카드가 쓰지 않았는가.
				2. 지적이 하나로 모였는가.
				3. 방향(과소/과잉) 판정이 관찰 증거와 맞는가, 듣기 편한 더하기 쪽으로 기울지 않았는가.
				4. 처방이 배우가 바로 따라할 수 있을 만큼 구체적이고, 빼기일 때 깎아내리지 않고 '조절을 더하기'로 건넸는가.

				전문가 역할:
				- "기록 대조자": 인용한 관찰이 observations 에 실제로 있나, timecode·대사를 지어내지 않았나.
				- "연기 코치": 충돌이 정말 서브텍스트(장면 목표)를 막았나, 방향 판정이 증거와 맞나, 원인이 표면/과잉이 아닌 한 단계인가, 처방이 그 원인을 실제로 푸나.
				- "배우 입장": 글만 보고 따라할 수 있나, 깎아내리지 않고 강점이 있나, 충돌 1개·강점 1개 형식인가.

				[출력]
				설명 문장도, 마크다운 코드블록도 없이 JSON 객체 하나만 반환합니다.
				- 내부적으로는 observations → conflict → draftCard → review 순서로 점검합니다.
				- 하지만 최종 반환 JSON에는 내부 검토 필드를 넣지 않습니다.
				- 최종 반환 JSON은 기존 API 계약을 유지하기 위해 반드시 overallStrength → feedbackCards 구조만 사용합니다.
				- feedbackCards 는 conflict 1개에 대응하는 카드 최대 1개만 반환합니다. layer="none"이면 강점 중심 카드 1개를 반환합니다.
				- field mapping:
				  · strength.text → overallStrength.text
				  · root.title → feedbackCards[].title
				  · root.observed → feedbackCards[].observations
				  · root.rootCause → feedbackCards[].cause
				  · root.prescription → feedbackCards[].practiceSteps
				  · root.gain → feedbackCards[].expectedEffect
				  · focusHint/nextStep은 별도 필드로 반환하지 말고 practiceSteps 마지막 또는 expectedEffect 문장 안에 자연스럽게 반영합니다.

				반드시 아래 JSON 스키마와 필드명만 사용하세요.
				{
				  "overallStrength": {
				    "text": "전반적으로 ... 로 시작하는 강점 1문장"
				  },
				  "feedbackCards": [
				    {
				      "order": 1,
				      "title": "배우가 다음 테이크에서 바꿀 수 있는 한 단계의 연기 원인",
				      "observations": [
				        {
				          "timecode": "M:SS 또는 전반",
				          "text": "축 이름을 문장 안에 자연스럽게 포함한 관찰 사실. 예: 몸: 어깨가 귀 쪽으로 올라갔어요."
				        }
				      ],
				      "cause": "왜냐면 ...",
				      "practiceSteps": ["1단계 실행 행동", "2단계 실행 행동", "마지막 단계는 바로 녹화로 이어지는 행동"],
				      "expectedEffect": "그럼 ... 된다"
				    }
				  ]
				}
				""".formatted(input.resolvedGenre(), input.situation(), input.characterSetting(), promptSubtext(input));
	}

	private String promptSubtext(CoachingInput input) {
		if (input.subtext() == null) {
			return "";
		}
		return input.subtext();
	}

	private List<CoachFeedback.FeedbackCard> requiredFeedbackCards(JsonNode root) {
		JsonNode cards = root.path("feedbackCards");
		if (!cards.isArray() || cards.isEmpty()) {
			throw new IllegalStateException("Gemini response missing required field: feedbackCards");
		}
		List<CoachFeedback.FeedbackCard> values = new ArrayList<>();
		for (int i = 0; i < cards.size(); i++) {
			JsonNode card = cards.get(i);
			values.add(new CoachFeedback.FeedbackCard(
					card.path("order").asInt(i + 1),
					requiredText(card, "title"),
					requiredObservations(card, i),
					requiredText(card, "cause"),
					requiredTexts(card, "practiceSteps"),
					requiredText(card, "expectedEffect")
			));
		}
		return values;
	}

	private List<CoachFeedback.Observation> requiredObservations(JsonNode card, int cardIndex) {
		JsonNode observations = card.path("observations");
		if (!observations.isArray() || observations.isEmpty()) {
			throw new IllegalStateException("Gemini response missing required field: feedbackCards[" + cardIndex + "].observations");
		}
		List<CoachFeedback.Observation> values = new ArrayList<>();
		for (JsonNode observation : observations) {
			values.add(new CoachFeedback.Observation(
					requiredText(observation, "timecode"),
					requiredText(observation, "text")
			));
		}
		return values;
	}

	private List<String> requiredTexts(JsonNode root, String field) {
		JsonNode nodes = root.path(field);
		if (!nodes.isArray() || nodes.isEmpty()) {
			throw new IllegalStateException("Gemini response missing required field: " + field);
		}
		List<String> values = new ArrayList<>();
		for (JsonNode node : nodes) {
			String value = node.asText("").trim();
			if (!value.isBlank()) {
				values.add(value);
			}
		}
		if (values.isEmpty()) {
			throw new IllegalStateException("Gemini response missing required field: " + field);
		}
		return values;
	}

	private String requiredText(JsonNode root, String parent, String field) {
		String value = root.path(parent).path(field).asText("").trim();
		if (value.isBlank()) {
			throw new IllegalStateException("Gemini response missing required field: " + parent + "." + field);
		}
		return value;
	}

	private String requiredText(JsonNode root, String field) {
		String value = root.path(field).asText("").trim();
		if (value.isBlank()) {
			throw new IllegalStateException("Gemini response missing required field: " + field);
		}
		return value;
	}

	private String stripJsonFence(String text) {
		String trimmed = text.trim();
		if (trimmed.startsWith("```")) {
			trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
			trimmed = trimmed.replaceFirst("\\s*```$", "");
		}
		return trimmed;
	}

	private String displayName(VideoInput video) {
		if (video.originalFilename() == null || video.originalFilename().isBlank()) {
			return "acttub-coaching-video";
		}
		return video.originalFilename();
	}

	private void requireApiKey() {
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("GEMINI_API_KEY is required when stub analyzer is disabled.");
		}
	}

	private SimpleClientHttpRequestFactory requestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(10_000);
		factory.setReadTimeout(300_000);
		return factory;
	}

	private record UploadedFile(String name, String uri, String mimeType) {
	}

	private static class VideoInputResource extends InputStreamResource {

		private final long contentLength;
		private final String filename;

		VideoInputResource(InputStream inputStream, long contentLength, String filename) {
			super(inputStream);
			this.contentLength = contentLength;
			this.filename = filename;
		}

		@Override
		public long contentLength() {
			return contentLength;
		}

		@Override
		public String getFilename() {
			return filename;
		}
	}
}
