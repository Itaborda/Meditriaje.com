package com.meditriaje.dto.triage;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Petición para evaluar y registrar un nuevo triaje clínico (ADR-009, HU-02).
 */
public record CreateTriageRequest(
        @JsonProperty("sintomas")
        @NotEmpty(message = "Debe reportar al menos un síntoma.")
        @Size(max = 20, message = "No se admiten más de 20 síntomas.")
        List<@Valid SymptomItemRequest> symptoms,

        @JsonProperty("observaciones")
        @Size(max = 500, message = "Las observaciones no pueden superar los 500 caracteres.")
        String notes
) {
    public CreateTriageRequest(List<SymptomItemRequest> symptoms) {
        this(symptoms, null);
    }
}
