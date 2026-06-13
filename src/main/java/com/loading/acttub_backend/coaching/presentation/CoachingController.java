package com.loading.acttub_backend.coaching.presentation;

import java.util.List;
import java.util.Map;

import com.loading.acttub_backend.coaching.application.model.CoachingResult;
import com.loading.acttub_backend.coaching.application.model.EvaluationCommand;
import com.loading.acttub_backend.coaching.application.model.EvaluationResult;
import com.loading.acttub_backend.coaching.application.model.VideoInput;
import com.loading.acttub_backend.coaching.application.service.CoachingService;
import com.loading.acttub_backend.coaching.application.service.EvaluationService;
import com.loading.acttub_backend.coaching.presentation.dto.CoachingResponse;
import com.loading.acttub_backend.coaching.presentation.dto.EvaluationRequest;
import com.loading.acttub_backend.coaching.presentation.dto.EvaluationResponse;
import com.loading.acttub_backend.global.api.ApiEnvelope;
import com.loading.acttub_backend.global.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class CoachingController {

	private final CoachingService coachingService;
	private final EvaluationService evaluationService;

	public CoachingController(CoachingService coachingService, EvaluationService evaluationService) {
		this.coachingService = coachingService;
		this.evaluationService = evaluationService;
	}

	@PostMapping("/api/v1/coachings")
	public ResponseEntity<ApiEnvelope<CoachingResponse>> create(
			@RequestParam("video") MultipartFile video,
			@RequestParam("performanceIntent") String performanceIntent
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.data(toResponse(coachingService.create(toVideoInput(video), performanceIntent))));
	}

	@PostMapping("/api/v1/coachings/{coachingId}/evaluation")
	public ResponseEntity<ApiEnvelope<EvaluationResponse>> evaluate(
			@PathVariable Long coachingId,
			@RequestBody EvaluationRequest request
	) {
		if (request == null) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"INVALID_EVALUATION_REQUEST",
					"Invalid evaluation request.",
					Map.of("fields", List.of("body"))
			);
		}
		EvaluationResult result = evaluationService.evaluate(coachingId, new EvaluationCommand(request.rating(), request.comment()));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.data(toResponse(result)));
	}

	private CoachingResponse toResponse(CoachingResult result) {
		return new CoachingResponse(
				result.coachingId(),
				result.status(),
				result.createdAt(),
				result.completedAt(),
				new CoachingResponse.CoachingInput(result.performanceIntent()),
				result.feedback()
		);
	}

	private VideoInput toVideoInput(MultipartFile video) {
		return new VideoInput(
				video.getOriginalFilename(),
				video.getContentType(),
				video.getSize(),
				video::getInputStream
		);
	}

	private EvaluationResponse toResponse(EvaluationResult result) {
		return new EvaluationResponse(
				result.evaluationId(),
				result.coachingId(),
				result.rating(),
				result.comment(),
				result.createdAt()
		);
	}
}
