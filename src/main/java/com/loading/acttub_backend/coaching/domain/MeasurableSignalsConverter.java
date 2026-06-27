package com.loading.acttub_backend.coaching.domain;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class MeasurableSignalsConverter implements AttributeConverter<List<MeasurableSignal>, String> {

	private static final ObjectMapper objectMapper = new ObjectMapper();
	private static final TypeReference<List<MeasurableSignal>> typeReference = new TypeReference<>() {
	};

	@Override
	public String convertToDatabaseColumn(List<MeasurableSignal> attribute) {
		if (attribute == null) {
			return "[]";
		}
		try {
			return objectMapper.writeValueAsString(attribute);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("Failed to serialize measurable signals.", exception);
		}
	}

	@Override
	public List<MeasurableSignal> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return new ArrayList<>();
		}
		try {
			return objectMapper.readValue(dbData, typeReference);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("Failed to deserialize measurable signals.", exception);
		}
	}
}
