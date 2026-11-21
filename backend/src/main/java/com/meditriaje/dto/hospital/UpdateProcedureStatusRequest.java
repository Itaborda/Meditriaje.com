package com.meditriaje.dto.hospital;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProcedureStatusRequest(
        @JsonProperty("nuevoEstado")
        @NotBlank(message = "nuevoEstado es obligatorio")
        @Pattern(regexp = "PROGRAMADO|EN_CURSO|RECUPERACION|FINALIZADO|CANCELADO", message = "Estado de procedimiento no válido")
        String newStatus,

        @JsonProperty("observaciones")
        @Size(max = 1000, message = "observaciones no debe superar 1000 caracteres")
        String notes
) {
}
