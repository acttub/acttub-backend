package com.loading.acttub_backend.coaching.application.model;

import java.time.OffsetDateTime;

import com.loading.acttub_backend.coaching.domain.CoachFeedback;

public record CoachingResult(
		String coachingId,
		String status,
		OffsetDateTime createdAt,
		OffsetDateTime completedAt,
		CoachingInput input,
		CoachFeedback feedback
) {
}
