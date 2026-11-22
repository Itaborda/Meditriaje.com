package com.meditriaje.dto.emergency;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Respuesta con el cálculo automático del nivel de prioridad de triaje basado en síntomas.
 */
public record TriageCalculationResponse(
        @JsonProperty("nivel")
        String level,
        @JsonProperty("rutaSugerida")
        String suggestedRoute,
        @JsonProperty("esEmergencia")
        boolean isEmergency,
        @JsonProperty("versionReglas")
        String rulesVersion,
        @JsonProperty("sintomasAlarma")
        List<String> alarmSymptoms,
        @JsonProperty("mensaje")
        String message
) {
    public TriageCalculationResponse {
        alarmSymptoms = alarmSymptoms == null ? List.of() : List.copyOf(alarmSymptoms);
    }
}
