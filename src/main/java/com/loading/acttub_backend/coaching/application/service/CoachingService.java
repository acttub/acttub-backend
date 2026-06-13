package com.loading.acttub_backend.coaching.application.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.loading.acttub_backend.coaching.application.model.CoachingResult;
import com.loading.acttub_backend.coaching.application.model.StoredVideo;
import com.loading.acttub_backend.coaching.application.port.CoachingAnalyzer;
import com.loading.acttub_backend.coaching.application.port.CoachingRepository;
import com.loading.acttub_backend.coaching.application.port.VideoStorage;
import com.loading.acttub_backend.coaching.domain.CoachFeedback;
import com.loading.acttub_backend.coaching.domain.Coaching;
import com.loading.acttub_backend.coaching.domain.CoachingAnalysisResult;
import com.loading.acttub_backend.coaching.domain.CoachingStatus;
import com.loading.acttub_backend.global.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

	@Transactional(noRollbackFor = ApiException.class)
	public CoachingResult create(MultipartFile video, String performanceIntent) {
		validate(video, performanceIntent);

		Coaching coaching = createAnalyzingCoaching(video, performanceIntent);
		storeVideo(coaching, video);
		CoachingAnalysisResult analysisResult = analyze(coaching, video, performanceIntent);

		coaching.complete(analysisResult, OffsetDateTime.now(clock));
		return toResponse(coaching);
	}

	private Coaching createAnalyzingCoaching(MultipartFile video, String performanceIntent) {
		OffsetDateTime now = OffsetDateTime.now(clock);
		Coaching coaching = Coaching.analyzing(
				performanceIntent,
				video.getOriginalFilename(),
				video.getContentType(),
				video.getSize(),
				now
		);
		return coachingRepository.saveAndFlush(coaching);
	}

	private void storeVideo(Coaching coaching, MultipartFile video) {
		try {
			StoredVideo storedVideo = videoStorage.store(coaching.getId(), video);
			coaching.attachVideo(storedVideo.storageKey(), storedVideo.storageUri(), OffsetDateTime.now(clock));
			coachingRepository.saveAndFlush(coaching);
		} catch (RuntimeException e) {
			coaching.fail("VIDEO_STORAGE_FAILED", e.getMessage(), OffsetDateTime.now(clock));
			coachingRepository.saveAndFlush(coaching);
			throw new ApiException(
					HttpStatus.INTERNAL_SERVER_ERROR,
					"VIDEO_STORAGE_FAILED",
					"Video storage failed.",
					Map.of("coachingId", String.valueOf(coaching.getId()), "status", CoachingStatus.FAILED.name())
			);
		}
	}

	private CoachingAnalysisResult analyze(Coaching coaching, MultipartFile video, String performanceIntent) {
		try {
			return coachingAnalyzer.analyze(video, performanceIntent);
		} catch (RuntimeException e) {
			coaching.fail("AI_ANALYSIS_FAILED", e.getMessage(), OffsetDateTime.now(clock));
			coachingRepository.saveAndFlush(coaching);
			throw new ApiException(
					HttpStatus.BAD_GATEWAY,
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

	private void validate(MultipartFile video, String performanceIntent) {
		if (video == null || video.isEmpty() || !ALLOWED_VIDEO_CONTENT_TYPES.contains(video.getContentType())) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"INVALID_COACHING_REQUEST",
					"Invalid coaching request.",
					Map.of("fields", List.of("video"))
			);
		}
		if (performanceIntent == null || performanceIntent.isBlank()) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"INVALID_COACHING_REQUEST",
					"Invalid coaching request.",
					Map.of("fields", List.of("performanceIntent"))
			);
		}
		if (video.getSize() > MAX_VIDEO_SIZE_BYTES) {
			throw new ApiException(
					HttpStatus.PAYLOAD_TOO_LARGE,
					"PAYLOAD_TOO_LARGE",
					"Uploaded video is too large.",
					Map.of("maxSizeBytes", MAX_VIDEO_SIZE_BYTES)
			);
		}
	}
}
