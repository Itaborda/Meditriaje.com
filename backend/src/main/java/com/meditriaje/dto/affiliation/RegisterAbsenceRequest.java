package com.meditriaje.dto.affiliation;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record RegisterAbsenceRequest(
        @JsonProperty("profesionalPublicId")
        @NotBlank(message = "profesionalPublicId es obligatorio")
        String professionalPublicId,

        @JsonProperty("fechaInicio")
        @NotNull(message = "fechaInicio es obligatoria")
        Instant startDate,

        @JsonProperty("fechaFin")
        @NotNull(message = "fechaFin es obligatoria")
        Instant endDate,

        @JsonProperty("motivo")
        @NotBlank(message = "motivo es obligatorio")
        @Size(max = 500, message = "motivo no debe superar 500 caracteres")
        String reason
) {
}
