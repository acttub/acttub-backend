package com.loading.acttub_backend.global.api;

import java.util.List;
import java.util.Map;

import com.loading.acttub_backend.coaching.application.model.ApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

	@ExceptionHandler(ApplicationException.class)
	ResponseEntity<ApiErrorEnvelope> handleApplicationException(ApplicationException exception) {
		return ResponseEntity
				.status(statusFor(exception.code()))
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
				Map.of("maxSizeBytes", 104857600L)
		));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiErrorEnvelope> handleUnreadableMessage() {
		return invalidEvaluationRequest("body");
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ApiErrorEnvelope> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
		return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(ApiErrorEnvelope.error(
				"UNSUPPORTED_MEDIA_TYPE",
				"Unsupported media type.",
				Map.of("contentType", String.valueOf(exception.getContentType()))
		));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ApiErrorEnvelope> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
		if ("coachingId".equals(exception.getName())) {
			return ResponseEntity.badRequest().body(ApiErrorEnvelope.error(
					"INVALID_COACHING_ID",
					"Invalid coaching id.",
					Map.of("coachingId", String.valueOf(exception.getValue()))
			));
		}
		return ResponseEntity.badRequest().body(ApiErrorEnvelope.error(
				"INVALID_REQUEST",
				"Invalid request.",
				Map.of("field", exception.getName(), "value", String.valueOf(exception.getValue()))
		));
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

	private HttpStatus statusFor(String code) {
		return switch (code) {
			case "INVALID_COACHING_REQUEST", "INVALID_EVALUATION_REQUEST" -> HttpStatus.BAD_REQUEST;
			case "PAYLOAD_TOO_LARGE" -> HttpStatus.PAYLOAD_TOO_LARGE;
			case "COACHING_NOT_FOUND" -> HttpStatus.NOT_FOUND;
			case "COACHING_NOT_EVALUABLE", "COACHING_EVALUATION_ALREADY_EXISTS" -> HttpStatus.CONFLICT;
			case "AI_ANALYSIS_FAILED" -> HttpStatus.BAD_GATEWAY;
			case "AI_ANALYSIS_TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
			case "VIDEO_STORAGE_FAILED" -> HttpStatus.INTERNAL_SERVER_ERROR;
			default -> HttpStatus.INTERNAL_SERVER_ERROR;
		};
	}
}
