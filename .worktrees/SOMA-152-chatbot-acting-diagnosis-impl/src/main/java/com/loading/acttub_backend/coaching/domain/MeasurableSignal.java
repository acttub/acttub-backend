package com.loading.acttub_backend.coaching.domain;

import java.math.BigDecimal;

public record MeasurableSignal(String name, BigDecimal value, String unit, String evidence) {
}
