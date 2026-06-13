package com.loading.acttub_backend.coaching.domain;

import java.util.List;

public record CoachFeedback(
		SceneIntent sceneIntent,
		Strength strength,
		Focus focus,
		NextStep nextStep
) {

	public record SceneIntent(String text, String source) {
	}

	public record Strength(String timecode, String axis, String signal, String why, String tier) {
	}

	public record Focus(
			String timecode,
			List<String> axes,
			String observedSignal,
			String rootCause,
			String intentGap,
			String prescription
	) {
	}

	public record NextStep(String text, String action) {
	}
}
