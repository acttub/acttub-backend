package com.loading.acttub_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Tag("prod-smoke")
@EnabledIfEnvironmentVariable(named = "RUN_PROD_SMOKE_TESTS", matches = "true")
class GeminiApiPingTests {

	@Test
	void geminiModelsApiResponds() throws Exception {
		String apiKey = System.getenv("GEMINI_API_KEY");

		assertThat(apiKey)
				.as("GEMINI_API_KEY must be set when RUN_PROD_SMOKE_TESTS=true")
				.isNotBlank();

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models?key=" + encode(apiKey)))
				.timeout(Duration.ofSeconds(10))
				.GET()
				.build();

		HttpResponse<String> response = HttpClient.newHttpClient()
				.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode())
				.as(response.body())
				.isBetween(200, 299);
		assertThat(response.body()).contains("\"models\"");
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
