package com.meditriaje.dto.emergency.summary;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Antecedente o atención médica reciente relevante para el resumen de emergencia (ADR-010, §5.17).
 */
public record AttentionSummaryDto(
        @JsonProperty("fechaAtencion")
        Instant attentionDate,
        @JsonProperty("especialidad")
        String specialty,
        @JsonProperty("diagnosticoCodigo")
        String diagnosisCode,
        @JsonProperty("diagnosticoDescripcion")
        String diagnosisDescription,
        @JsonProperty("motivoConsulta")
        String consultationReason,
        @JsonProperty("indicaciones")
        String instructions
) {
}
