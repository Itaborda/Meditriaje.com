package com.meditriaje.dto.appointment;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

/**
 * Petición para solicitar la cancelación de una cita médica agendada (ADR-006, HU-05).
 */
public record CancelAppointmentRequest(
        @JsonProperty("motivo")
        @Size(max = 255, message = "El motivo de cancelacion no puede exceder 255 caracteres.")
        String reason
) {
    public CancelAppointmentRequest() {
        this(null);
    }
}
