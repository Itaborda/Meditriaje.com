package com.meditriaje.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * DTO que consolida la información administrativa de una cita médica para supervisión,
 * calendario de citas y exportación a reportes analíticos (ADR-003, ADR-006, ADR-007, RF-26).
 * No expone datos de historia clínica privada de pacientes.
 */
public record AdminAppointmentDetailResponse(
        @JsonProperty("citaPublicId")
        String appointmentPublicId,
        @JsonProperty("pacientePublicId")
        String patientPublicId,
        @JsonProperty("pacienteNombre")
        String patientName,
        @JsonProperty("pacienteDocumentoTipo")
        String patientDocumentType,
        @JsonProperty("pacienteDocumentoNumero")
        String patientDocumentNumber,
        @JsonProperty("pacienteTelefono")
        String patientPhone,
        @JsonProperty("pacienteEmail")
        String patientEmail,
        @JsonProperty("profesionalPublicId")
        String professionalPublicId,
        @JsonProperty("profesionalNombre")
        String professionalName,
        @JsonProperty("registroMedico")
        String medicalLicense,
        @JsonProperty("especialidadPublicId")
        String specialtyPublicId,
        @JsonProperty("especialidadNombre")
        String specialtyName,
        @JsonProperty("sedePublicId")
        String sitePublicId,
        @JsonProperty("sedeNombre")
        String siteName,
        @JsonProperty("sedeCiudad")
        String siteCity,
        @JsonProperty("fechaHoraInicio")
        Instant startDateTime,
        @JsonProperty("fechaHoraFin")
        Instant endDateTime,
        @JsonProperty("modalidad")
        String modality,
        @JsonProperty("estado")
        String status,
        @JsonProperty("motivoCancelacion")
        String cancellationReason,
        @JsonProperty("triajePublicId")
        String triagePublicId,
        @JsonProperty("triajeNivel")
        String triageLevel,
        @JsonProperty("motivoConsulta")
        String consultationReason,
        Instant createdAt
) {}
