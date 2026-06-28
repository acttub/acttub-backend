package com.loading.acttub_backend.waitlist.infrastructure.persistence;

import com.loading.acttub_backend.waitlist.application.port.WaitlistRegistrationRepository;
import com.loading.acttub_backend.waitlist.domain.WaitlistRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaWaitlistRegistrationRepository
		extends JpaRepository<WaitlistRegistration, Long>, WaitlistRegistrationRepository {
}
