package com.loading.acttub_backend.coaching.application.model;

import java.util.Arrays;

public enum CoachingGenre {

	PLAY("연극"),
	MOVIE("영화"),
	MUSICAL("뮤지컬"),
	DRAMA("드라마"),
	ETC("기타");

	private final String label;

	CoachingGenre(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public boolean requiresCustomGenre() {
		return this == ETC;
	}

	public static CoachingGenre fromLabel(String label) {
		String normalized = normalize(label);
		if (normalized == null) {
			return null;
		}
		return Arrays.stream(values())
				.filter(genre -> genre.label.equals(normalized))
				.findFirst()
				.orElse(null);
	}

	private static String normalize(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		if (normalized.isBlank()) {
			return null;
		}
		return normalized;
	}
}
