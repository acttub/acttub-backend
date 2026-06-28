package com.loading.acttub_backend.coaching.application.port;

import java.util.List;

import com.loading.acttub_backend.coaching.application.model.CoachingInput;
import com.loading.acttub_backend.coaching.application.model.VideoInput;
import com.loading.acttub_backend.coaching.domain.CoachingAnalysisResult;
import com.loading.acttub_backend.coaching.domain.CoachingConversationMessage;
import com.loading.acttub_backend.coaching.domain.CoachingConversationResult;
import com.loading.acttub_backend.coaching.domain.CoachingDiagnosisResult;
import com.loading.acttub_backend.coaching.domain.CoachingInitialContext;

public interface CoachingAnalyzer {

	CoachingAnalysisResult analyze(VideoInput video, CoachingInput input);

	default CoachingConversationResult continueConversation(CoachingInput input, CoachingInitialContext initialContext,
			List<CoachingConversationMessage> messages, int turnIndex, int minTurns, int maxTurns) {
		if (turnIndex >= minTurns) {
			return new CoachingConversationResult(
					true,
					null,
					"진단에 필요한 최소 대화가 완료되었습니다.",
					"사용자 답변을 바탕으로 연기 의도와 숨기는 이유를 구체화했습니다.",
					input.situation(),
					input.displaySubtext()
			);
		}
		return new CoachingConversationResult(
				false,
				"그 선택을 하게 된 인물의 가장 큰 이유는 무엇인가요?",
				"최소 대화 턴 전이므로 의도 구체화를 계속합니다.",
				null,
				null,
				null
		);
	}

	default CoachingDiagnosisResult diagnose(CoachingInput input, CoachingInitialContext initialContext,
			List<CoachingConversationMessage> messages, String conversationSummary, String refinedSituation,
			String refinedIntent) {
		return new CoachingDiagnosisResult(new com.loading.acttub_backend.coaching.domain.CoachFeedback(
				new com.loading.acttub_backend.coaching.domain.CoachFeedback.OverallStrength(
						"전반적으로 장면을 끝까지 이어가며 인물의 선택을 찾으려는 시도가 보였습니다."
				),
				List.of(new com.loading.acttub_backend.coaching.domain.CoachFeedback.FeedbackCard(
						1,
						"구체화한 의도를 첫 대사 전에 몸으로 한 번 준비해보세요",
						List.of(new com.loading.acttub_backend.coaching.domain.CoachFeedback.Observation(
								"전반",
								"대화에서 구체화한 의도와 영상의 첫 반응 사이를 연결할 여지가 있습니다."
						)),
						"왜냐면 대화로 정리한 목표가 첫 순간부터 몸에 걸리면 장면의 방향이 더 선명해질 수 있기 때문입니다.",
						List.of(
								"첫 대사 전 구체화한 의도를 한 문장으로 속으로 말하기",
								"상대 위치를 1초 바라본 뒤 같은 톤으로 첫 대사 시작하기"
						),
						"그럼 감정 설명이 아니라 인물의 선택으로 장면이 시작됩니다."
				))
		));
	}
}
