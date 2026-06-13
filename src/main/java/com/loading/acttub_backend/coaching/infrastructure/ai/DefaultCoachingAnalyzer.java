package com.loading.acttub_backend.coaching.infrastructure.ai;

import java.math.BigDecimal;
import java.util.List;

import com.loading.acttub_backend.coaching.application.port.CoachingAnalyzer;
import com.loading.acttub_backend.coaching.domain.CoachFeedback;
import com.loading.acttub_backend.coaching.domain.CoachingAnalysisResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@ConditionalOnProperty(name = "app.ai.coaching.stub-enabled", havingValue = "true")
public class DefaultCoachingAnalyzer implements CoachingAnalyzer {

	@Override
	public CoachingAnalysisResult analyze(MultipartFile video, String performanceIntent) {
		CoachFeedback feedback = new CoachFeedback(
				new CoachFeedback.SceneIntent(performanceIntent, "actor_input"),
				new CoachFeedback.Strength(
						"0:48",
						"emotion",
						"시선을 유지한 채 말의 속도를 늦춘 순간",
						"감정을 바로 터뜨리지 않고 버티는 힘이 보여 장면의 의도가 살아났습니다.",
						"execution"
				),
				new CoachFeedback.Focus(
						"0:00-0:15",
						List.of("emotion", "speech"),
						"첫 대사 전부터 어깨와 목소리가 굳어 있었습니다.",
						"도입부 긴장이 먼저 올라와 후반에 감정이 무너질 높이가 줄었습니다.",
						"참다가 무너지는 흐름보다 처음부터 긴장한 사람처럼 보였습니다.",
						"첫 대사는 아직 괜찮은 사람처럼 시작해 보세요."
				),
				new CoachFeedback.NextStep("도입부 0:00-0:15만 다시 찍어보세요.", "retake_selected_range")
		);

		return new CoachingAnalysisResult("gemini", "Gemini 3.0 Flash", BigDecimal.ZERO, "analysis-v0.1", feedback);
	}
}
