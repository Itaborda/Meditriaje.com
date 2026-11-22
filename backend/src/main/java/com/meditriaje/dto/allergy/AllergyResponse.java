package com.meditriaje.dto.allergy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Respuesta DTO para alergia clínica o autorreportada (D3, T4).
 * Cero exposición de identificadores numéricos de base de datos.
 */
public record AllergyResponse(
        String publicId,
        @JsonProperty("pacientePublicId")
        String patientPublicId,
        @JsonProperty("sustancia")
        String substance,
        @JsonProperty("reaccion")
        String reaction,
        @JsonProperty("severidad")
        String severity,
        @JsonProperty("estado")
        String status,
        @JsonProperty("origen")
        String origin,
        @JsonProperty("autorreportada")
        boolean selfReported,
        @JsonProperty("atencionPublicId")
        String attentionPublicId,
        Instant createdAt,
        @JsonProperty("fechaInactivacion")
        Instant inactivatedAt,
        @JsonProperty("motivoInactivacion")
        String inactivationReason
) {
}
