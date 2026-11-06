package com.loading.acttub_backend.coaching.application.model;

public record CoachingInput(
		CoachingGenre genre,
		String customGenre,
		String situation,
		String characterSetting,
		String subtext
) {

	public CoachingInput(String genre, String customGenre, String situation, String characterSetting, String subtext) {
		this(CoachingGenre.fromLabel(genre), customGenre, situation, characterSetting, subtext);
	}

	public CoachingInput {
		customGenre = normalize(customGenre);
		situation = normalize(situation);
		characterSetting = normalize(characterSetting);
		subtext = normalize(subtext);
		if (genre == null || !genre.requiresCustomGenre()) {
			customGenre = null;
		}
	}

	public boolean hasAllowedGenre() {
		return genre != null;
	}

	public boolean requiresCustomGenre() {
		return genre != null && genre.requiresCustomGenre();
	}

	public String genreLabel() {
		if (genre == null) {
			return null;
		}
		return genre.label();
	}

	public String resolvedGenre() {
		if (requiresCustomGenre()) {
			return customGenre;
		}
		return genreLabel();
	}

	public String summary() {
		return """
				장르: %s
				상황: %s
				인물 설정: %s
				서브텍스트: %s
				""".formatted(resolvedGenre(), situation, characterSetting, displaySubtext());
	}

	public String displaySubtext() {
		if (subtext == null) {
			return "입력 없음";
		}
		return subtext;
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
