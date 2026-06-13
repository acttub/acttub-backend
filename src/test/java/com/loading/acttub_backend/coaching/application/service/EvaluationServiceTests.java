package com.loading.acttub_backend.coaching.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.loading.acttub_backend.coaching.application.model.ApplicationException;
import com.loading.acttub_backend.coaching.application.model.EvaluationCommand;
import com.loading.acttub_backend.coaching.application.port.CoachingEvaluationRepository;
import com.loading.acttub_backend.coaching.application.port.CoachingRepository;
import com.loading.acttub_backend.coaching.domain.CoachFeedback;
import com.loading.acttub_backend.coaching.domain.Coaching;
import com.loading.acttub_backend.coaching.domain.CoachingAnalysisResult;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class EvaluationServiceTests {

	@Test
	void mapsUniqueConstraintViolationToDuplicateEvaluationError() {
		CoachingRepository coachingRepository = mock(CoachingRepository.class);
		CoachingEvaluationRepository evaluationRepository = mock(CoachingEvaluationRepository.class);
		EvaluationService service = new EvaluationService(coachingRepository, evaluationRepository);

		when(coachingRepository.findById(1L)).thenReturn(Optional.of(completedCoaching()));
		when(evaluationRepository.existsByCoachingId(1L)).thenReturn(false);
		when(evaluationRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

		assertThatThrownBy(() -> service.evaluate(1L, new EvaluationCommand(5, "")))
				.isInstanceOf(ApplicationException.class)
				.hasMessage("Coaching evaluation already exists.");
	}

	private Coaching completedCoaching() {
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
						new CoachFeedback.Focus("0:00-0:15", List.of("emotion"), "관찰", "원인", "차이", "처방"),
						new CoachFeedback.NextStep("다음 단계", "retake_selected_range")
				)
		), OffsetDateTime.parse("2026-06-13T00:01:00+09:00"));
		return coaching;
	}
}
