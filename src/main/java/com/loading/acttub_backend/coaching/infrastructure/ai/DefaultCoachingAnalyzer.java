package com.loading.acttub_backend.coaching.infrastructure.ai;

import java.math.BigDecimal;
import java.util.List;

import com.loading.acttub_backend.coaching.application.model.CoachingInput;
import com.loading.acttub_backend.coaching.application.model.VideoInput;
import com.loading.acttub_backend.coaching.application.port.CoachingAnalyzer;
import com.loading.acttub_backend.coaching.domain.CoachFeedback;
import com.loading.acttub_backend.coaching.domain.CoachingAnalysisResult;
import com.loading.acttub_backend.coaching.domain.CoachingConversationMessage;
import com.loading.acttub_backend.coaching.domain.CoachingConversationResult;
import com.loading.acttub_backend.coaching.domain.CoachingDiagnosisResult;
import com.loading.acttub_backend.coaching.domain.CoachingInitialContext;
import com.loading.acttub_backend.coaching.domain.MeasurableSignal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.coaching.stub-enabled", havingValue = "true")
public class DefaultCoachingAnalyzer implements CoachingAnalyzer {

	@Override
	public CoachingAnalysisResult analyze(VideoInput video, CoachingInput input) {
		CoachingInitialContext initialContext = new CoachingInitialContext(
				"이 장면에서 상대에게 가장 들키고 싶지 않았던 감정은 무엇이었나요?",
				"안녕. 오랜만이네. 잘 지냈어?",
				"첫 대사 전 호흡이 짧아지고 시선이 흔들려 재회 상황의 긴장이 먼저 드러납니다.",
				List.of(new MeasurableSignal("pause_before_first_line", BigDecimal.valueOf(2), "초", "첫 대사 직전 약 2초간 시선 회피")),
				"숨기려는 감정의 이유가 아직 덜 구체화되어 있습니다."
		);
		return new CoachingAnalysisResult("gemini", "Gemini 3.0 Flash", BigDecimal.ZERO, "analysis-v0.4", initialContext);
	}

	@Override
	public CoachingConversationResult continueConversation(CoachingInput input, CoachingInitialContext initialContext,
			List<CoachingConversationMessage> messages, int turnIndex, int minTurns, int maxTurns) {
		if (turnIndex >= minTurns) {
			return new CoachingConversationResult(
					true,
					null,
					"인물의 숨기는 이유와 관계 목표가 충분히 구체화되었습니다.",
					"사용자는 미련을 숨기는 이유가 상대에게 약해 보이고 싶지 않기 때문이라고 구체화했습니다.",
					"재회한 상대에게 흔들리는 마음을 들키지 않으려는 상황",
					"미련을 숨기며 평정심을 유지해 주도권을 잃지 않으려는 의도"
			);
		}
		return new CoachingConversationResult(
				false,
				turnIndex == 1
						? "그 감정을 숨기는 이유는 상대에게 약해 보이지 않기 위해서인가요, 다시 붙잡고 싶은 마음을 감추기 위해서인가요?"
						: "그 선택이 장면 끝에서는 상대와 어떤 거리로 남기를 바라나요?",
				"최소 3턴 전이므로 의도 구체화를 계속합니다.",
				null,
				null,
				null
		);
	}

	@Override
	public CoachingDiagnosisResult diagnose(CoachingInput input, CoachingInitialContext initialContext,
			List<CoachingConversationMessage> messages, String conversationSummary, String refinedSituation,
			String refinedIntent) {
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

		return new CoachingDiagnosisResult(feedback);
	}
}
