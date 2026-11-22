package com.meditriaje.dto.pharmacy;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Representación del saldo y estado de entrega de un medicamento prescrito en una receta médica (F2.4, ADR-016).
 */
public record MedicationBalanceDto(
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
        @JsonProperty("cantidadPrescrita")
        int prescribedQuantity,
        @JsonProperty("cantidadDispensada")
        int dispensedQuantity,
        @JsonProperty("saldoPendiente")
        int pendingBalance,
        @JsonProperty("estado")
        String status,
        @JsonProperty("indicaciones")
        String instructions
) {}
