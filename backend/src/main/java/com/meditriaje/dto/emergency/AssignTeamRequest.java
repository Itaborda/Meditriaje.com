package com.meditriaje.dto.emergency;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Petición para asignar un profesional de la salud a un episodio de urgencias (Fase U, ADR-022, U06).
 */
public record AssignTeamRequest(
        @JsonProperty("profesionalPublicId")
        @NotBlank(message = "El identificador público del profesional es obligatorio.")
        String professionalPublicId,

        @JsonProperty("funcion")
        @NotBlank(message = "La función asistencial es obligatoria (MEDICO_TRATANTE, ENFERMERO_CARGO, INTERCONSULTOR).")
        String roleFunction
) {
}
