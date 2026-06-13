package com.loading.acttub_backend.coaching.application.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.loading.acttub_backend.coaching.application.model.ApplicationException;
import com.loading.acttub_backend.coaching.application.model.CoachingResult;
import com.loading.acttub_backend.coaching.application.model.StoredVideo;
import com.loading.acttub_backend.coaching.application.model.VideoInput;
import com.loading.acttub_backend.coaching.application.port.CoachingAnalysisTimeoutException;
import com.loading.acttub_backend.coaching.application.port.CoachingAnalyzer;
import com.loading.acttub_backend.coaching.application.port.CoachingRepository;
import com.loading.acttub_backend.coaching.application.port.VideoStorage;
import com.loading.acttub_backend.coaching.domain.CoachFeedback;
import com.loading.acttub_backend.coaching.domain.Coaching;
import com.loading.acttub_backend.coaching.domain.CoachingAnalysisResult;
import com.loading.acttub_backend.coaching.domain.CoachingStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoachingService {

	private static final long MAX_VIDEO_SIZE_BYTES = 314572800L;
	private static final Set<String> ALLOWED_VIDEO_CONTENT_TYPES = Set.of(
			"video/mp4",
			"video/quicktime",
			"video/webm"
	);

	private final CoachingRepository coachingRepository;
	private final CoachingAnalyzer coachingAnalyzer;
	private final VideoStorage videoStorage;
	private final Clock clock;

	public CoachingService(CoachingRepository coachingRepository, CoachingAnalyzer coachingAnalyzer, VideoStorage videoStorage) {
		this.coachingRepository = coachingRepository;
		this.coachingAnalyzer = coachingAnalyzer;
		this.videoStorage = videoStorage;
		this.clock = Clock.systemDefaultZone();
	}

	@Transactional(noRollbackFor = ApplicationException.class)
	public CoachingResult create(VideoInput video, String performanceIntent) {
		validate(video, performanceIntent);

		Coaching coaching = createAnalyzingCoaching(video, performanceIntent);
		storeVideo(coaching, video);
		CoachingAnalysisResult analysisResult = analyze(coaching, video, performanceIntent);

		coaching.complete(analysisResult, OffsetDateTime.now(clock));
		return toResponse(coaching);
	}

	@Transactional(readOnly = true)
	public CoachingResult get(Long coachingId) {
		Coaching coaching = coachingRepository.findById(coachingId)
				.orElseThrow(() -> new ApplicationException(
						"COACHING_NOT_FOUND",
						"Coaching not found.",
						Map.of("coachingId", String.valueOf(coachingId))
				));
		return toResponse(coaching);
	}

	private Coaching createAnalyzingCoaching(VideoInput video, String performanceIntent) {
		OffsetDateTime now = OffsetDateTime.now(clock);
		Coaching coaching = Coaching.analyzing(
				performanceIntent,
				video.originalFilename(),
				video.contentType(),
				video.sizeBytes(),
				now
		);
		return coachingRepository.saveAndFlush(coaching);
	}

	private void storeVideo(Coaching coaching, VideoInput video) {
		try {
			StoredVideo storedVideo = videoStorage.store(coaching.getId(), video);
			coaching.attachVideo(storedVideo.storageKey(), storedVideo.storageUri(), OffsetDateTime.now(clock));
			coachingRepository.saveAndFlush(coaching);
		} catch (RuntimeException e) {
			coaching.fail("VIDEO_STORAGE_FAILED", e.getMessage(), OffsetDateTime.now(clock));
			coachingRepository.saveAndFlush(coaching);
			throw new ApplicationException(
					"VIDEO_STORAGE_FAILED",
					"Video storage failed.",
					Map.of("coachingId", String.valueOf(coaching.getId()), "status", CoachingStatus.FAILED.name())
			);
		}
	}

	private CoachingAnalysisResult analyze(Coaching coaching, VideoInput video, String performanceIntent) {
		try {
			return coachingAnalyzer.analyze(video, performanceIntent);
		} catch (CoachingAnalysisTimeoutException e) {
			coaching.fail("AI_ANALYSIS_TIMEOUT", e.getMessage(), OffsetDateTime.now(clock));
			coachingRepository.saveAndFlush(coaching);
			throw new ApplicationException(
					"AI_ANALYSIS_TIMEOUT",
					"Coaching analysis timed out.",
					Map.of("coachingId", String.valueOf(coaching.getId()), "status", CoachingStatus.FAILED.name())
			);
		} catch (RuntimeException e) {
			coaching.fail("AI_ANALYSIS_FAILED", e.getMessage(), OffsetDateTime.now(clock));
			coachingRepository.saveAndFlush(coaching);
			throw new ApplicationException(
					"AI_ANALYSIS_FAILED",
					"Coaching analysis failed.",
					Map.of("coachingId", String.valueOf(coaching.getId()), "status", CoachingStatus.FAILED.name())
			);
		}
	}

	private CoachingResult toResponse(Coaching coaching) {
		return new CoachingResult(
				String.valueOf(coaching.getId()),
				coaching.getStatus().name(),
				coaching.getCreatedAt(),
				coaching.getCompletedAt(),
				coaching.getPerformanceIntent(),
				new CoachFeedback(
						new CoachFeedback.SceneIntent(coaching.getResultSceneIntent(), coaching.getResultSceneIntentSource()),
						new CoachFeedback.Strength(
								coaching.getResultStrengthTimecode(),
								coaching.getResultStrengthAxis(),
								coaching.getResultStrengthSignal(),
								coaching.getResultStrengthWhy(),
								coaching.getResultStrengthTier()
						),
						new CoachFeedback.Focus(
								coaching.getResultFocusTimecode(),
								coaching.getResultFocusAxes(),
								coaching.getResultFocusObservedSignal(),
								coaching.getResultFocusRootCause(),
								coaching.getResultFocusIntentGap(),
								coaching.getResultFocusPrescription()
						),
						new CoachFeedback.NextStep(coaching.getResultNextStepText(), coaching.getResultNextStepAction())
				)
		);
	}

	private void validate(VideoInput video, String performanceIntent) {
		if (video == null || video.sizeBytes() == 0 || !ALLOWED_VIDEO_CONTENT_TYPES.contains(video.contentType())) {
			throw new ApplicationException(
					"INVALID_COACHING_REQUEST",
					"Invalid coaching request.",
					Map.of("fields", List.of("video"))
			);
		}
		if (performanceIntent == null || performanceIntent.isBlank()) {
			throw new ApplicationException(
					"INVALID_COACHING_REQUEST",
					"Invalid coaching request.",
					Map.of("fields", List.of("performanceIntent"))
			);
		}
		if (video.sizeBytes() > MAX_VIDEO_SIZE_BYTES) {
			throw new ApplicationException(
					"PAYLOAD_TOO_LARGE",
					"Uploaded video is too large.",
					Map.of("maxSizeBytes", MAX_VIDEO_SIZE_BYTES)
			);
		}
	}
}
