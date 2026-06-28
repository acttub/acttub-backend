package com.loading.acttub_backend.coaching.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "coaching_conversation_messages")
public class CoachingConversationMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long coachingId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "text")
	private ConversationRole role;

	@Column(nullable = false)
	private int turnIndex;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Column(nullable = false)
	private OffsetDateTime createdAt;

	protected CoachingConversationMessage() {
	}

	public CoachingConversationMessage(Long coachingId, ConversationRole role, int turnIndex, String content,
			OffsetDateTime createdAt) {
		this.coachingId = coachingId;
		this.role = role;
		this.turnIndex = turnIndex;
		this.content = content;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public Long getCoachingId() {
		return coachingId;
	}

	public ConversationRole getRole() {
		return role;
	}

	public int getTurnIndex() {
		return turnIndex;
	}

	public String getContent() {
		return content;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}
}
