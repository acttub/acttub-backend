package com.loading.acttub_backend.coaching.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class CoachingTests {

	@Test
	void normalizesFocusAxesWhenCompleting() {
		Coaching coaching = Coaching.analyzing(
				"차분하지만 단호한 감정",
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
						new CoachFeedback.SceneIntent("장면 의도", "actor_input"),
						new CoachFeedback.Strength("0:48", "emotion", "좋은 신호", "좋은 이유", "execution"),
						new CoachFeedback.Focus(
								"0:00-0:15",
								Arrays.asList(" emotion ", null, "speech", "emotion", "", "  "),
								"관찰",
								"원인",
								"차이",
								"처방"
						),
						new CoachFeedback.NextStep("다음 단계", "retake_selected_range")
				)
		), OffsetDateTime.parse("2026-06-13T00:01:00+09:00"));

		assertThat(coaching.getResultFocusAxes()).containsExactly("emotion", "speech");
	}
}
