package com.loading.acttub_backend.coaching.application.model;

import java.time.OffsetDateTime;

public record EvaluationResult(
		String evaluationId,
		String coachingId,
		int rating,
		String comment,
		OffsetDateTime createdAt
) {
}
