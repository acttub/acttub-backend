package com.loading.acttub_backend.waitlist.presentation.dto;

public record WaitlistRequest(
		String email,
		String actorStatus,
		String helpTopic,
		Boolean consent,
		String website
) {
}
