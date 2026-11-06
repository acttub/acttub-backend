package com.loading.acttub_backend.global.api;

import java.util.Map;

public record ApiEnvelope<T>(T data) {

	public static <T> ApiEnvelope<T> data(T data) {
		return new ApiEnvelope<>(data);
	}
}

record ApiErrorEnvelope(ApiError error) {

	static ApiErrorEnvelope error(String code, String message, Map<String, Object> details) {
		return new ApiErrorEnvelope(new ApiError(code, message, details));
	}
}

record ApiError(String code, String message, Map<String, Object> details) {
}
