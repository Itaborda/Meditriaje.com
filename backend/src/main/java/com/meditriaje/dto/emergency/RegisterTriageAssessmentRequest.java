package com.meditriaje.dto.emergency;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Petición para registrar una valoración de triaje presencial o reevaluación clínica (Fase U, ADR-027, U04).
 */
public record RegisterTriageAssessmentRequest(
        @JsonProperty("nivel")
        @NotBlank(message = "El nivel de triaje es obligatorio (I, II, III, IV, V o PENDIENTE_VALORACION).")
        String level,

        @JsonProperty("motivoConsulta")
        @NotBlank(message = "El motivo de consulta clínica es obligatorio.")
        @Size(max = 500, message = "El motivo de consulta no puede exceder 500 caracteres.")
        String consultationReason,

        @JsonProperty("hallazgosClinicos")
        @Size(max = 1000, message = "Los hallazgos clínicos no pueden exceder 1000 caracteres.")
        String clinicalFindings,

        @JsonProperty("presionArterial")
        @Size(max = 20, message = "La presión arterial no puede exceder 20 caracteres.")
        String bloodPressure,

        @JsonProperty("frecuenciaCardiaca")
        @Min(value = 20, message = "Frecuencia cardíaca fuera de rango biológico.")
        @Max(value = 300, message = "Frecuencia cardíaca fuera de rango biológico.")
        Integer heartRate,

        @JsonProperty("frecuenciaRespiratoria")
        @Min(value = 4, message = "Frecuencia respiratoria fuera de rango biológico.")
        @Max(value = 80, message = "Frecuencia respiratoria fuera de rango biológico.")
        Integer respiratoryRate,

        @JsonProperty("saturacionOxigeno")
        @Min(value = 30, message = "Saturación de oxígeno fuera de rango biológico.")
        @Max(value = 100, message = "Saturación de oxígeno máxima es 100%.")
        Integer oxygenSaturation,

        @JsonProperty("temperatura")
        @DecimalMin(value = "25.0", message = "Temperatura corporal fuera de rango biológico.")
        @DecimalMax(value = "45.0", message = "Temperatura corporal fuera de rango biológico.")
        BigDecimal temperature,

        @JsonProperty("escalaGlasgow")
        @Min(value = 3, message = "Escala de Glasgow mínima es 3.")
        @Max(value = 15, message = "Escala de Glasgow máxima es 15.")
        Integer glasgowScale,

        @JsonProperty("esReevaluacion")
        boolean isReevaluation,

        @JsonProperty("motivoReevaluacion")
        @Size(max = 500, message = "El motivo de reevaluación no puede exceder 500 caracteres.")
        String reevaluationReason
) {
}
