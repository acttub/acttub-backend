package com.loading.acttub_backend.coaching.application.model;

import java.time.OffsetDateTime;

import com.loading.acttub_backend.coaching.domain.CoachFeedback;
import com.loading.acttub_backend.coaching.domain.CoachingInitialContext;

public record CoachingResult(
		String coachingId,
		String status,
		OffsetDateTime createdAt,
		OffsetDateTime completedAt,
		CoachingInput input,
		CoachingInitialContext initialContext,
		ConversationState conversation,
		String nextQuestion,
		String refinedSituation,
		String refinedIntent,
		CoachFeedback feedback
) {

	public CoachingResult(String coachingId, String status, OffsetDateTime createdAt, OffsetDateTime completedAt,
			CoachingInput input, CoachFeedback feedback) {
		this(coachingId, status, createdAt, completedAt, input, null, null, null, null, null, feedback);
	}
}
