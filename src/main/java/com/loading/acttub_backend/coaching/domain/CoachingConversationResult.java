package com.loading.acttub_backend.coaching.domain;

public record CoachingConversationResult(
		boolean diagnosisReady,
		String nextQuestion,
		String reason,
		String summary,
		String refinedSituation,
		String refinedIntent
) {
}
