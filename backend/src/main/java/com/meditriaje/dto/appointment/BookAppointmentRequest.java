package com.meditriaje.dto.appointment;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Petición para agendar/reservar una cita médica en un slot disponible (ADR-003, ADR-006, HU-04).
 */
public record BookAppointmentRequest(
        @JsonProperty("slotPublicId")
        @NotBlank(message = "El identificador del slot es obligatorio.")
        String slotPublicId,
        @JsonProperty("triajePublicId")
        String triagePublicId
) {
    public BookAppointmentRequest(String slotPublicId) {
        this(slotPublicId, null);
    }
}
