package com.meditriaje.dto.emergency;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meditriaje.dto.triage.SymptomItemResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
        Instant createdAt,
        @JsonProperty("sintomas")
        List<SymptomItemResponse> symptoms
) {
    public TriageAssessmentResponse {
        symptoms = symptoms == null ? List.of() : List.copyOf(symptoms);
    }

    public TriageAssessmentResponse(
            String publicId,
            String episodePublicId,
            Integer version,
            String level,
            String consultationReason,
            String clinicalFindings,
            String bloodPressure,
            Integer heartRate,
            Integer respiratoryRate,
            Integer oxygenSaturation,
            BigDecimal temperature,
            Integer glasgowScale,
            String evaluatorName,
            boolean isReevaluation,
            String reevaluationReason,
            Instant createdAt
    ) {
        this(
                publicId,
                episodePublicId,
                version,
                level,
                consultationReason,
                clinicalFindings,
                bloodPressure,
                heartRate,
                respiratoryRate,
                oxygenSaturation,
                temperature,
                glasgowScale,
                evaluatorName,
                isReevaluation,
                reevaluationReason,
                createdAt,
                List.of()
        );
    }
}
