package com.loading.acttub_backend.waitlist.presentation.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaitlistResponse(
		boolean accepted,
		String waitlistId,
		String email,
		String actorStatus,
		String helpTopic,
		OffsetDateTime createdAt
) {
}
