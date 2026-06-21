package com.loading.acttub_backend.coaching.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "coaching_feedback_observations")
public class CoachingFeedbackObservation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(columnDefinition = "text")
	private String timecode;

	@Column(nullable = false, columnDefinition = "text")
	private String text;

	protected CoachingFeedbackObservation() {
	}

	public CoachingFeedbackObservation(CoachFeedback.Observation observation) {
		this.timecode = observation.timecode();
		this.text = observation.text();
	}

	CoachFeedback.Observation toObservation() {
		return new CoachFeedback.Observation(timecode, text);
	}
}
