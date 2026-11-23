package com.meditriaje.dto.hospital;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Petición para crear una habitación o sala dentro de un área hospitalaria (H01).
 */
public record CreateHospitalRoomRequest(
        @JsonProperty("codigo")
        @NotBlank(message = "El código de la habitación o sala es obligatorio.")
        @Size(max = 30, message = "El código de la habitación no puede superar 30 caracteres.")
        String code,

        @JsonProperty("tipo")
        @NotBlank(message = "El tipo de habitación o sala es obligatorio.")
        String type
) {
}
