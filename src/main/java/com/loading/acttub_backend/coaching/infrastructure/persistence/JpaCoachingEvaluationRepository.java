package com.loading.acttub_backend.coaching.infrastructure.persistence;

import com.loading.acttub_backend.coaching.application.port.CoachingEvaluationRepository;
import com.loading.acttub_backend.coaching.domain.CoachingEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaCoachingEvaluationRepository
		extends JpaRepository<CoachingEvaluation, Long>, CoachingEvaluationRepository {
}
