package com.meditriaje.dto.clinical;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Representación inmutable de la autorización de acceso Break-Glass para la API (ADR-017).
 * Cero exposición de identificadores numéricos internos de base de datos.
 */
public record BreakGlassAccessResponse(
        String publicId,
        @JsonProperty("profesionalPublicId")
        String professionalPublicId,
        @JsonProperty("profesionalNombre")
        String professionalName,
        @JsonProperty("pacientePublicId")
        String patientPublicId,
        @JsonProperty("pacienteNombre")
        String patientName,
        @JsonProperty("motivo")
        String reason,
        @JsonProperty("fechaExpiracion")
        Instant expirationDate,
        Instant createdAt,
        @JsonProperty("activo")
        boolean active
) {}
