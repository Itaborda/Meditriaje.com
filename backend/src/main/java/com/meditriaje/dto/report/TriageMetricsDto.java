package com.meditriaje.dto.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Métricas agregadas de triaje clínico y cortes de emergencia (F2.6, RF-30).
 */
public record TriageMetricsDto(
        @JsonProperty("totalTriajes")
        long totalTriages,
        @JsonProperty("nivel1")
        long level1,
        @JsonProperty("nivel2")
        long level2,
        @JsonProperty("nivel3")
        long level3,
        @JsonProperty("nivel4")
        long level4,
        @JsonProperty("nivel5")
        long level5,
        @JsonProperty("emergencias")
        long emergencies,
        @JsonProperty("tasaEmergencia")
        double emergencyRate,
        @JsonProperty("porRutaSugerida")
        List<DistributionItemDto> bySuggestedRoute
) {
    public TriageMetricsDto(
            long totalTriages,
            long level1,
            long level2,
            long level3,
            long level4,
            long level5,
            long emergencies,
            double emergencyRate
    ) {
        this(totalTriages, level1, level2, level3, level4, level5, emergencies, emergencyRate, List.of());
    }
}

