package com.meditriaje.dto.emergency;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Respuesta con el detalle inmutable de una valoración clínica de triaje (Fase U, ADR-027).
 */
public record TriageAssessmentResponse(
        String publicId,
        @JsonProperty("episodioPublicId")
        String episodePublicId,
        Integer version,
        @JsonProperty("nivel")
        String level,
        @JsonProperty("motivoConsulta")
        String consultationReason,
        @JsonProperty("hallazgosClinicos")
        String clinicalFindings,
        @JsonProperty("presionArterial")
        String bloodPressure,
        @JsonProperty("frecuenciaCardiaca")
        Integer heartRate,
        @JsonProperty("frecuenciaRespiratoria")
        Integer respiratoryRate,
        @JsonProperty("saturacionOxigeno")
        Integer oxygenSaturation,
        @JsonProperty("temperatura")
        BigDecimal temperature,
        @JsonProperty("escalaGlasgow")
        Integer glasgowScale,
        @JsonProperty("evaluadorNombre")
        String evaluatorName,
        @JsonProperty("esReevaluacion")
        boolean isReevaluation,
        @JsonProperty("motivoReevaluacion")
        String reevaluationReason,
        @JsonProperty("creadoAt")
        Instant createdAt
) {
}
