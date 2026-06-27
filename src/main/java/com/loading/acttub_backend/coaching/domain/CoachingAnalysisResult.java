package com.loading.acttub_backend.coaching.domain;

import java.math.BigDecimal;

public record CoachingAnalysisResult(
		String provider,
		String model,
		BigDecimal temperature,
		String promptVersion,
		CoachingInitialContext initialContext,
		CoachFeedback feedback
) {

	public CoachingAnalysisResult(String provider, String model, BigDecimal temperature, String promptVersion,
			CoachingInitialContext initialContext) {
		this(provider, model, temperature, promptVersion, initialContext, null);
	}

	public CoachingAnalysisResult(String provider, String model, BigDecimal temperature, String promptVersion,
			CoachFeedback feedback) {
		this(provider, model, temperature, promptVersion, null, feedback);
	}
}
