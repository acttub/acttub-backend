package com.loading.acttub_backend.coaching.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "coaching_evaluations")
public class CoachingEvaluation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long coachingId;

	@Column(nullable = false)
	private int rating;

	@Column(columnDefinition = "text")
	private String comment;

	@Column(nullable = false)
	private OffsetDateTime createdAt;

	protected CoachingEvaluation() {
	}

	public CoachingEvaluation(Long coachingId, int rating, String comment, OffsetDateTime createdAt) {
		this.coachingId = coachingId;
		this.rating = rating;
		this.comment = comment;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public Long getCoachingId() {
		return coachingId;
	}

	public int getRating() {
		return rating;
	}

	public String getComment() {
		return comment;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}
}
