package com.loading.acttub_backend.global.api;

import java.util.Map;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	private final String message;
	private final Map<String, Object> details;

	public ApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
		super(message);
		this.status = status;
		this.code = code;
		this.message = message;
		this.details = details;
	}

	HttpStatus status() {
		return status;
	}

	String code() {
		return code;
	}

	@Override
	public String getMessage() {
		return message;
	}

	Map<String, Object> details() {
		return details;
	}
}
