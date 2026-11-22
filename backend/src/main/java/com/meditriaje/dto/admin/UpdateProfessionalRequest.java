package com.meditriaje.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Solicitud administrativa para actualizar los datos de un profesional asistencial (HU-10).
 */
public record UpdateProfessionalRequest(
        @JsonProperty("nombres")
        @NotBlank(message = "Los nombres son obligatorios.")
        @Size(max = 60, message = "Los nombres no pueden superar los 60 caracteres.")
        String firstNames,

        @JsonProperty("apellidos")
        @NotBlank(message = "Los apellidos son obligatorios.")
        @Size(max = 60, message = "Los apellidos no pueden superar los 60 caracteres.")
        String lastNames,

        @JsonProperty("especialidadPublicId")
        @NotBlank(message = "La especialidad es obligatoria.")
        String specialtyPublicId,

        @JsonProperty("telefono")
        String phone
) {
    public UpdateProfessionalRequest(
            String firstNames,
            String lastNames,
            String specialtyPublicId
    ) {
        this(firstNames, lastNames, specialtyPublicId, null);
    }
}
