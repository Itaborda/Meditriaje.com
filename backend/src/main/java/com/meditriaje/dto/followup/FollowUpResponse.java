package com.meditriaje.dto.followup;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Representación pública inmutable de un seguimiento post-atención (ADR-003, ADR-015).
 * Expone exclusivamente UUIDs públicos sin filtrar IDs autonuméricos internos de la base de datos.
 */
public record FollowUpResponse(
        String publicId,
        @JsonProperty("atencionPublicId")
        String attentionPublicId,
        @JsonProperty("pacientePublicId")
        String patientPublicId,
        @JsonProperty("pacienteNombre")
        String patientName,
        @JsonProperty("profesionalPublicId")
        String professionalPublicId,
        @JsonProperty("profesionalNombre")
        String professionalName,
        @JsonProperty("profesionalEspecialidad")
        String professionalSpecialty,
        @JsonProperty("tipo")
        String type,
        @JsonProperty("indicaciones")
        String instructions,
        @JsonProperty("fechaSugeridaControl")
        LocalDate suggestedControlDate,
        @JsonProperty("estado")
        String status,
        @JsonProperty("fechaRespuestaPaciente")
        Instant patientRespondedAt,
        @JsonProperty("reportePaciente")
        String patientReport,
        Instant createdAt,
        Instant updatedAt
) {
}
