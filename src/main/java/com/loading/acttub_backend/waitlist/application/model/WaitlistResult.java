package com.loading.acttub_backend.waitlist.application.model;

import java.time.OffsetDateTime;

public record WaitlistResult(
		boolean accepted,
		String waitlistId,
		String email,
		String actorStatus,
		String helpTopic,
		OffsetDateTime createdAt
) {

	public static WaitlistResult acceptedWithoutRegistration() {
		return new WaitlistResult(true, null, null, null, null, null);
	}
}
