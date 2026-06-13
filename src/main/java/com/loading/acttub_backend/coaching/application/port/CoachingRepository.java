package com.loading.acttub_backend.coaching.application.port;

import java.util.Optional;

import com.loading.acttub_backend.coaching.domain.Coaching;

public interface CoachingRepository {

	Coaching saveAndFlush(Coaching coaching);

	Optional<Coaching> findById(Long coachingId);
}
