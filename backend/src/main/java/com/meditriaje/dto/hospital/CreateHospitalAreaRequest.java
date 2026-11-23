package com.meditriaje.dto.hospital;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Petición para crear un área o pabellón hospitalario (H01).
 */
public record CreateHospitalAreaRequest(
        @JsonProperty("codigo")
        @NotBlank(message = "El código del área es obligatorio.")
        @Size(max = 30, message = "El código del área no puede superar 30 caracteres.")
        String code,

        @JsonProperty("nombre")
        @NotBlank(message = "El nombre del área es obligatorio.")
        @Size(max = 100, message = "El nombre del área no puede superar 100 caracteres.")
        String name,

        @JsonProperty("tipo")
        @NotBlank(message = "El tipo de área es obligatorio.")
        String type,

        @JsonProperty("piso")
        @Size(max = 20, message = "El piso no puede superar 20 caracteres.")
        String floor
) {
}
