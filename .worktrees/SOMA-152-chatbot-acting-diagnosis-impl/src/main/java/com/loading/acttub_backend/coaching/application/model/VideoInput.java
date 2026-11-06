package com.loading.acttub_backend.coaching.application.model;

import java.io.IOException;
import java.io.InputStream;

public record VideoInput(
		String originalFilename,
		String contentType,
		long sizeBytes,
		Content content
) {

	public InputStream openStream() throws IOException {
		return content.openStream();
	}

	@FunctionalInterface
	public interface Content {

		InputStream openStream() throws IOException;
	}
}
