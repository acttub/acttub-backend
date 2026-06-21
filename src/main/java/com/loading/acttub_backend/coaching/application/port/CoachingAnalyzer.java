package com.loading.acttub_backend.coaching.application.port;

import com.loading.acttub_backend.coaching.application.model.CoachingInput;
import com.loading.acttub_backend.coaching.application.model.VideoInput;
import com.loading.acttub_backend.coaching.domain.CoachingAnalysisResult;

public interface CoachingAnalyzer {

	CoachingAnalysisResult analyze(VideoInput video, CoachingInput input);
}
