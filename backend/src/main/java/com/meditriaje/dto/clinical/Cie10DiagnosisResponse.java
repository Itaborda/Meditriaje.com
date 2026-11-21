package com.meditriaje.dto.clinical;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO de respuesta para catálogo de diagnósticos CIE-10 (ADR-013).
 */
public record Cie10DiagnosisResponse(
        @JsonProperty("codigo")
        String code,
        @JsonProperty("descripcion")
        String description
) {
}
