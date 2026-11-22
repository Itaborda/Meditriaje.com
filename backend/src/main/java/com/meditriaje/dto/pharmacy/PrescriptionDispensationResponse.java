package com.meditriaje.dto.pharmacy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meditriaje.model.PrescriptionDispensationStatus;

import java.time.Instant;
import java.util.List;

/**
 * Consulta de receta médica con saldos y entregas para dispensación farmacéutica y portal del paciente (F2.4, ADR-016).
 */
public record PrescriptionDispensationResponse(
        @JsonProperty("recetaPublicId")
        String prescriptionPublicId,
        @JsonProperty("codigoReclamacion")
        String claimCode,
        @JsonProperty("atencionPublicId")
        String attentionPublicId,
        @JsonProperty("pacientePublicId")
        String patientPublicId,
        @JsonProperty("pacienteDocumento")
        String patientDocument,
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
        @JsonProperty("fechaEmision")
        Instant issuedAt,
        @JsonProperty("fechaVencimiento")
        Instant expiresAt,
        @JsonProperty("vencida")
        boolean expired,
        @JsonProperty("estadoDispensacion")
        PrescriptionDispensationStatus dispensationStatus,
        List<MedicationBalanceDto> items,
        @JsonProperty("entregasPrevias")
        List<DispensationResponse> previousDeliveries
) {}
