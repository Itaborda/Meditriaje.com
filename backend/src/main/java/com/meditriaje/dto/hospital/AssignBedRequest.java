package com.meditriaje.dto.hospital;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssignBedRequest(
        @JsonProperty("camaPublicId")
        @NotBlank(message = "camaPublicId es obligatorio")
        String bedPublicId,

        @JsonProperty("motivoAsignacion")
        @Size(max = 500, message = "motivoAsignacion no debe superar 500 caracteres")
        String assignmentReason
) {
}
