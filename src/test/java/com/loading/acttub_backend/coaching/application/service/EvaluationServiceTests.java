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
								List.of("처방"),
								"기대 효과"
						))
				)
		), OffsetDateTime.parse("2026-06-13T00:01:00+09:00"));
		return coaching;
	}
}
