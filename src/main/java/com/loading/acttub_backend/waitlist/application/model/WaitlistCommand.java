package com.loading.acttub_backend.waitlist.application.model;

public record WaitlistCommand(
		String email,
		String actorStatus,
		String helpTopic,
		Boolean consent,
		String website
) {
}
