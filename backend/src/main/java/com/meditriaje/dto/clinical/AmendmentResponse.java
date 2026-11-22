package com.meditriaje.dto.clinical;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * DTO de respuesta para una enmienda médica append-only (ADR-008, HU-07, HU-09).
 */
public record AmendmentResponse(
        @JsonProperty("profesionalPublicId")
        String professionalPublicId,
        @JsonProperty("profesionalNombre")
        String professionalName,
        @JsonProperty("motivo")
        String reason,
        @JsonProperty("contenido")
        String content,
        @JsonProperty("fechaEnmienda")
        Instant amendedAt
) {
}
