package com.meditriaje.dto.triage;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Petición individual de síntoma reportado por el paciente en un triaje (ADR-009, HU-02).
 */
public record SymptomItemRequest(
        @JsonProperty("codigo")
        @NotBlank(message = "El código del síntoma es obligatorio.")
        String code,

        @JsonProperty("duracionHoras")
        @NotNull(message = "La duración es obligatoria.")
        @DecimalMin(value = "0.0", message = "La duración no puede ser negativa.")
        BigDecimal durationHours,

        @JsonProperty("intensidad")
        @NotNull(message = "La intensidad es obligatoria.")
        @Min(value = 0, message = "La intensidad mínima es 0.")
        @Max(value = 10, message = "La intensidad máxima es 10.")
        Integer intensity
) {}
