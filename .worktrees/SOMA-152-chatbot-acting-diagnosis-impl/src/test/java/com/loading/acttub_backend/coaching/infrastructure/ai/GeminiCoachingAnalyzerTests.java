package com.loading.acttub_backend.coaching.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loading.acttub_backend.coaching.application.model.CoachingInput;
import com.loading.acttub_backend.coaching.application.model.VideoInput;
import com.loading.acttub_backend.coaching.domain.CoachingAnalysisResult;
import com.loading.acttub_backend.coaching.domain.CoachingDiagnosisResult;
import com.loading.acttub_backend.coaching.domain.CoachingInitialContext;
import com.loading.acttub_backend.coaching.domain.MeasurableSignal;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GeminiCoachingAnalyzerTests {

	private HttpServer server;
	private ExecutorService serverExecutor;

	@AfterEach
	void tearDown() {
		if (server != null) {
			server.stop(0);
		}
		if (serverExecutor != null) {
			serverExecutor.shutdownNow();
		}
	}

	@Test
	void streamsVideoWhenUploadingToGemini() throws Exception {
		byte[] videoBytes = "fake-video".getBytes(StandardCharsets.UTF_8);
		AtomicReference<String> uploadedBody = new AtomicReference<>();
		startGeminiServer(uploadedBody);

		GeminiCoachingAnalyzer analyzer = new GeminiCoachingAnalyzer(
				new ObjectMapper(),
				"test-api-key",
				"http://localhost:" + server.getAddress().getPort(),
				"gemini-test",
				BigDecimal.ZERO,
				"test-prompt",
				1_000L
		);

		CoachingAnalysisResult result = analyzer.analyze(new VideoInput(
				"scene.mp4",
				"video/mp4",
				videoBytes.length,
				() -> new NoBulkReadInputStream(videoBytes)
		), coachingInput());

		assertThat(uploadedBody.get()).isEqualTo("fake-video");
		assertThat(result.initialContext().question()).isEqualTo("이 장면에서 숨기고 싶은 감정은 무엇이었나요?");
		assertThat(result.initialContext().measurableSignals().getFirst().name()).isEqualTo("pauseCount");
	}

	@Test
	void failsWhenGeminiResponseOmitsRequiredInitialField() throws Exception {
		byte[] videoBytes = "fake-video".getBytes(StandardCharsets.UTF_8);
		startGeminiServer(new AtomicReference<>(), """
				{"question":"질문","analysis":"분석","measurableSignals":[{"name":"pauseCount","value":2,"unit":"count","evidence":"두 번 멈춤"}]}
				""");

		GeminiCoachingAnalyzer analyzer = new GeminiCoachingAnalyzer(
				new ObjectMapper(),
				"test-api-key",
				"http://localhost:" + server.getAddress().getPort(),
				"gemini-test",
				BigDecimal.ZERO,
				"test-prompt",
				1_000L
		);

		assertThatThrownBy(() -> analyzer.analyze(new VideoInput(
				"scene.mp4",
				"video/mp4",
				videoBytes.length,
				() -> new NoBulkReadInputStream(videoBytes)
		), coachingInput()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("diagnosticHypothesis");
	}

	@Test
	void diagnosesFromInitialContextAndAnswer() throws Exception {
		AtomicReference<String> uploadedBody = new AtomicReference<>();
		AtomicReference<String> diagnosisRequestBody = new AtomicReference<>();
		startGeminiServer(uploadedBody, diagnosisRequestBody);

		GeminiCoachingAnalyzer analyzer = new GeminiCoachingAnalyzer(
				new ObjectMapper(),
				"test-api-key",
				"http://localhost:" + server.getAddress().getPort(),
				"gemini-test",
				BigDecimal.ZERO,
				"test-prompt",
				1_000L
		);

		CoachingDiagnosisResult result = analyzer.diagnose(
				coachingInput(),
				new CoachingInitialContext(
						"이 장면에서 숨기고 싶은 감정은 무엇이었나요?",
						"안녕. 오랜만이네.",
						"감정을 참는 선택이 보입니다.",
						List.of(new MeasurableSignal("pauseCount", BigDecimal.valueOf(2), "count", "두 번 멈춤")),
						"숨기는 이유가 아직 덜 선명한 것 같아요."
				),
				"약하게 보이고 싶지 않았어요."
		);

		assertThat(uploadedBody.get()).isNull();
		assertThat(diagnosisRequestBody.get()).doesNotContain("file_data");
		assertThat(diagnosisRequestBody.get()).contains("약하게 보이고 싶지 않았어요.");
		assertThat(diagnosisRequestBody.get()).contains("감정을 참는 선택이 보입니다.");
		assertThat(diagnosisRequestBody.get()).contains("두 번 멈춤");
		assertThat(result.refinedSituation()).isEqualTo("약하게 보이고 싶지 않아 미련을 숨기는 상황");
		assertThat(result.refinedIntent()).isEqualTo("미련을 숨기며 상대의 반응을 확인한다");
		assertThat(result.feedback().feedbackCards().getFirst().title()).isEqualTo("첫 멈춤 전에 숨기는 이유를 분명히 잡아보세요");
	}

	@Test
	void failsWhenDiagnosisResponseOmitsFeedback() throws Exception {
		startGeminiServer(new AtomicReference<>(), new AtomicReference<>(), """
				{"refinedSituation":"상황","refinedIntent":"의도"}
				""");

		GeminiCoachingAnalyzer analyzer = new GeminiCoachingAnalyzer(
				new ObjectMapper(),
				"test-api-key",
				"http://localhost:" + server.getAddress().getPort(),
				"gemini-test",
				BigDecimal.ZERO,
				"test-prompt",
				1_000L
		);

		assertThatThrownBy(() -> analyzer.diagnose(
				coachingInput(),
				new CoachingInitialContext(
						"질문",
						"",
						"감정을 참는 선택이 보입니다.",
						List.of(new MeasurableSignal("pauseCount", BigDecimal.valueOf(2), "count", "두 번 멈춤")),
						"숨기는 이유가 아직 덜 선명한 것 같아요."
				),
				"약하게 보이고 싶지 않았어요."
		))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("feedback");
	}

	private CoachingInput coachingInput() {
		return new CoachingInput(
				"영화",
				null,
				"헤어진 연인을 우연히 다시 만난 상황",
				"감정을 쉽게 드러내지 않는 배우 지망생",
				"아직 미련이 있지만 괜찮은 척한다"
		);
	}

	private void startGeminiServer(AtomicReference<String> uploadedBody) throws IOException {
		startGeminiServer(uploadedBody, """
				{"question":"이 장면에서 숨기고 싶은 감정은 무엇이었나요?","transcript":"안녕. 오랜만이네.","analysis":"감정을 참는 선택이 보입니다.","measurableSignals":[{"name":"pauseCount","value":2,"unit":"count","evidence":"두 번 멈춤"}],"diagnosticHypothesis":"숨기는 이유가 아직 덜 선명한 것 같아요."}
				""");
	}

	private void startGeminiServer(AtomicReference<String> uploadedBody, String initialContextJson) throws IOException {
		startGeminiServer(uploadedBody, new AtomicReference<>(), initialContextJson, diagnosisJson());
	}

	private void startGeminiServer(AtomicReference<String> uploadedBody, AtomicReference<String> diagnosisRequestBody) throws IOException {
		startGeminiServer(uploadedBody, diagnosisRequestBody, """
				{"question":"이 장면에서 숨기고 싶은 감정은 무엇이었나요?","transcript":"안녕. 오랜만이네.","analysis":"감정을 참는 선택이 보입니다.","measurableSignals":[{"name":"pauseCount","value":2,"unit":"count","evidence":"두 번 멈춤"}],"diagnosticHypothesis":"숨기는 이유가 아직 덜 선명한 것 같아요."}
				""", diagnosisJson());
	}

	private void startGeminiServer(
			AtomicReference<String> uploadedBody,
			AtomicReference<String> diagnosisRequestBody,
			String diagnosisJson
	) throws IOException {
		startGeminiServer(uploadedBody, diagnosisRequestBody, """
				{"question":"이 장면에서 숨기고 싶은 감정은 무엇이었나요?","transcript":"안녕. 오랜만이네.","analysis":"감정을 참는 선택이 보입니다.","measurableSignals":[{"name":"pauseCount","value":2,"unit":"count","evidence":"두 번 멈춤"}],"diagnosticHypothesis":"숨기는 이유가 아직 덜 선명한 것 같아요."}
				""", diagnosisJson);
	}

	private void startGeminiServer(
			AtomicReference<String> uploadedBody,
			AtomicReference<String> diagnosisRequestBody,
			String initialContextJson,
			String diagnosisJson
	) throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		serverExecutor = Executors.newSingleThreadExecutor();
		server.setExecutor(serverExecutor);
		server.createContext("/upload/v1beta/files", exchange -> {
			assertThat(exchange.getRequestMethod()).isEqualTo("POST");
			sendJson(exchange, 200, "{\"file\":{}}", "x-goog-upload-url", "http://localhost:" + server.getAddress().getPort() + "/upload-session");
		});
		server.createContext("/upload-session", exchange -> {
			assertThat(exchange.getRequestMethod()).isEqualTo("POST");
			uploadedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			sendJson(exchange, 200, "{\"file\":{\"name\":\"files/test-file\",\"uri\":\"gemini://test-file\",\"mimeType\":\"video/mp4\"}}");
		});
		server.createContext("/v1beta/files/test-file", exchange -> {
			if ("GET".equals(exchange.getRequestMethod())) {
				sendJson(exchange, 200, "{\"file\":{\"state\":\"ACTIVE\"}}");
				return;
			}
			if ("DELETE".equals(exchange.getRequestMethod())) {
				sendJson(exchange, 200, "{}");
				return;
			}
			sendJson(exchange, 405, "{}");
		});
		server.createContext("/v1beta/models/gemini-test:generateContent", exchange -> {
			assertThat(exchange.getRequestMethod()).isEqualTo("POST");
			String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			if (requestBody.contains("file_data")) {
				sendJson(exchange, 200, geminiResponse(initialContextJson));
				return;
			}
			diagnosisRequestBody.set(requestBody);
			sendJson(exchange, 200, geminiResponse(diagnosisJson));
		});
		server.start();
	}

	private String diagnosisJson() {
		String diagnosisJson = """
				{"refinedSituation":"약하게 보이고 싶지 않아 미련을 숨기는 상황","refinedIntent":"미련을 숨기며 상대의 반응을 확인한다","feedback":{"overallStrength":{"text":"전반적으로 버티는 선택이 좋았어요."},"feedbackCards":[{"order":1,"title":"첫 멈춤 전에 숨기는 이유를 분명히 잡아보세요","observations":[{"timecode":"0:28","text":"대사 사이 멈춤이 보였어요."}],"cause":"왜냐면 방어의 이유가 아직 충분히 걸리지 않은 것 같아요.","practiceSteps":["첫 대사 전 시선을 2초 고정하세요."],"expectedEffect":"그럼 숨기는 이유가 선명해져요."}]}}
				""";
		return diagnosisJson;
	}

	private String geminiResponse(String feedbackJson) {
		return """
				{"candidates":[{"content":{"parts":[{"text":%s}]}}]}
				""".formatted(writeJsonString(feedbackJson));
	}

	private String writeJsonString(String value) {
		try {
			return new ObjectMapper().writeValueAsString(value);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to write Gemini test response.", e);
		}
	}

	private void sendJson(HttpExchange exchange, int status, String body, String headerName, String headerValue) throws IOException {
		exchange.getResponseHeaders().add(headerName, headerValue);
		sendJson(exchange, status, body);
	}

	private void sendJson(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream responseBody = exchange.getResponseBody()) {
			responseBody.write(bytes);
		}
	}

	private static class NoBulkReadInputStream extends ByteArrayInputStream {

		NoBulkReadInputStream(byte[] buf) {
			super(buf);
		}

		@Override
		public byte[] readAllBytes() {
			throw new AssertionError("Video upload must not load the whole stream into memory.");
		}
	}
}
