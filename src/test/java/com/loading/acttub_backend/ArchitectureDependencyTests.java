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
		Path applicationPackage = Path.of("src/main/java/com/loading/acttub_backend/coaching/application");

		List<String> violations;
		try (Stream<Path> paths = Files.walk(applicationPackage)) {
			violations = paths
					.filter(path -> path.toString().endsWith(".java"))
					.flatMap(this::importLines)
					.filter(line -> line.contains("com.loading.acttub_backend.coaching.presentation")
							|| line.contains("com.loading.acttub_backend.coaching.infrastructure"))
					.toList();
		}

		assertThat(violations).isEmpty();
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
