package com.loading.acttub_backend.coaching.infrastructure.persistence;

import com.loading.acttub_backend.coaching.application.port.CoachingConversationMessageRepository;
import com.loading.acttub_backend.coaching.domain.CoachingConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaCoachingConversationMessageRepository
		extends JpaRepository<CoachingConversationMessage, Long>, CoachingConversationMessageRepository {
}
