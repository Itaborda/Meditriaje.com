package com.meditriaje.dto.emergency.summary;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * Respuesta consolidada del resumen clínico de salud para emergencias (ADR-010, §5.17, §5.18).
 */
public record HealthSummaryResponse(
        @JsonProperty("paciente")
        EmergencyPatientDto patient,
        @JsonProperty("alergias")
        List<EmergencyAllergyDto> allergies,
        @JsonProperty("medicamentosActivos")
        List<ActiveMedicationDto> activeMedications,
        @JsonProperty("atencionesRecientes")
        List<AttentionSummaryDto> recentAttentions,
        @JsonProperty("generadoAt")
        Instant generatedAt,
        @JsonProperty("advertenciaLegal")
        String legalWarning
) {
}
