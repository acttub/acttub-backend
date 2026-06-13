package com.loading.acttub_backend.coaching.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "coachings")
public class Coaching {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private CoachingStatus status;

	private String videoOriginalFilename;
	@Column(length = 100)
	private String videoContentType;
	private Long videoSizeBytes;
	@Column(length = 500)
	private String videoStorageKey;
	@Column(length = 1000)
	private String videoStorageUri;

	@Column(nullable = false, columnDefinition = "text")
	private String performanceIntent;

	@Column(length = 50)
	private String aiProvider;
	@Column(length = 100)
	private String aiModel;
	private BigDecimal aiTemperature;
	@Column(length = 50)
	private String aiPromptVersion;

	@Column(columnDefinition = "text")
	private String resultSceneIntent;
	@Column(length = 30)
	private String resultSceneIntentSource;
	@Column(length = 50)
	private String resultStrengthTimecode;
	@Column(length = 30)
	private String resultStrengthAxis;
	@Column(columnDefinition = "text")
	private String resultStrengthSignal;
	@Column(columnDefinition = "text")
	private String resultStrengthWhy;
	@Column(length = 30)
	private String resultStrengthTier;
	@Column(length = 50)
	private String resultFocusTimecode;

	@ElementCollection
	@CollectionTable(name = "coaching_focus_axes", joinColumns = @JoinColumn(name = "coaching_id"))
	@OrderColumn(name = "axis_order")
	@Column(name = "axis", nullable = false, length = 30)
	private List<String> resultFocusAxes = new ArrayList<>();

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
	@Column(length = 50)
	private String resultNextStepAction;
	@Column(length = 100)
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

	public static Coaching analyzing(String performanceIntent, String originalFilename, String contentType, long sizeBytes,
			OffsetDateTime now) {
		Coaching coaching = new Coaching();
		coaching.status = CoachingStatus.ANALYZING;
		coaching.performanceIntent = performanceIntent;
		coaching.videoOriginalFilename = originalFilename;
		coaching.videoContentType = contentType;
		coaching.videoSizeBytes = sizeBytes;
		coaching.createdAt = now;
		coaching.updatedAt = now;
		return coaching;
	}

	public void complete(CoachingAnalysisResult analysisResult, OffsetDateTime now) {
		CoachFeedback feedback = analysisResult.feedback();
		this.status = CoachingStatus.COMPLETED;
		this.aiProvider = analysisResult.provider();
		this.aiModel = analysisResult.model();
		this.aiTemperature = analysisResult.temperature();
		this.aiPromptVersion = analysisResult.promptVersion();
		this.resultSceneIntent = feedback.sceneIntent().text();
		this.resultSceneIntentSource = feedback.sceneIntent().source();
		this.resultStrengthTimecode = feedback.strength().timecode();
		this.resultStrengthAxis = feedback.strength().axis();
		this.resultStrengthSignal = feedback.strength().signal();
		this.resultStrengthWhy = feedback.strength().why();
		this.resultStrengthTier = feedback.strength().tier();
		this.resultFocusTimecode = feedback.focus().timecode();
		this.resultFocusAxes.clear();
		this.resultFocusAxes.addAll(normalizeFocusAxes(feedback.focus().axes()));
		this.resultFocusObservedSignal = feedback.focus().observedSignal();
		this.resultFocusRootCause = feedback.focus().rootCause();
		this.resultFocusIntentGap = feedback.focus().intentGap();
		this.resultFocusPrescription = feedback.focus().prescription();
		this.resultNextStepText = feedback.nextStep().text();
		this.resultNextStepAction = feedback.nextStep().action();
		this.completedAt = now;
		this.updatedAt = now;
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

	private List<String> normalizeFocusAxes(List<String> axes) {
		if (axes == null) {
			return List.of();
		}

		LinkedHashSet<String> normalizedAxes = new LinkedHashSet<>();
		for (String axis : axes) {
			if (axis == null) {
				continue;
			}
			String normalizedAxis = axis.trim();
			if (!normalizedAxis.isBlank()) {
				normalizedAxes.add(normalizedAxis);
			}
		}
		return new ArrayList<>(normalizedAxes);
	}

	public Long getId() {
		return id;
	}

	public CoachingStatus getStatus() {
		return status;
	}

	public String getPerformanceIntent() {
		return performanceIntent;
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

	public String getResultSceneIntent() {
		return resultSceneIntent;
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

	public List<String> getResultFocusAxes() {
		return List.copyOf(resultFocusAxes);
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
