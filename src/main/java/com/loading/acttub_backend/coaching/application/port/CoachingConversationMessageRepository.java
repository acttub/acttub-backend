package com.loading.acttub_backend.coaching.application.port;

import java.util.List;

import com.loading.acttub_backend.coaching.domain.CoachingConversationMessage;

public interface CoachingConversationMessageRepository {

	CoachingConversationMessage saveAndFlush(CoachingConversationMessage message);

	List<CoachingConversationMessage> findByCoachingIdOrderByTurnIndexAscRoleAsc(Long coachingId);
}
