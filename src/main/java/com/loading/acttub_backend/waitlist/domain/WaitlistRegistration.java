package com.loading.acttub_backend.waitlist.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "waitlist_registrations")
public class WaitlistRegistration {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(columnDefinition = "text")
	private String actorStatus;

	@Column(columnDefinition = "text")
	private String helpTopic;

	@Column(nullable = false)
	private boolean consent;

	@Column(nullable = false)
	private OffsetDateTime createdAt;

	protected WaitlistRegistration() {
	}

	public WaitlistRegistration(String email, String actorStatus, String helpTopic, boolean consent, OffsetDateTime createdAt) {
		this.email = email;
		this.actorStatus = actorStatus;
		this.helpTopic = helpTopic;
		this.consent = consent;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getActorStatus() {
		return actorStatus;
	}

	public String getHelpTopic() {
		return helpTopic;
	}

	public boolean isConsent() {
		return consent;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}
}
