package com.meditriaje.dto.triage;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * Respuesta consolidada tras la evaluación o consulta de un triaje clínico (ADR-009, HU-02).
 */
public record TriageResponse(
        String publicId,
        @JsonProperty("pacientePublicId")
        String patientPublicId,
        @JsonProperty("nivelPrioridad")
        String priorityLevel,
        @JsonProperty("rutaSugerida")
        String suggestedRoute,
        @JsonProperty("esEmergencia")
        boolean isEmergency,
        @JsonProperty("versionReglas")
        String rulesVersion,
        @JsonProperty("mensaje")
        String message,
        @JsonProperty("aviso")
        String notice,
        @JsonProperty("sintomasAlarma")
        List<String> alarmSymptoms,
        @JsonProperty("sintomas")
        List<SymptomItemResponse> symptoms,
        @JsonProperty("observaciones")
        String notes,
        Instant createdAt
) {}
