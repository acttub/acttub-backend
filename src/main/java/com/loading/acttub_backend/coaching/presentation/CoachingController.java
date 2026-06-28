package com.loading.acttub_backend.coaching.presentation;

import java.util.List;
import java.util.Map;

import com.loading.acttub_backend.coaching.application.model.CoachingResult;
import com.loading.acttub_backend.coaching.application.model.CoachingInput;
import com.loading.acttub_backend.coaching.application.model.MessageCommand;
import com.loading.acttub_backend.coaching.application.model.EvaluationCommand;
import com.loading.acttub_backend.coaching.application.model.EvaluationResult;
import com.loading.acttub_backend.coaching.application.model.VideoInput;
import com.loading.acttub_backend.coaching.application.service.CoachingService;
import com.loading.acttub_backend.coaching.application.service.EvaluationService;
import com.loading.acttub_backend.coaching.domain.CoachingInitialContext;
import com.loading.acttub_backend.coaching.domain.MeasurableSignal;
import com.loading.acttub_backend.coaching.presentation.dto.CoachingResponse;
import com.loading.acttub_backend.coaching.presentation.dto.EvaluationRequest;
import com.loading.acttub_backend.coaching.presentation.dto.EvaluationResponse;
import com.loading.acttub_backend.coaching.presentation.dto.MessageRequest;
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
			description = "연기 영상과 장르, 상황, 인물 설정, 선택 서브텍스트를 전달해 코칭 피드백 카드를 생성합니다."
	)
	@ApiResponse(responseCode = "201", description = "코칭 피드백 생성 완료")
	public ResponseEntity<ApiEnvelope<CoachingResponse>> create(
			@Parameter(description = "업로드할 연기 영상 파일. 허용 MIME type: video/mp4, video/quicktime, video/webm")
			@RequestParam("video") MultipartFile video,
			@Parameter(description = "장르. 연극, 영화, 뮤지컬, 드라마, 기타 중 하나")
			@RequestParam("genre") String genre,
			@Parameter(description = "장르가 기타일 때 입력하는 사용자 지정 장르")
			@RequestParam(value = "customGenre", required = false) String customGenre,
			@Parameter(description = "장면 상황")
			@RequestParam("situation") String situation,
			@Parameter(description = "인물 설정")
			@RequestParam("characterSetting") String characterSetting,
			@Parameter(description = "서브텍스트. 생략 가능")
			@RequestParam(value = "subtext", required = false) String subtext
	) {
		CoachingInput input = new CoachingInput(genre, customGenre, situation, characterSetting, subtext);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.data(toResponse(coachingService.create(toVideoInput(video), input))));
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

	@PostMapping(value = "/api/v1/coachings/{coachingId}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(
			summary = "코칭 챗봇 답변 등록",
			description = "현재 AI 질문에 대한 사용자 답변을 등록하고 다음 질문 또는 진단 준비 상태를 반환합니다."
	)
	@ApiResponse(responseCode = "201", description = "코칭 챗봇 메시지 처리 완료")
	public ResponseEntity<ApiEnvelope<CoachingResponse>> message(
			@Parameter(description = "코칭 ID")
			@PathVariable Long coachingId,
			@RequestBody MessageRequest request
	) {
		if (request == null) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"INVALID_MESSAGE_REQUEST",
					"Invalid message request.",
					Map.of("fields", List.of("answer"))
			);
		}
		CoachingResult result = coachingService.message(coachingId, new MessageCommand(request.answer()));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.data(toResponse(result)));
	}

	@PostMapping(value = "/api/v1/coachings/{coachingId}/diagnosis")
	@Operation(
			summary = "코칭 최종 진단 생성",
			description = "저장된 초기 컨텍스트와 챗봇 대화 기록을 바탕으로 최종 진단을 생성합니다."
	)
	@ApiResponse(responseCode = "201", description = "코칭 진단 완료")
	public ResponseEntity<ApiEnvelope<CoachingResponse>> diagnose(
			@Parameter(description = "코칭 ID")
			@PathVariable Long coachingId
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.data(toResponse(coachingService.diagnose(coachingId))));
	}

	private CoachingResponse toResponse(CoachingResult result) {
		return new CoachingResponse(
				result.coachingId(),
				result.status(),
				result.createdAt(),
				result.completedAt(),
				new CoachingResponse.CoachingInput(
						result.input().genreLabel(),
						result.input().customGenre(),
						result.input().situation(),
						result.input().characterSetting(),
						result.input().subtext()
				),
				toDialogue(result.nextQuestion()),
				toConversation(result),
				toInitialContext(result.initialContext()),
				toRefinedContext(result),
				result.feedback()
		);
	}

	private CoachingResponse.Dialogue toDialogue(String nextQuestion) {
		if (nextQuestion != null) {
			return new CoachingResponse.Dialogue(nextQuestion);
		}
		return null;
	}

	private CoachingResponse.Conversation toConversation(CoachingResult result) {
		if (result.conversation() == null) {
			return null;
		}
		return new CoachingResponse.Conversation(
				result.conversation().turnIndex(),
				result.conversation().minTurns(),
				result.conversation().maxTurns(),
				result.conversation().diagnosisReady(),
				result.conversation().reason(),
				result.conversation().summary()
		);
	}

	private CoachingResponse.InitialContext toInitialContext(CoachingInitialContext initialContext) {
		if (initialContext == null) {
			return null;
		}
		return new CoachingResponse.InitialContext(
				initialContext.transcript(),
				initialContext.analysis(),
				toMeasurableSignals(initialContext.measurableSignals()),
				initialContext.diagnosticHypothesis()
		);
	}

	private List<CoachingResponse.MeasurableSignalResponse> toMeasurableSignals(List<MeasurableSignal> measurableSignals) {
		if (measurableSignals == null) {
			return List.of();
		}
		return measurableSignals.stream()
				.map(signal -> new CoachingResponse.MeasurableSignalResponse(
						signal.name(),
						signal.value(),
						signal.unit(),
						signal.evidence()
				))
				.toList();
	}

	private CoachingResponse.RefinedContext toRefinedContext(CoachingResult result) {
		if (result.refinedSituation() == null && result.refinedIntent() == null) {
			return null;
		}
		return new CoachingResponse.RefinedContext(result.refinedSituation(), result.refinedIntent());
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
