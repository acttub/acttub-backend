package com.loading.acttub_backend.coaching.application.port;

import com.loading.acttub_backend.coaching.domain.CoachingEvaluation;

public interface CoachingEvaluationRepository {

	CoachingEvaluation saveAndFlush(CoachingEvaluation evaluation);

	boolean existsByCoachingId(Long coachingId);
}
