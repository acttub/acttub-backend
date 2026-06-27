package com.loading.acttub_backend.coaching.application.port;

import com.loading.acttub_backend.coaching.application.model.CoachingInput;
import com.loading.acttub_backend.coaching.application.model.VideoInput;
import com.loading.acttub_backend.coaching.domain.CoachingAnalysisResult;
import com.loading.acttub_backend.coaching.domain.CoachingDiagnosisResult;
import com.loading.acttub_backend.coaching.domain.CoachingInitialContext;

public interface CoachingAnalyzer {

	CoachingAnalysisResult analyze(VideoInput video, CoachingInput input);

	default CoachingDiagnosisResult diagnose(CoachingInput input, CoachingInitialContext initialContext, String answer) {
		throw new UnsupportedOperationException("Coaching diagnosis is not implemented.");
	}
}
