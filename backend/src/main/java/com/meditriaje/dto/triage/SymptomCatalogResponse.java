package com.meditriaje.dto.triage;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ítem de síntoma activo del catálogo para el formulario de triaje (ADR-009, HU-02).
 */
public record SymptomCatalogResponse(
        String publicId,
        @JsonProperty("codigo")
        String code,
        @JsonProperty("nombre")
        String name,
        @JsonProperty("categoria")
        String category,
        @JsonProperty("esAlarma")
        boolean isAlarm
) {}
