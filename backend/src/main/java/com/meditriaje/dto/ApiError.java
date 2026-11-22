package com.meditriaje.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Estructura estándar de error de la API.
 * Nunca incluye stack traces ni datos clínicos/personales.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        @JsonProperty("codigo")
        String code,
        @JsonProperty("mensaje")
        String message,
        Instant timestamp,
        String traceId
) {
    public static ApiError of(String code, String message, String traceId) {
        return new ApiError(code, message, Instant.now(), traceId);
    }
}
