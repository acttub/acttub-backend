package com.loading.acttub_backend.waitlist.application.port;

import com.loading.acttub_backend.waitlist.domain.WaitlistRegistration;

public interface WaitlistRegistrationRepository {

	WaitlistRegistration saveAndFlush(WaitlistRegistration registration);

	boolean existsByEmail(String email);
}
