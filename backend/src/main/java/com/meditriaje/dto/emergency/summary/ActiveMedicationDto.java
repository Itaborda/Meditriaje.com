package com.meditriaje.dto.emergency.summary;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Medicamento activo prescrito en receta vigente para el resumen de emergencia (ADR-010, §5.17).
 */
public record ActiveMedicationDto(
        @JsonProperty("nombre")
        String name,
        @JsonProperty("principioActivo")
        String activeIngredient,
        @JsonProperty("presentacion")
        String presentation,
        @JsonProperty("concentracion")
        String concentration,
        @JsonProperty("dosis")
        String dose,
        @JsonProperty("frecuencia")
        String frequency,
        @JsonProperty("duracionDias")
        int durationDays,
        @JsonProperty("cantidad")
        int quantity,
        @JsonProperty("indicaciones")
        String instructions,
        @JsonProperty("fechaPrescripcion")
        Instant prescribedAt,
        @JsonProperty("vigenciaDias")
        int validityDays
) {
}
