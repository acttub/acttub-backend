package com.loading.acttub_backend.coaching.infrastructure.ai;

import java.math.BigDecimal;
import java.util.List;

import com.loading.acttub_backend.coaching.application.model.CoachingInput;
import com.loading.acttub_backend.coaching.application.model.VideoInput;
import com.loading.acttub_backend.coaching.application.port.CoachingAnalyzer;
import com.loading.acttub_backend.coaching.domain.CoachFeedback;
import com.loading.acttub_backend.coaching.domain.CoachingAnalysisResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.coaching.stub-enabled", havingValue = "true")
public class DefaultCoachingAnalyzer implements CoachingAnalyzer {

	@Override
	public CoachingAnalysisResult analyze(VideoInput video, CoachingInput input) {
		CoachFeedback feedback = new CoachFeedback(
				new CoachFeedback.OverallStrength("전반적으로 상대를 떠올리며 흐름을 끝까지 끊지 않은 선택은 살아 있어요."),
				List.of(
						new CoachFeedback.FeedbackCard(
								1,
								"첫 문장 전에 몸의 긴장을 한 번 덜어내면 목표가 더 선명해져요",
								List.of(
										new CoachFeedback.Observation("0:00-0:15", "몸: 첫 대사 전부터 어깨가 올라간 상태로 시작했습니다."),
										new CoachFeedback.Observation("전반", "대사: 문장 끝으로 갈수록 소리가 작아지는 흐름이 반복됐습니다.")
								),
								"왜냐면 장면 목표를 보내기 전에 몸이 먼저 방어 자세를 잡는 선택이 앞선 것 같아요.",
								List.of(
										"촬영 직전 어깨를 귀까지 올렸다가 툭 떨어뜨리기 3번",
										"상대 위치를 한 점으로 정하고 첫 문장 전 2초 동안 바라보기",
										"그 상태 그대로 첫 문장만 다시 녹화하기"
								),
								"그럼 감정이 처음부터 소진되지 않고 장면 목표가 더 또렷하게 전달돼요."
						)
				)
		);

		return new CoachingAnalysisResult("gemini", "Gemini 3.0 Flash", BigDecimal.ZERO, "analysis-v0.1", feedback);
	}
}
