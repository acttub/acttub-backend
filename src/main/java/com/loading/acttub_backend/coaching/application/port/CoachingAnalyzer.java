package com.loading.acttub_backend.coaching.application.port;

import com.loading.acttub_backend.coaching.domain.CoachingAnalysisResult;
import org.springframework.web.multipart.MultipartFile;

public interface CoachingAnalyzer {

	CoachingAnalysisResult analyze(MultipartFile video, String performanceIntent);
}
