package com.loading.acttub_backend.coaching.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import com.loading.acttub_backend.coaching.application.model.StoredVideo;
import com.loading.acttub_backend.coaching.application.model.VideoInput;
import com.loading.acttub_backend.coaching.application.port.VideoStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalVideoStorage implements VideoStorage {

	private final Path root;

	public LocalVideoStorage(@Value("${app.storage.video.root:storage/videos}") String root) {
		this.root = Paths.get(root).toAbsolutePath().normalize();
	}

	@Override
	public StoredVideo store(Long coachingId, VideoInput video) {
		String storageKey = "coachings/" + coachingId + "/" + storedFilename(video);
		Path destination = root.resolve(storageKey).normalize();
		if (!destination.startsWith(root)) {
			throw new IllegalStateException("Invalid video storage path.");
		}

		try {
			Files.createDirectories(destination.getParent());
			try (InputStream inputStream = video.openStream()) {
				Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
			}
			return new StoredVideo(storageKey, destination.toUri().toString());
		} catch (IOException e) {
			throw new IllegalStateException("Failed to store uploaded video.", e);
		}
	}

	private String storedFilename(VideoInput video) {
		String originalFilename = video.originalFilename();
		if (originalFilename == null || originalFilename.isBlank()) {
			return "video" + extension(video.contentType());
		}
		return Paths.get(originalFilename).getFileName().toString();
	}

	private String extension(String contentType) {
		return switch (contentType) {
			case "video/mp4" -> ".mp4";
			case "video/quicktime" -> ".mov";
			case "video/webm" -> ".webm";
			default -> "";
		};
	}
}
