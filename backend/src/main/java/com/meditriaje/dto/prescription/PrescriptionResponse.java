package com.meditriaje.dto.prescription;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * DTO de respuesta para una receta médica.
 * Regla de seguridad (ADR-003): Cero exposición de identificadores autonuméricos de base de datos.
 */
public record PrescriptionResponse(
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
        @JsonProperty("especialidadNombre")
        String specialtyName,
        @JsonProperty("vigenciaDias")
        int validityDays,
        Instant createdAt,
        @JsonProperty("detalles")
        List<PrescriptionDetailResponse> details
) {}
