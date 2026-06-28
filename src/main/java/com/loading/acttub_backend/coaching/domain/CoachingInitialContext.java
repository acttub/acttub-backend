package com.loading.acttub_backend.coaching.domain;

import java.util.List;

public record CoachingInitialContext(
		String question,
		String transcript,
		String analysis,
		List<MeasurableSignal> measurableSignals,
		String diagnosticHypothesis
) {
}
