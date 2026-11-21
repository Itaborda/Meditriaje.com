package com.meditriaje.dto.clinical;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Petición para invocar la activación de acceso clínico de emergencia Break-Glass (ADR-017).
 */
public record ActivateBreakGlassRequest(
        @JsonProperty("pacientePublicId")
        @NotBlank(message = "El identificador público del paciente es obligatorio.")
        String patientPublicId,

        @JsonProperty("motivo")
        @NotBlank(message = "El motivo de justificación de emergencia es obligatorio.")
        @Size(min = 20, max = 500, message = "El motivo debe contener entre 20 y 500 caracteres.")
        String reason
) {}
