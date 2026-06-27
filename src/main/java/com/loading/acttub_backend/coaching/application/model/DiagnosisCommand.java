package com.loading.acttub_backend.coaching.application.model;

public record DiagnosisCommand(String answer) {
	public String normalizedAnswer() {
		if (answer == null || answer.isBlank()) {
			return null;
		}
		return answer.trim();
	}
}
