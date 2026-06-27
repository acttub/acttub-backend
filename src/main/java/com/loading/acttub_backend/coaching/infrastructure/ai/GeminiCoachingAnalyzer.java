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
import com.loading.acttub_backend.coaching.domain.CoachingDiagnosisResult;
import com.loading.acttub_backend.coaching.domain.CoachingInitialContext;
import com.loading.acttub_backend.coaching.domain.MeasurableSignal;
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
			return new CoachingAnalysisResult("gemini", model, temperature, promptVersion, parseInitialContext(response));
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

	@Override
	public CoachingDiagnosisResult diagnose(CoachingInput input, CoachingInitialContext initialContext, String answer) {
		requireApiKey();
		try {
			String response = generateDiagnosis(input, initialContext, answer);
			return parseDiagnosisResult(response);
		} catch (ResourceAccessException e) {
			throw new CoachingAnalysisTimeoutException("Gemini diagnosis timed out.", e);
		} catch (RestClientResponseException e) {
			throw new IllegalStateException("Gemini diagnosis request failed: " + e.getResponseBodyAsString(), e);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read Gemini diagnosis response.", e);
		} catch (RestClientException e) {
			throw new IllegalStateException("Gemini diagnosis request failed.", e);
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
										Map.of("text", initialContextPrompt(input))
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

	private String generateDiagnosis(CoachingInput input, CoachingInitialContext initialContext, String answer) throws IOException {
		return restClient.post()
				.uri("/v1beta/models/{model}:generateContent", model)
				.header("x-goog-api-key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of(
						"contents", List.of(Map.of(
								"role", "user",
								"parts", List.of(Map.of("text", diagnosisPrompt(input, initialContext, answer)))
						)),
						"generationConfig", Map.of(
								"temperature", temperature,
								"responseMimeType", "application/json"
						)
				))
				.retrieve()
				.body(String.class);
	}

	private CoachingInitialContext parseInitialContext(String response) throws IOException {
		String text = responseText(response);
		JsonNode root = objectMapper.readTree(stripJsonFence(text));
		return new CoachingInitialContext(
				requiredText(root, "question"),
				root.path("transcript").asText("").trim(),
				requiredText(root, "analysis"),
				requiredMeasurableSignals(root),
				requiredText(root, "diagnosticHypothesis")
		);
	}

	private CoachingDiagnosisResult parseDiagnosisResult(String response) throws IOException {
		String text = responseText(response);
		JsonNode root = objectMapper.readTree(stripJsonFence(text));
		return new CoachingDiagnosisResult(
				requiredText(root, "refinedSituation"),
				requiredText(root, "refinedIntent"),
				parseFeedback(requiredObject(root, "feedback"))
		);
	}

	private CoachFeedback parseFeedback(JsonNode root) {
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

	private String initialContextPrompt(CoachingInput input) {
		return """
				당신은 배우를 돕는 연기 코치입니다. 첨부된 연기 영상을 보고 배우에게 이어서 물어볼 진단 질문과 초기 관찰 맥락을 만듭니다.

				출력은 설명 문장, 마크다운 코드블록 없이 JSON 객체 하나만 반환하세요. 영상에서 실제로 보이거나 들린 근거만 사용하고, 불확실한 대사는 transcript에서 비워 두세요.

				[배우 입력]
				- 매체/장르: %s
				- 상황: %s
				- 인물 설정: %s
				- 서브텍스트: %s

				반드시 아래 필드명만 사용하세요.
				{
				  "question": "이 장면에서 숨기고 싶은 감정은 무엇이었나요?",
				  "transcript": "안녕. 오랜만이네.",
				  "analysis": "감정을 참는 선택이 보입니다.",
				  "measurableSignals": [
				    {"name":"pauseCount","value":2,"unit":"count","evidence":"두 번 멈춤"}
				  ],
				  "diagnosticHypothesis": "숨기는 이유가 아직 덜 선명한 것 같아요."
				}
				""".formatted(input.resolvedGenre(), input.situation(), input.characterSetting(), promptSubtext(input));
	}

	private String diagnosisPrompt(CoachingInput input, CoachingInitialContext initialContext, String answer) throws IOException {
		String initialContextJson = objectMapper.writeValueAsString(initialContext);
		return """
				당신은 배우를 돕는 연기 코치입니다. 초기 영상 관찰 맥락과 배우의 답변을 바탕으로 최종 진단 피드백을 만듭니다.

				출력은 설명 문장, 마크다운 코드블록 없이 JSON 객체 하나만 반환하세요.
				진단 단계에는 영상 파일이 없으므로 새 영상 근거, timecode, 대사를 만들지 마세요.
				최종 피드백 observations는 initialContext.analysis, initialContext.measurableSignals[].evidence, initialContext.transcript에서만 가져오세요.
				초기 맥락에 신뢰할 수 있는 timecode가 없으면 timecode는 "전반"으로 쓰세요.
				아래 예시의 timecode나 문장을 실제 사실로 사용하지 마세요.

				[배우 입력]
				- 매체/장르: %s
				- 상황: %s
				- 인물 설정: %s
				- 서브텍스트: %s

				[초기 맥락 JSON]
				%s

				[배우 답변]
				%s

				반드시 아래 필드명만 사용하세요.
				{
				  "refinedSituation": "약하게 보이고 싶지 않아 미련을 숨기는 상황",
				  "refinedIntent": "미련을 숨기며 상대의 반응을 확인한다",
				  "feedback": {
				    "overallStrength": {"text":"전반적으로 버티는 선택이 좋았어요."},
				    "feedbackCards": [
				      {
				        "order":1,
				        "title":"첫 멈춤 전에 숨기는 이유를 분명히 잡아보세요",
				        "observations":[{"timecode":"전반","text":"measurableSignals의 evidence와 analysis에 근거한 관찰을 쓰세요."}],
				        "cause":"왜냐면 방어의 이유가 아직 충분히 걸리지 않은 것 같아요.",
				        "practiceSteps":["첫 대사 전 시선을 2초 고정하세요."],
				        "expectedEffect":"그럼 숨기는 이유가 선명해져요."
				      }
				    ]
				  }
				}
				""".formatted(
				input.resolvedGenre(),
				input.situation(),
				input.characterSetting(),
				promptSubtext(input),
				initialContextJson,
				answer
		);
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

	private List<MeasurableSignal> requiredMeasurableSignals(JsonNode root) {
		JsonNode signals = root.path("measurableSignals");
		if (!signals.isArray() || signals.isEmpty()) {
			throw new IllegalStateException("Gemini response missing required field: measurableSignals");
		}
		List<MeasurableSignal> values = new ArrayList<>();
		for (JsonNode signal : signals) {
			JsonNode value = signal.path("value");
			if (!value.isNumber()) {
				throw new IllegalStateException("Gemini response missing required field: measurableSignals");
			}
			values.add(new MeasurableSignal(
					requiredText(signal, "name"),
					value.decimalValue(),
					requiredText(signal, "unit"),
					requiredText(signal, "evidence")
			));
		}
		return values;
	}

	private JsonNode requiredObject(JsonNode root, String field) {
		JsonNode value = root.path(field);
		if (!value.isObject()) {
			throw new IllegalStateException("Gemini response missing required field: " + field);
		}
		return value;
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
