package com.loading.acttub_backend.coaching.domain;

import java.math.BigDecimal;

public record CoachingAnalysisResult(
		String provider,
		String model,
		BigDecimal temperature,
		String promptVersion,
		CoachFeedback feedback
) {
}
