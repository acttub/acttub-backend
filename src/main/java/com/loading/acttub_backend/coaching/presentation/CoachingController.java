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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "Coachings", description = "코칭 분석 및 평가 API")
public class CoachingController {

	private final CoachingService coachingService;
	private final EvaluationService evaluationService;

	public CoachingController(CoachingService coachingService, EvaluationService evaluationService) {
		this.coachingService = coachingService;
		this.evaluationService = evaluationService;
	}

	@PostMapping(value = "/api/v1/coachings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(
			summary = "코칭 피드백 생성",
			description = "연기 영상을 업로드하고 연기 의도를 함께 전달해 코칭 분석 결과를 생성합니다."
	)
	@ApiResponse(responseCode = "201", description = "코칭 피드백 생성 완료")
	public ResponseEntity<ApiEnvelope<CoachingResponse>> create(
			@Parameter(description = "업로드할 연기 영상 파일")
			@RequestParam("video") MultipartFile video,
			@Parameter(description = "사용자가 의도한 연기 방향")
			@RequestParam("performanceIntent") String performanceIntent
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.data(toResponse(coachingService.create(toVideoInput(video), performanceIntent))));
	}

	@PostMapping(value = "/api/v1/coachings/{coachingId}/evaluation", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(
			summary = "코칭 평가 등록",
			description = "완료된 코칭 결과에 대한 사용자 평가를 등록합니다."
	)
	@ApiResponse(responseCode = "201", description = "코칭 평가 등록 완료")
	public ResponseEntity<ApiEnvelope<EvaluationResponse>> evaluate(
			@Parameter(description = "코칭 ID")
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
