package com.loading.acttub_backend.coaching.domain;

import java.util.List;

public record CoachFeedback(
		OverallStrength overallStrength,
		List<FeedbackCard> feedbackCards
) {

	public record OverallStrength(String text) {
	}

	public record FeedbackCard(
			int order,
			String title,
			List<Observation> observations,
			String cause,
			List<String> practiceSteps,
			String expectedEffect
	) {
	}

	public record Observation(String timecode, String text) {
	}
}
