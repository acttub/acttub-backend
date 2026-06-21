package com.loading.acttub_backend.coaching.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class CoachingTests {

	@Test
	void completesWithCardFeedback() {
		Coaching coaching = Coaching.analyzing(
				"영화",
				null,
				"헤어진 연인을 우연히 다시 만난 상황",
				"감정을 쉽게 드러내지 않는 배우 지망생",
				"아직 미련이 있지만 괜찮은 척한다",
				"scene.mp4",
				"video/mp4",
				1024L,
				OffsetDateTime.parse("2026-06-13T00:00:00+09:00")
		);

		coaching.complete(new CoachingAnalysisResult(
				"gemini",
				"Gemini 3.0 Flash",
				BigDecimal.ZERO,
				"analysis-v0.1",
				new CoachFeedback(
						new CoachFeedback.OverallStrength("전체 강점"),
						List.of(new CoachFeedback.FeedbackCard(
								1,
								"장면 의도",
								List.of(new CoachFeedback.Observation("0:00-0:15", "관찰")),
								"원인",
								List.of("처방1", "처방2"),
								"기대 효과"
						))
				)
		), OffsetDateTime.parse("2026-06-13T00:01:00+09:00"));

		CoachFeedback feedback = coaching.getFeedback();

		assertThat(feedback.overallStrength().text()).isEqualTo("전체 강점");
		assertThat(feedback.feedbackCards()).hasSize(1);
		assertThat(feedback.feedbackCards().getFirst().practiceSteps()).containsExactly("처방1", "처방2");
	}
}
