package com.meditriaje.dto.emergency;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meditriaje.dto.triage.SymptomItemRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Petición de solo cálculo para previsualizar el nivel de prioridad de triaje a partir de los síntomas.
 */
public record CalculateTriageLevelRequest(
        @JsonProperty("sintomas")
        @NotEmpty(message = "Debe reportar al menos un síntoma para calcular el nivel de prioridad.")
        @Size(max = 20, message = "No se admiten más de 20 síntomas.")
        List<@Valid SymptomItemRequest> symptoms
) {
}
