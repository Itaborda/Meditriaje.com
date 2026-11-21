package com.meditriaje.dto.clinical;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Solicitud para iniciar el acto asistencial de una cita médica.
 */
public record StartAttentionRequest(
        @JsonProperty("citaPublicId")
        @NotBlank(message = "El identificador de la cita es obligatorio.")
        String appointmentPublicId
) {
}
