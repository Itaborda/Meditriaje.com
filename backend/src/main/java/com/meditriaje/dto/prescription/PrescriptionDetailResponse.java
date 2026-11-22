package com.meditriaje.dto.prescription;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO de respuesta para un ítem prescrito en una receta médica.
 * Preserva los datos del snapshot inmutable de medicamento.
 */
public record PrescriptionDetailResponse(
        @JsonProperty("medicamentoPublicId")
        String medicationPublicId,
        @JsonProperty("medicamentoCodigo")
        String medicationCode,
        @JsonProperty("nombreComercial")
        String brandName,
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
        String instructions
) {}
