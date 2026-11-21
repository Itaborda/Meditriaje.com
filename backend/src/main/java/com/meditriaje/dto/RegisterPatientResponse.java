package com.meditriaje.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta del registro de paciente.
 * No expone IDs internos, contraseñas ni datos sensibles (ADR-003).
 */
public record RegisterPatientResponse(
        @JsonProperty("pacientePublicId")
        String patientPublicId,
        String email,
        @JsonProperty("nombres")
        String firstNames,
        @JsonProperty("apellidos")
        String lastNames,
        @JsonProperty("mensaje")
        String message
) {}
