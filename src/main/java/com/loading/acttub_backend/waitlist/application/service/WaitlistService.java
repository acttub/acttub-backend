package com.loading.acttub_backend.waitlist.application.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.loading.acttub_backend.waitlist.application.model.WaitlistCommand;
import com.loading.acttub_backend.waitlist.application.model.WaitlistException;
import com.loading.acttub_backend.waitlist.application.model.WaitlistResult;
import com.loading.acttub_backend.waitlist.application.port.WaitlistRegistrationRepository;
import com.loading.acttub_backend.waitlist.domain.WaitlistRegistration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WaitlistService {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	private static final Set<String> ALLOWED_ACTOR_STATUSES = Set.of(
			"연기 입시 준비 중",
			"연극영화과 재학 중",
			"배우 지망생",
			"현역 배우",
			"취미로 연기 연습 중",
			"기타"
	);
	private static final Set<String> ALLOWED_HELP_TOPICS = Set.of(
			"대본 분석",
			"인물의 목적 찾기",
			"이전 상황 감정 정리",
			"현재 감정선 정리",
			"서브텍스트 정리",
			"기타"
	);

	private final WaitlistRegistrationRepository waitlistRepository;
	private final Clock clock;

	public WaitlistService(WaitlistRegistrationRepository waitlistRepository) {
		this.waitlistRepository = waitlistRepository;
		this.clock = Clock.systemDefaultZone();
	}

	@Transactional
	public WaitlistResult register(WaitlistCommand command) {
		if (isHoneypotFilled(command)) {
			return WaitlistResult.acceptedWithoutRegistration();
		}

		NormalizedWaitlist normalized = validate(command);
		if (waitlistRepository.existsByEmail(normalized.email())) {
			throw alreadyRegistered(normalized.email());
		}

		try {
			WaitlistRegistration registration = waitlistRepository.saveAndFlush(new WaitlistRegistration(
					normalized.email(),
					normalized.actorStatus(),
					normalized.helpTopic(),
					true,
					OffsetDateTime.now(clock)
			));
			return new WaitlistResult(
					true,
					String.valueOf(registration.getId()),
					registration.getEmail(),
					registration.getActorStatus(),
					registration.getHelpTopic(),
					registration.getCreatedAt()
			);
		} catch (DataIntegrityViolationException e) {
			throw alreadyRegistered(normalized.email());
		}
	}

	private boolean isHoneypotFilled(WaitlistCommand command) {
		return command != null && command.website() != null && !command.website().isBlank();
	}

	private NormalizedWaitlist validate(WaitlistCommand command) {
		List<String> invalidFields = new ArrayList<>();
		if (command == null) {
			throw invalidRequest(List.of("body"));
		}

		String email = normalizeEmail(command.email());
		String actorStatus = normalize(command.actorStatus());
		String helpTopic = normalize(command.helpTopic());

		if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
			invalidFields.add("email");
		}
		if (!Boolean.TRUE.equals(command.consent())) {
			invalidFields.add("consent");
		}
		if (actorStatus != null && !ALLOWED_ACTOR_STATUSES.contains(actorStatus)) {
			invalidFields.add("actorStatus");
		}
		if (helpTopic != null && !ALLOWED_HELP_TOPICS.contains(helpTopic)) {
			invalidFields.add("helpTopic");
		}
		if (!invalidFields.isEmpty()) {
			throw invalidRequest(invalidFields);
		}

		return new NormalizedWaitlist(email, actorStatus, helpTopic);
	}

	private String normalizeEmail(String email) {
		String normalized = normalize(email);
		if (normalized == null) {
			return null;
		}
		return normalized.toLowerCase(Locale.ROOT);
	}

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		if (normalized.isBlank()) {
			return null;
		}
		return normalized;
	}

	private WaitlistException invalidRequest(List<String> fields) {
		return new WaitlistException(
				"INVALID_WAITLIST_REQUEST",
				"Invalid waitlist request.",
				Map.of("fields", fields)
		);
	}

	private WaitlistException alreadyRegistered(String email) {
		return new WaitlistException(
				"WAITLIST_ALREADY_REGISTERED",
				"Waitlist email already registered.",
				Map.of("email", email)
		);
	}

	private record NormalizedWaitlist(String email, String actorStatus, String helpTopic) {
	}
}
