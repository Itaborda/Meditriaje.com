package com.meditriaje.dto.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Métricas agregadas de accesos clínicos de emergencia Break-Glass (F2.6, RF-30, ADR-017).
 */
public record BreakGlassMetricsDto(
        @JsonProperty("totalActivaciones")
        long totalActivations,
        @JsonProperty("activasVigentes")
        long activeCurrent,
        @JsonProperty("porEspecialidad")
        List<DistributionItemDto> bySpecialty
) {}
