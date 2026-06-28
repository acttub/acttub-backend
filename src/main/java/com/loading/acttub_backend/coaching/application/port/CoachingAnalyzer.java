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
		throw new UnsupportedOperationException("Coaching diagnosis is not implemented.");
	}
}
