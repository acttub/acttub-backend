package com.loading.acttub_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import com.loading.acttub_backend.coaching.application.port.CoachingAnalyzer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Tag("prod-smoke")
@SpringBootTest
@ActiveProfiles("prod")
@EnabledIfEnvironmentVariable(named = "RUN_PROD_SMOKE_TESTS", matches = "true")
class ProdProfileSmokeTests {

	@MockitoBean
	private CoachingAnalyzer coachingAnalyzer;

	@Value("${app.storage.video.root}")
	private String videoRoot;

	@Test
	void contextLoadsWithProdProfile() {
		assertThat(coachingAnalyzer).isNotNull();
	}

	@Test
	void configuredVideoStorageRootIsWritable() throws Exception {
		Path root = Path.of(videoRoot);
		Files.createDirectories(root);

		Path probe = Files.createTempFile(root, "acttub-prod-smoke-", ".tmp");
		Files.deleteIfExists(probe);

		assertThat(root).isDirectory();
	}
}
