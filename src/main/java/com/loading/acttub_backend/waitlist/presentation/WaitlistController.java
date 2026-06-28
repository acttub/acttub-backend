package com.loading.acttub_backend.waitlist.presentation;

import java.util.List;
import java.util.Map;

import com.loading.acttub_backend.global.api.ApiEnvelope;
import com.loading.acttub_backend.global.api.ApiException;
import com.loading.acttub_backend.waitlist.application.model.WaitlistCommand;
import com.loading.acttub_backend.waitlist.application.model.WaitlistResult;
import com.loading.acttub_backend.waitlist.application.service.WaitlistService;
import com.loading.acttub_backend.waitlist.presentation.dto.WaitlistRequest;
import com.loading.acttub_backend.waitlist.presentation.dto.WaitlistResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Waitlist", description = "사전 등록 API")
public class WaitlistController {

	private final WaitlistService waitlistService;

	public WaitlistController(WaitlistService waitlistService) {
		this.waitlistService = waitlistService;
	}

	@PostMapping(value = "/api/v1/waitlist", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(
			summary = "사전 등록",
			description = "비공개 테스트 오픈 안내를 받을 이메일과 선택 정보를 등록합니다."
	)
	@ApiResponse(responseCode = "201", description = "사전 등록 완료")
	public ResponseEntity<ApiEnvelope<WaitlistResponse>> register(@RequestBody WaitlistRequest request) {
		if (request == null) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"INVALID_WAITLIST_REQUEST",
					"Invalid waitlist request.",
					Map.of("fields", List.of("body"))
			);
		}
		WaitlistResult result = waitlistService.register(new WaitlistCommand(
				request.email(),
				request.actorStatus(),
				request.helpTopic(),
				request.consent(),
				request.website()
		));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.data(toResponse(result)));
	}

	private WaitlistResponse toResponse(WaitlistResult result) {
		return new WaitlistResponse(
				result.accepted(),
				result.waitlistId(),
				result.email(),
				result.actorStatus(),
				result.helpTopic(),
				result.createdAt()
		);
	}
}
