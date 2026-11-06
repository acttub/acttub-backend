package com.loading.acttub_backend.coaching.presentation.dto;

import java.time.OffsetDateTime;

public record EvaluationResponse(
		String evaluationId,
		String coachingId,
		int rating,
		String comment,
		OffsetDateTime createdAt
) {
}
