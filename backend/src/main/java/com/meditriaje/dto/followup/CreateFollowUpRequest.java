package com.meditriaje.dto.followup;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Solicitud médica para prescribir una tarea de seguimiento post-atención (ADR-015).
 */
public record CreateFollowUpRequest(
        @JsonProperty("tipo")
        @NotBlank(message = "El tipo de seguimiento es obligatorio.")
        String type,

        @JsonProperty("indicaciones")
        @NotBlank(message = "Las indicaciones de seguimiento son obligatorias.")
        @Size(max = 1000, message = "Las indicaciones no pueden exceder 1000 caracteres.")
        String instructions,

        @JsonProperty("fechaSugeridaControl")
        LocalDate suggestedControlDate
) {
}
