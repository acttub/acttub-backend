package com.loading.acttub_backend.coaching.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "coachings")
public class Coaching {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final TypeReference<List<MeasurableSignal>> MEASURABLE_SIGNALS_TYPE = new TypeReference<>() {
	};

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "text")
	private CoachingStatus status;

	@Column(columnDefinition = "text")
	private String videoOriginalFilename;
	@Column(columnDefinition = "text")
	private String videoContentType;
	private Long videoSizeBytes;
	@Column(columnDefinition = "text")
	private String videoStorageKey;
	@Column(columnDefinition = "text")
	private String videoStorageUri;

	@Column(columnDefinition = "text")
	private String genre;
	@Column(columnDefinition = "text")
	private String customGenre;
	@Column(columnDefinition = "text")
	private String situation;
	@Column(columnDefinition = "text")
	private String characterSetting;
	@Column(columnDefinition = "text")
	private String subtext;

	@Column(columnDefinition = "text")
	private String aiProvider;
	@Column(columnDefinition = "text")
	private String aiModel;
	private BigDecimal aiTemperature;
	@Column(columnDefinition = "text")
	private String aiPromptVersion;

	@Column(columnDefinition = "text")
	private String initialQuestion;
	@Column(columnDefinition = "text")
	private String initialTranscript;
	@Column(columnDefinition = "text")
	private String initialAnalysis;
	@Column(columnDefinition = "text")
	private String initialMeasurableSignals;
	@Column(columnDefinition = "text")
	private String initialDiagnosticHypothesis;
	@Column(columnDefinition = "text")
	private String conversationSummary;
	@Column(columnDefinition = "text")
	private String refinedSituation;
	@Column(columnDefinition = "text")
	private String refinedIntent;

	@Column(columnDefinition = "text")
	private String resultOverallStrengthText;

	@OneToMany(cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "coaching_id", nullable = false)
	@OrderColumn(name = "card_order")
	private List<CoachingFeedbackCard> feedbackCards = new ArrayList<>();

	@Column(columnDefinition = "text")
	private String resultSceneIntent;
	@Column(columnDefinition = "text")
	private String resultSceneIntentSource;
	@Column(columnDefinition = "text")
	private String resultStrengthTimecode;
	@Column(columnDefinition = "text")
	private String resultStrengthAxis;
	@Column(columnDefinition = "text")
	private String resultStrengthSignal;
	@Column(columnDefinition = "text")
	private String resultStrengthWhy;
	@Column(columnDefinition = "text")
	private String resultStrengthTier;
	@Column(columnDefinition = "text")
	private String resultFocusTimecode;

	@Column(columnDefinition = "text")
	private String resultFocusObservedSignal;
	@Column(columnDefinition = "text")
	private String resultFocusRootCause;
	@Column(columnDefinition = "text")
	private String resultFocusIntentGap;
	@Column(columnDefinition = "text")
	private String resultFocusPrescription;
	@Column(columnDefinition = "text")
	private String resultNextStepText;
	@Column(columnDefinition = "text")
	private String resultNextStepAction;
	@Column(columnDefinition = "text")
	private String failureCode;
	@Column(columnDefinition = "text")
	private String failureMessage;

	@Column(nullable = false)
	private OffsetDateTime createdAt;

	@Column(nullable = false)
	private OffsetDateTime updatedAt;

	private OffsetDateTime completedAt;

	protected Coaching() {
	}

	public static Coaching analyzing(String genre, String customGenre, String situation, String characterSetting,
			String subtext, String originalFilename, String contentType, long sizeBytes,
			OffsetDateTime now) {
		Coaching coaching = new Coaching();
		coaching.status = CoachingStatus.ANALYZING;
		coaching.genre = genre;
		coaching.customGenre = customGenre;
		coaching.situation = situation;
		coaching.characterSetting = characterSetting;
		coaching.subtext = subtext;
		coaching.videoOriginalFilename = originalFilename;
		coaching.videoContentType = contentType;
		coaching.videoSizeBytes = sizeBytes;
		coaching.createdAt = now;
		coaching.updatedAt = now;
		return coaching;
	}

	public void complete(CoachingAnalysisResult analysisResult, OffsetDateTime now) {
		applyFeedback(analysisResult.feedback());
		this.status = CoachingStatus.COMPLETED;
		this.aiProvider = analysisResult.provider();
		this.aiModel = analysisResult.model();
		this.aiTemperature = analysisResult.temperature();
		this.aiPromptVersion = analysisResult.promptVersion();
		this.completedAt = now;
		this.updatedAt = now;
	}

	public void markQuestionReady(CoachingInitialContext initialContext, CoachingAnalysisResult analysisResult,
			OffsetDateTime now) {
		this.status = CoachingStatus.QUESTION_READY;
		this.aiProvider = analysisResult.provider();
		this.aiModel = analysisResult.model();
		this.aiTemperature = analysisResult.temperature();
		this.aiPromptVersion = analysisResult.promptVersion();
		this.initialQuestion = initialContext.question();
		this.initialTranscript = initialContext.transcript();
		this.initialAnalysis = initialContext.analysis();
		this.initialMeasurableSignals = serializeSignals(initialContext.measurableSignals());
		this.initialDiagnosticHypothesis = initialContext.diagnosticHypothesis();
		this.updatedAt = now;
	}

	public void continueConversation(OffsetDateTime now) {
		this.status = CoachingStatus.IN_CONVERSATION;
		this.updatedAt = now;
	}

	public void markReadyForDiagnosis(CoachingConversationResult conversationResult, OffsetDateTime now) {
		this.status = CoachingStatus.READY_FOR_DIAGNOSIS;
		this.conversationSummary = conversationResult.summary();
		this.refinedSituation = conversationResult.refinedSituation();
		this.refinedIntent = conversationResult.refinedIntent();
		this.updatedAt = now;
	}

	public void completeDiagnosis(CoachingDiagnosisResult diagnosisResult, OffsetDateTime now) {
		applyFeedback(diagnosisResult.feedback());
		this.status = CoachingStatus.COMPLETED;
		this.completedAt = now;
		this.updatedAt = now;
	}

	private void applyFeedback(CoachFeedback feedback) {
		CoachFeedback.FeedbackCard primaryCard = firstFeedbackCard(feedback);
		CoachFeedback.Observation primaryObservation = firstObservation(primaryCard);
		this.resultOverallStrengthText = feedback.overallStrength().text();
		this.feedbackCards.clear();
		this.feedbackCards.addAll(feedback.feedbackCards().stream()
				.map(CoachingFeedbackCard::new)
				.toList());
		this.resultSceneIntent = primaryCard.title();
		this.resultSceneIntentSource = "actor_input";
		this.resultStrengthTimecode = null;
		this.resultStrengthAxis = null;
		this.resultStrengthSignal = resultOverallStrengthText;
		this.resultStrengthWhy = null;
		this.resultStrengthTier = null;
		this.resultFocusTimecode = primaryObservation.timecode();
		this.resultFocusObservedSignal = primaryObservation.text();
		this.resultFocusRootCause = primaryCard.cause();
		this.resultFocusIntentGap = primaryCard.expectedEffect();
		this.resultFocusPrescription = String.join("\n", primaryCard.practiceSteps());
		this.resultNextStepText = primaryCard.expectedEffect();
		this.resultNextStepAction = "retake_selected_range";
	}

	public void attachVideo(String storageKey, String storageUri, OffsetDateTime now) {
		this.videoStorageKey = storageKey;
		this.videoStorageUri = storageUri;
		this.updatedAt = now;
	}

	public void fail(String failureCode, String failureMessage, OffsetDateTime now) {
		this.status = CoachingStatus.FAILED;
		this.failureCode = failureCode;
		this.failureMessage = failureMessage;
		this.updatedAt = now;
		this.completedAt = now;
	}

	private CoachFeedback.FeedbackCard firstFeedbackCard(CoachFeedback feedback) {
		if (feedback.feedbackCards() == null || feedback.feedbackCards().isEmpty()) {
			throw new IllegalArgumentException("Feedback card is required.");
		}
		return feedback.feedbackCards().getFirst();
	}

	private CoachFeedback.Observation firstObservation(CoachFeedback.FeedbackCard card) {
		if (card.observations() == null || card.observations().isEmpty()) {
			throw new IllegalArgumentException("Feedback card observation is required.");
		}
		return card.observations().getFirst();
	}

	private String serializeSignals(List<MeasurableSignal> signals) {
		try {
			return OBJECT_MAPPER.writeValueAsString(signals == null ? List.of() : signals);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Failed to serialize measurable signals.", e);
		}
	}

	private List<MeasurableSignal> deserializeSignals() {
		if (initialMeasurableSignals == null || initialMeasurableSignals.isBlank()) {
			return List.of();
		}
		try {
			return OBJECT_MAPPER.readValue(initialMeasurableSignals, MEASURABLE_SIGNALS_TYPE);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to deserialize measurable signals.", e);
		}
	}

	public Long getId() {
		return id;
	}

	public CoachingStatus getStatus() {
		return status;
	}

	public String getGenre() {
		return genre;
	}

	public String getCustomGenre() {
		return customGenre;
	}

	public String getSituation() {
		return situation;
	}

	public String getCharacterSetting() {
		return characterSetting;
	}

	public String getSubtext() {
		return subtext;
	}

	public String getVideoStorageKey() {
		return videoStorageKey;
	}

	public String getVideoStorageUri() {
		return videoStorageUri;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getCompletedAt() {
		return completedAt;
	}

	public CoachingInitialContext getInitialContext() {
		if (initialQuestion == null) {
			return null;
		}
		return new CoachingInitialContext(
				initialQuestion,
				initialTranscript,
				initialAnalysis,
				deserializeSignals(),
				initialDiagnosticHypothesis
		);
	}

	public String getInitialQuestion() {
		return initialQuestion;
	}

	public String getConversationSummary() {
		return conversationSummary;
	}

	public String getRefinedSituation() {
		return refinedSituation;
	}

	public String getRefinedIntent() {
		return refinedIntent;
	}

	public String getResultSceneIntent() {
		return resultSceneIntent;
	}

	public CoachFeedback getFeedback() {
		return new CoachFeedback(
				new CoachFeedback.OverallStrength(resultOverallStrengthText),
				feedbackCards.stream()
						.map(CoachingFeedbackCard::toFeedbackCard)
						.toList()
		);
	}

	public String getResultSceneIntentSource() {
		return resultSceneIntentSource;
	}

	public String getResultStrengthTimecode() {
		return resultStrengthTimecode;
	}

	public String getResultStrengthAxis() {
		return resultStrengthAxis;
	}

	public String getResultStrengthSignal() {
		return resultStrengthSignal;
	}

	public String getResultStrengthWhy() {
		return resultStrengthWhy;
	}

	public String getResultStrengthTier() {
		return resultStrengthTier;
	}

	public String getResultFocusTimecode() {
		return resultFocusTimecode;
	}

	public String getResultFocusObservedSignal() {
		return resultFocusObservedSignal;
	}

	public String getResultFocusRootCause() {
		return resultFocusRootCause;
	}

	public String getResultFocusIntentGap() {
		return resultFocusIntentGap;
	}

	public String getResultFocusPrescription() {
		return resultFocusPrescription;
	}

	public String getResultNextStepText() {
		return resultNextStepText;
	}

	public String getResultNextStepAction() {
		return resultNextStepAction;
	}

	public String getFailureCode() {
		return failureCode;
	}
}
