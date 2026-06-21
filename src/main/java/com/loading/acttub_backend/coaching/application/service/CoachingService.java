package com.loading.acttub_backend.coaching.application.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.loading.acttub_backend.coaching.application.model.ApplicationException;
import com.loading.acttub_backend.coaching.application.model.CoachingInput;
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

	private static final long MAX_VIDEO_SIZE_BYTES = 104857600L;
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
	public CoachingResult create(VideoInput video, CoachingInput input) {
		validate(video, input);

		Coaching coaching = createAnalyzingCoaching(video, input);
		storeVideo(coaching, video);
		CoachingAnalysisResult analysisResult = analyze(coaching, video, input);

		coaching.complete(analysisResult, OffsetDateTime.now(clock));
		return toResponse(coaching, analysisResult.feedback());
	}

	private Coaching createAnalyzingCoaching(VideoInput video, CoachingInput input) {
		OffsetDateTime now = OffsetDateTime.now(clock);
		Coaching coaching = Coaching.analyzing(
				input.genreLabel(),
				input.customGenre(),
				input.situation(),
				input.characterSetting(),
				input.subtext(),
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

	private CoachingAnalysisResult analyze(Coaching coaching, VideoInput video, CoachingInput input) {
		try {
			return coachingAnalyzer.analyze(video, input);
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
		return toResponse(coaching, coaching.getFeedback());
	}

	private CoachingResult toResponse(Coaching coaching, CoachFeedback feedback) {
		return new CoachingResult(
				String.valueOf(coaching.getId()),
				coaching.getStatus().name(),
				coaching.getCreatedAt(),
				coaching.getCompletedAt(),
				new CoachingInput(
						coaching.getGenre(),
						coaching.getCustomGenre(),
						coaching.getSituation(),
						coaching.getCharacterSetting(),
						coaching.getSubtext()
				),
				feedback
		);
	}

	private void validate(VideoInput video, CoachingInput input) {
		if (video == null || video.sizeBytes() == 0 || !ALLOWED_VIDEO_CONTENT_TYPES.contains(video.contentType())) {
			throw new ApplicationException(
					"INVALID_COACHING_REQUEST",
					"Invalid coaching request.",
					Map.of("fields", List.of("video"))
			);
		}
		List<String> invalidFields = invalidInputFields(input);
		if (!invalidFields.isEmpty()) {
			throw new ApplicationException(
					"INVALID_COACHING_REQUEST",
					"Invalid coaching request.",
					Map.of("fields", invalidFields)
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

	private List<String> invalidInputFields(CoachingInput input) {
		if (input == null) {
			return List.of("input");
		}

		List<String> invalidFields = new ArrayList<>();
		if (!input.hasAllowedGenre()) {
			invalidFields.add("genre");
		}
		if (input.requiresCustomGenre() && input.customGenre() == null) {
			invalidFields.add("customGenre");
		}
		if (input.situation() == null) {
			invalidFields.add("situation");
		}
		if (input.characterSetting() == null) {
			invalidFields.add("characterSetting");
		}
		return invalidFields;
	}
}
