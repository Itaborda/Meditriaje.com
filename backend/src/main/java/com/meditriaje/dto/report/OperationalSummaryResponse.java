package com.meditriaje.dto.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Resumen consolidado de indicadores y métricas operativas hospitalarias (F2.6, RF-30, ADR-018).
 * Cero datos clínicos personales ni identificables de pacientes (ADR-007).
 */
public record OperationalSummaryResponse(
        @JsonProperty("fechaDesde")
        Instant dateFrom,
        @JsonProperty("fechaHasta")
        Instant dateTo,
        @JsonProperty("citas")
        AppointmentMetricsDto appointments,
        @JsonProperty("triaje")
        TriageMetricsDto triage,
        @JsonProperty("farmacia")
        PharmacyMetricsDto pharmacy,
        BreakGlassMetricsDto breakGlass,
        @JsonProperty("generadoAt")
        Instant generatedAt
) {}
