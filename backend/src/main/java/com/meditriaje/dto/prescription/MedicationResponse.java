package com.meditriaje.dto.prescription;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO de respuesta para un ítem del catálogo maestro de medicamentos.
 */
public record MedicationResponse(
        String publicId,
        @JsonProperty("codigo")
        String code,
        @JsonProperty("nombreComercial")
        String brandName,
        @JsonProperty("principioActivo")
        String activeIngredient,
        @JsonProperty("presentacion")
        String presentation,
        @JsonProperty("concentracion")
        String concentration,
        @JsonProperty("estado")
        String status
) {}
