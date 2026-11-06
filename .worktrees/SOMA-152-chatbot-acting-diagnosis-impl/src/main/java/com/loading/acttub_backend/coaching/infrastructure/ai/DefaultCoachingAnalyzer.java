package com.loading.acttub_backend.coaching.infrastructure.ai;

import java.math.BigDecimal;
import java.util.List;

import com.loading.acttub_backend.coaching.application.model.CoachingInput;
import com.loading.acttub_backend.coaching.application.model.VideoInput;
import com.loading.acttub_backend.coaching.application.port.CoachingAnalyzer;
import com.loading.acttub_backend.coaching.domain.CoachFeedback;
import com.loading.acttub_backend.coaching.domain.CoachingAnalysisResult;
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
				"초반에는 괜찮은 척하는 표면이 유지되지만, 중간 멈춤에서 미련이 새어 나오는 흐름이 보입니다.",
				List.of(
						new MeasurableSignal("pauseCount", BigDecimal.valueOf(3), "count", "0:12, 0:28, 0:41에서 대사 사이 멈춤이 확인됩니다."),
						new MeasurableSignal("longestPauseSeconds", BigDecimal.valueOf(2.4), "seconds", "0:28 이후 약 2.4초 동안 다음 대사로 넘어가지 않습니다.")
				),
				"상대를 놓지 못한 마음은 보이지만, 왜 그것을 숨겨야 하는지가 아직 선명하지 않은 것 같아요."
		);

		return new CoachingAnalysisResult("gemini", "Gemini 3.0 Flash", BigDecimal.ZERO, "analysis-v0.2", initialContext);
	}

	@Override
	public CoachingDiagnosisResult diagnose(CoachingInput input, CoachingInitialContext initialContext, String answer) {
		CoachFeedback feedback = new CoachFeedback(
				new CoachFeedback.OverallStrength("전반적으로 감정을 바로 터뜨리지 않고 버티는 선택이 장면의 방향과 잘 맞았어요."),
				List.of(new CoachFeedback.FeedbackCard(
						1,
						"상대에게 약하게 보이지 않으려는 이유를 첫 멈춤 전에 더 분명히 잡아보세요",
						List.of(new CoachFeedback.Observation("0:28", "대사 사이 멈춤이 길어지며 다음 말을 고르는 시간이 보였어요.")),
						"왜냐면 미련을 숨기는 선택은 보이지만, 약하게 보이고 싶지 않다는 방어의 이유가 몸과 시선에 아직 충분히 걸리지 않은 것 같아요.",
						List.of(
								"첫 대사 전 상대가 나를 불쌍하게 보는 장면을 한 번 떠올리고 시선을 2초 고정하세요.",
								"0:28 지점의 멈춤에서는 고개를 숙이지 말고 숨만 한 번 삼키세요.",
								"그 상태로 첫 세 문장만 바로 다시 녹화하세요."
						),
						"그럼 감정을 누르는 이유가 선명해져서 장면이 더 구체적으로 잡혀요."
				))
		);

		return new CoachingDiagnosisResult(
				input.situation() + "에서 " + answer,
				"미련을 숨기면서도 상대가 자신을 쉽게 지나치지 못하게 만들고 싶다",
				feedback
		);
	}
}
