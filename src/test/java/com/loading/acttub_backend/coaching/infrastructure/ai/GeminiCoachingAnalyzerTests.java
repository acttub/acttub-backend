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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loading.acttub_backend.coaching.application.model.VideoInput;
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

		analyzer.analyze(new VideoInput(
				"scene.mp4",
				"video/mp4",
				videoBytes.length,
				() -> new NoBulkReadInputStream(videoBytes)
		), "차분하지만 단호한 감정");

		assertThat(uploadedBody.get()).isEqualTo("fake-video");
	}

	@Test
	void failsWhenGeminiResponseOmitsRequiredFeedbackField() throws Exception {
		byte[] videoBytes = "fake-video".getBytes(StandardCharsets.UTF_8);
		startGeminiServer(new AtomicReference<>(), """
				{"sceneIntent":{"text":"의도","source":"actor_input"},"strength":{"timecode":"00:01","axis":"voice","signal":"명확함","why":"전달됨","tier":"good"},"focus":{"timecode":"00:02","axes":["emotion"],"observedSignal":"작음","rootCause":"긴장","intentGap":"간극"},"nextStep":{"text":"다시 시도","action":"retry"}}
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
		), "차분하지만 단호한 감정"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Gemini response missing required field: focus.prescription");
	}

	private void startGeminiServer(AtomicReference<String> uploadedBody) throws IOException {
		startGeminiServer(uploadedBody, """
				{"sceneIntent":{"text":"의도","source":"actor_input"},"strength":{"timecode":"00:01","axis":"voice","signal":"명확함","why":"전달됨","tier":"good"},"focus":{"timecode":"00:02","axes":["emotion"],"observedSignal":"작음","rootCause":"긴장","intentGap":"간극","prescription":"호흡"},"nextStep":{"text":"다시 시도","action":"retry"}}
				""");
	}

	private void startGeminiServer(AtomicReference<String> uploadedBody, String feedbackJson) throws IOException {
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
			sendJson(exchange, 200, geminiResponse(feedbackJson));
		});
		server.start();
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
