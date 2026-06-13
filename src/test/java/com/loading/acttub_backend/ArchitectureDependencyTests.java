package com.loading.acttub_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class ArchitectureDependencyTests {

	@Test
	void applicationLayerDoesNotDependOnPresentationOrInfrastructurePackages() throws IOException {
		List<String> violations = applicationImports()
				.filter(line -> line.contains("com.loading.acttub_backend.coaching.presentation")
						|| line.contains("com.loading.acttub_backend.coaching.infrastructure"))
				.toList();

		assertThat(violations).isEmpty();
	}

	@Test
	void applicationLayerDoesNotDependOnSpringWeb() throws IOException {
		List<String> violations = applicationImports()
				.filter(line -> line.contains("org.springframework.web"))
				.toList();

		assertThat(violations).isEmpty();
	}

	@Test
	void applicationLayerDoesNotDependOnApiResponseDetails() throws IOException {
		List<String> violations = applicationImports()
				.filter(line -> line.contains("com.loading.acttub_backend.global.api")
						|| line.contains("org.springframework.http"))
				.toList();

		assertThat(violations).isEmpty();
	}

	private Stream<String> applicationImports() throws IOException {
		Path applicationPackage = Path.of("src/main/java/com/loading/acttub_backend/coaching/application");
		return Files.walk(applicationPackage)
				.filter(path -> path.toString().endsWith(".java"))
				.flatMap(this::importLines);
	}

	private Stream<String> importLines(Path path) {
		try {
			return Files.readAllLines(path).stream()
					.filter(line -> line.startsWith("import "))
					.map(line -> path.getFileName() + ": " + line);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read " + path, e);
		}
	}
}
