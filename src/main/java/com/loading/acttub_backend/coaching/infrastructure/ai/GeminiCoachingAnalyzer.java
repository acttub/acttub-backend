package com.loading.acttub_backend.coaching.infrastructure.ai;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loading.acttub_backend.coaching.application.model.VideoInput;
import com.loading.acttub_backend.coaching.application.port.CoachingAnalysisTimeoutException;
import com.loading.acttub_backend.coaching.application.port.CoachingAnalyzer;
import com.loading.acttub_backend.coaching.domain.CoachFeedback;
import com.loading.acttub_backend.coaching.domain.CoachingAnalysisResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
	public CoachingAnalysisResult analyze(VideoInput video, String performanceIntent) {
		requireApiKey();
		try {
			UploadedFile uploadedFile = upload(video);
			waitUntilActive(uploadedFile);
			String response = generate(uploadedFile, performanceIntent);
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

		byte[] videoBytes = video.openStream().readAllBytes();
		String fileResponse = restClient.post()
				.uri(uploadUrl)
				.header(HttpHeaders.CONTENT_LENGTH, String.valueOf(videoBytes.length))
				.header("X-Goog-Upload-Offset", "0")
				.header("X-Goog-Upload-Command", "upload, finalize")
				.body(videoBytes)
				.retrieve()
				.body(String.class);

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

	private String generate(UploadedFile uploadedFile, String performanceIntent) {
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
										Map.of("text", prompt(performanceIntent))
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
				new CoachFeedback.SceneIntent(
						text(root, "sceneIntent", "text"),
						text(root, "sceneIntent", "source")
				),
				new CoachFeedback.Strength(
						text(root, "strength", "timecode"),
						text(root, "strength", "axis"),
						text(root, "strength", "signal"),
						text(root, "strength", "why"),
						text(root, "strength", "tier")
				),
				new CoachFeedback.Focus(
						text(root, "focus", "timecode"),
						axes(root.path("focus").path("axes")),
						text(root, "focus", "observedSignal"),
						text(root, "focus", "rootCause"),
						text(root, "focus", "intentGap"),
						text(root, "focus", "prescription")
				),
				new CoachFeedback.NextStep(
						text(root, "nextStep", "text"),
						text(root, "nextStep", "action")
				)
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

	private String prompt(String performanceIntent) {
		return """
				당신은 배우의 연기 영상을 분석하는 코칭 전문가입니다.
				업로드된 영상을 보고 사용자의 연기 의도와 실제 수행 사이의 차이를 분석하세요.

				사용자의 연기 의도:
				%s

				반드시 아래 JSON 형식만 반환하세요. 설명 문장, markdown, code fence는 반환하지 마세요.
				{
				  "sceneIntent": {"text": "string", "source": "actor_input"},
				  "strength": {"timecode": "MM:SS 또는 범위", "axis": "string", "signal": "string", "why": "string", "tier": "string"},
				  "focus": {"timecode": "MM:SS 또는 범위", "axes": ["emotion"], "observedSignal": "string", "rootCause": "string", "intentGap": "string", "prescription": "string"},
				  "nextStep": {"text": "string", "action": "string"}
				}
				""".formatted(performanceIntent);
	}

	private List<String> axes(JsonNode axes) {
		if (!axes.isArray()) {
			return List.of();
		}
		List<String> values = new ArrayList<>();
		for (JsonNode axis : axes) {
			values.add(axis.asText(""));
		}
		return values;
	}

	private String text(JsonNode root, String parent, String field) {
		return root.path(parent).path(field).asText("");
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
}
