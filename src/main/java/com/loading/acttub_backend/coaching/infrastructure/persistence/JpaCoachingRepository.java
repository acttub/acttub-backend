package com.loading.acttub_backend.coaching.infrastructure.persistence;

import com.loading.acttub_backend.coaching.application.port.CoachingRepository;
import com.loading.acttub_backend.coaching.domain.Coaching;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaCoachingRepository extends JpaRepository<Coaching, Long>, CoachingRepository {
}
