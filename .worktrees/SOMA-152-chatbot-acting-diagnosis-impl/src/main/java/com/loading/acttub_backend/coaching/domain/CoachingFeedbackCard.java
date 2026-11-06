package com.loading.acttub_backend.coaching.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "coaching_feedback_cards")
public class CoachingFeedbackCard {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private int displayOrder;

	@Column(columnDefinition = "text")
	private String title;

	@OneToMany(cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "feedback_card_id", nullable = false)
	@OrderColumn(name = "observation_order")
	private List<CoachingFeedbackObservation> observations = new ArrayList<>();

	@Column(columnDefinition = "text")
	private String cause;

	@ElementCollection
	@CollectionTable(name = "coaching_practice_steps", joinColumns = @JoinColumn(name = "feedback_card_id"))
	@OrderColumn(name = "step_order")
	@Column(name = "text", nullable = false, columnDefinition = "text")
	private List<String> practiceSteps = new ArrayList<>();

	@Column(columnDefinition = "text")
	private String expectedEffect;

	protected CoachingFeedbackCard() {
	}

	public CoachingFeedbackCard(CoachFeedback.FeedbackCard feedbackCard) {
		this.displayOrder = feedbackCard.order();
		this.title = feedbackCard.title();
		this.cause = feedbackCard.cause();
		this.expectedEffect = feedbackCard.expectedEffect();
		if (feedbackCard.observations() != null) {
			this.observations = new ArrayList<>(feedbackCard.observations().stream()
					.map(CoachingFeedbackObservation::new)
					.toList());
		}
		if (feedbackCard.practiceSteps() != null) {
			this.practiceSteps = new ArrayList<>(feedbackCard.practiceSteps().stream()
					.filter(step -> step != null && !step.isBlank())
					.toList());
		}
	}

	CoachFeedback.FeedbackCard toFeedbackCard() {
		return new CoachFeedback.FeedbackCard(
				displayOrder,
				title,
				observations.stream()
						.map(CoachingFeedbackObservation::toObservation)
						.toList(),
				cause,
				List.copyOf(practiceSteps),
				expectedEffect
		);
	}
}
