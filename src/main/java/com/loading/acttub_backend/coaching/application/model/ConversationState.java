package com.loading.acttub_backend.coaching.application.model;

public record ConversationState(
		int turnIndex,
		int minTurns,
		int maxTurns,
		boolean diagnosisReady,
		String reason,
		String summary
) {
}
