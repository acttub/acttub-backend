package com.loading.acttub_backend.waitlist.application.model;

import java.util.Map;

public class WaitlistException extends RuntimeException {

	private final String code;
	private final Map<String, Object> details;

	public WaitlistException(String code, String message, Map<String, Object> details) {
		super(message);
		this.code = code;
		this.details = details;
	}

	public String code() {
		return code;
	}

	public Map<String, Object> details() {
		return details;
	}
}
