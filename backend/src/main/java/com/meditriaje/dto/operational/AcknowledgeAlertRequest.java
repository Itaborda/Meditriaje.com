package com.meditriaje.dto.operational;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcknowledgeAlertRequest(
        @JsonProperty("motivo")
        @NotBlank(message = "El motivo de reconocimiento es obligatorio")
        @Size(max = 500, message = "El motivo no puede exceder 500 caracteres")
        String reason
) {
}
