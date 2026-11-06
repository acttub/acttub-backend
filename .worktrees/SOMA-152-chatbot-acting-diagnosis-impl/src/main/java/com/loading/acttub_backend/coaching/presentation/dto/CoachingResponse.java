package com.loading.acttub_backend.coaching.presentation.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.loading.acttub_backend.coaching.domain.CoachFeedback;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CoachingResponse(
		String coachingId,
		String status,
		OffsetDateTime createdAt,
		OffsetDateTime completedAt,
		CoachingInput input,
		Dialogue dialogue,
		InitialContext context,
		RefinedContext refinedContext,
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

	public record Dialogue(String question) {
	}

	public record InitialContext(
			String transcript,
			String analysis,
			List<MeasurableSignalResponse> measurableSignals,
			String diagnosticHypothesis
	) {
	}

	public record MeasurableSignalResponse(String name, BigDecimal value, String unit, String evidence) {
	}

	public record RefinedContext(String answer, String refinedSituation, String refinedIntent) {
	}
}
