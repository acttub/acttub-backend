package com.loading.acttub_backend.coaching.application.port;

import com.loading.acttub_backend.coaching.domain.CoachingEvaluation;

public interface CoachingEvaluationRepository {

	CoachingEvaluation save(CoachingEvaluation evaluation);

	boolean existsByCoachingId(Long coachingId);
}
