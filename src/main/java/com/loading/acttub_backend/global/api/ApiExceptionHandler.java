package com.loading.acttub_backend.global.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiErrorEnvelope> handleApiException(ApiException exception) {
		return ResponseEntity
				.status(exception.status())
				.body(ApiErrorEnvelope.error(exception.code(), exception.getMessage(), exception.details()));
	}

	@ExceptionHandler(MissingServletRequestPartException.class)
	ResponseEntity<ApiErrorEnvelope> handleMissingPart(MissingServletRequestPartException exception) {
		return invalidCoachingRequest(exception.getRequestPartName());
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<ApiErrorEnvelope> handleMissingParameter(MissingServletRequestParameterException exception) {
		return invalidCoachingRequest(exception.getParameterName());
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ApiErrorEnvelope> handleMaxUploadSizeExceeded() {
		return ResponseEntity.status(413).body(ApiErrorEnvelope.error(
				"PAYLOAD_TOO_LARGE",
				"Uploaded video is too large.",
				Map.of("maxSizeBytes", 314572800L)
		));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiErrorEnvelope> handleUnreadableMessage() {
		return invalidEvaluationRequest("body");
	}

	private ResponseEntity<ApiErrorEnvelope> invalidCoachingRequest(String field) {
		return ResponseEntity.badRequest().body(ApiErrorEnvelope.error(
				"INVALID_COACHING_REQUEST",
				"Invalid coaching request.",
				Map.of("fields", List.of(field))
		));
	}

	private ResponseEntity<ApiErrorEnvelope> invalidEvaluationRequest(String field) {
		return ResponseEntity.badRequest().body(ApiErrorEnvelope.error(
				"INVALID_EVALUATION_REQUEST",
				"Invalid evaluation request.",
				Map.of("fields", List.of(field))
		));
	}
}
