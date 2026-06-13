package com.loading.acttub_backend.coaching.application.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.loading.acttub_backend.coaching.application.model.EvaluationCommand;
import com.loading.acttub_backend.coaching.application.model.EvaluationResult;
import com.loading.acttub_backend.coaching.application.port.CoachingEvaluationRepository;
import com.loading.acttub_backend.coaching.application.port.CoachingRepository;
import com.loading.acttub_backend.coaching.domain.Coaching;
import com.loading.acttub_backend.coaching.domain.CoachingEvaluation;
import com.loading.acttub_backend.coaching.domain.CoachingStatus;
import com.loading.acttub_backend.global.api.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluationService {

	private final CoachingRepository coachingRepository;
	private final CoachingEvaluationRepository evaluationRepository;
	private final Clock clock;

	public EvaluationService(CoachingRepository coachingRepository, CoachingEvaluationRepository evaluationRepository) {
		this.coachingRepository = coachingRepository;
		this.evaluationRepository = evaluationRepository;
		this.clock = Clock.systemDefaultZone();
	}

	@Transactional
	public EvaluationResult evaluate(Long coachingId, EvaluationCommand command) {
		validateRating(command);

		Coaching coaching = findCoaching(coachingId);
		validateEvaluable(coaching);
		validateNotEvaluated(coachingId);

		CoachingEvaluation evaluation = saveEvaluation(coachingId, command);
		return toResponse(evaluation);
	}

	private void validateRating(EvaluationCommand command) {
		if (command.rating() == null || command.rating() < 1 || command.rating() > 5) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"INVALID_EVALUATION_REQUEST",
					"Invalid evaluation request.",
					Map.of("fields", List.of("rating"))
			);
		}
	}

	private Coaching findCoaching(Long coachingId) {
		return coachingRepository.findById(coachingId)
				.orElseThrow(() -> new ApiException(
						HttpStatus.NOT_FOUND,
						"COACHING_NOT_FOUND",
						"Coaching not found.",
						Map.of("coachingId", String.valueOf(coachingId))
				));
	}

	private void validateEvaluable(Coaching coaching) {
		if (coaching.getStatus() != CoachingStatus.COMPLETED) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"COACHING_NOT_EVALUABLE",
					"Only completed coachings can be evaluated.",
					Map.of("coachingId", String.valueOf(coaching.getId()), "status", coaching.getStatus().name())
			);
		}
	}

	private void validateNotEvaluated(Long coachingId) {
		if (evaluationRepository.existsByCoachingId(coachingId)) {
			throw alreadyEvaluated(coachingId);
		}
	}

	private CoachingEvaluation saveEvaluation(Long coachingId, EvaluationCommand command) {
		try {
			return evaluationRepository.saveAndFlush(new CoachingEvaluation(
					coachingId,
					command.rating(),
					normalizeComment(command.comment()),
					OffsetDateTime.now(clock)
			));
		} catch (DataIntegrityViolationException e) {
			throw alreadyEvaluated(coachingId);
		}
	}

	private ApiException alreadyEvaluated(Long coachingId) {
		return new ApiException(
				HttpStatus.CONFLICT,
				"COACHING_EVALUATION_ALREADY_EXISTS",
				"Coaching evaluation already exists.",
				Map.of("coachingId", String.valueOf(coachingId))
		);
	}

	private EvaluationResult toResponse(CoachingEvaluation evaluation) {
		return new EvaluationResult(
				String.valueOf(evaluation.getId()),
				String.valueOf(evaluation.getCoachingId()),
				evaluation.getRating(),
				evaluation.getComment(),
				evaluation.getCreatedAt()
		);
	}

	private String normalizeComment(String comment) {
		if (comment == null || comment.isBlank()) {
			return null;
		}
		return comment;
	}
}
