package com.loading.acttub_backend.coaching.presentation.dto;

import java.time.OffsetDateTime;

import com.loading.acttub_backend.coaching.domain.CoachFeedback;

public record CoachingResponse(
		String coachingId,
		String status,
		OffsetDateTime createdAt,
		OffsetDateTime completedAt,
		CoachingInput input,
		CoachFeedback result
) {

	public record CoachingInput(
			String genre,
			String customGenre,
			String situation,
			String characterSetting,
			String subtext
	) {
	}
}
