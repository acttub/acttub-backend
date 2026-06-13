package com.loading.acttub_backend.global.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

	private ResponseEntity<ApiErrorEnvelope> invalidCoachingRequest(String field) {
		return ResponseEntity.badRequest().body(ApiErrorEnvelope.error(
				"INVALID_COACHING_REQUEST",
				"Invalid coaching request.",
				Map.of("fields", List.of(field))
		));
	}
}
