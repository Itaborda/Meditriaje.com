package com.meditriaje.dto.report;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Métricas agregadas de recetas y dispensaciones farmacéuticas (F2.6, RF-30).
 */
public record PharmacyMetricsDto(
        @JsonProperty("totalRecetas")
        long totalPrescriptions,
        @JsonProperty("pendientes")
        long pending,
        @JsonProperty("dispensadasParcial")
        long partiallyDispensed,
        @JsonProperty("dispensadasTotal")
        long fullyDispensed,
        @JsonProperty("totalUnidadesDispensadas")
        long totalDispensedUnits
) {}
