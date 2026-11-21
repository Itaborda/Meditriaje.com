package com.meditriaje.dto.triage;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * Detalle consolidado de un síntoma reportado en un triaje (ADR-009, HU-02).
 */
public record SymptomItemResponse(
        @JsonProperty("codigo")
        String code,
        @JsonProperty("nombre")
        String name,
        @JsonProperty("duracionHoras")
        BigDecimal durationHours,
        @JsonProperty("intensidad")
        int intensity,
        @JsonProperty("esAlarma")
        boolean isAlarm
) {}
