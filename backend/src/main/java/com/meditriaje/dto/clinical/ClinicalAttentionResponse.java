package com.meditriaje.dto.clinical;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * DTO de respuesta para la atención médica (HU-07, HU-09).
 */
public record ClinicalAttentionResponse(
        String publicId,
        @JsonProperty("citaPublicId")
        String appointmentPublicId,
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
        @JsonProperty("estado")
        String status,
        Instant createdAt,
        @JsonProperty("fechaCierre")
        Instant closedAt,
        @JsonProperty("diagnosticoCodigo")
        String diagnosisCode,
        @JsonProperty("diagnosticoDescripcion")
        String diagnosisDescription,
        @JsonProperty("motivoConsulta")
        String consultationReason,
        @JsonProperty("evolucion")
        String progressNote,
        @JsonProperty("indicaciones")
        String instructions,
        @JsonProperty("signosVitales")
        VitalSignsDto vitalSigns,
        @JsonProperty("enmiendas")
        List<AmendmentResponse> amendments
) {
    public ClinicalAttentionResponse(
            String publicId,
            String appointmentPublicId,
            String patientPublicId,
            String patientName,
            String professionalPublicId,
            String professionalName,
            String specialtyName,
            String status,
            Instant createdAt,
            Instant closedAt,
            String diagnosisCode,
            String diagnosisDescription,
            String consultationReason,
            String progressNote,
            String instructions,
            VitalSignsDto vitalSigns
    ) {
        this(
                publicId,
                appointmentPublicId,
                patientPublicId,
                patientName,
                professionalPublicId,
                professionalName,
                specialtyName,
                status,
                createdAt,
                closedAt,
                diagnosisCode,
                diagnosisDescription,
                consultationReason,
                progressNote,
                instructions,
                vitalSigns,
                List.of()
        );
    }
}
