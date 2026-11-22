package com.meditriaje.dto.hospital;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Petición para crear una o varias camas dentro de una habitación o sala hospitalaria (H01).
 */
public record CreateHospitalBedRequest(
        @JsonProperty("codigo")
        @NotBlank(message = "El código o prefijo de la cama es obligatorio.")
        @Size(max = 30, message = "El código de la cama no puede superar 30 caracteres.")
        String code,

        @JsonProperty("cantidad")
        @Min(value = 1, message = "La cantidad mínima de camas es 1.")
        @Max(value = 20, message = "No se pueden crear más de 20 camas en una sola operación.")
        Integer count
) {
}
