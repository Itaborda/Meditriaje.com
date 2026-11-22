package com.meditriaje.dto.appointment;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Respuesta inmutable que detalla la información consolidada de una cita agendada (ADR-003, ADR-006, HU-04, HU-06).
 */
public record AppointmentResponse(
        String publicId,
        @JsonProperty("slotPublicId")
        String slotPublicId,
        @JsonProperty("pacientePublicId")
        String patientPublicId,
        @JsonProperty("pacienteNombre")
        String patientName,
        @JsonProperty("profesionalPublicId")
        String professionalPublicId,
        @JsonProperty("profesionalNombre")
        String professionalName,
        @JsonProperty("especialidadPublicId")
        String specialtyPublicId,
        @JsonProperty("especialidadNombre")
        String specialtyName,
        @JsonProperty("sedePublicId")
        String sitePublicId,
        @JsonProperty("sedeNombre")
        String siteName,
        @JsonProperty("sedeDireccion")
        String siteAddress,
        @JsonProperty("fechaHoraInicio")
        Instant startDateTime,
        @JsonProperty("fechaHoraFin")
        Instant endDateTime,
        @JsonProperty("modalidad")
        String modality,
        @JsonProperty("estado")
        String status,
        @JsonProperty("triajePublicId")
        String triagePublicId,
        @JsonProperty("citaOrigenPublicId")
        String originAppointmentPublicId,
        @JsonProperty("triajeNivel")
        String triageLevel,
        @JsonProperty("motivoConsulta")
        String consultationReason,
        Instant createdAt,
        @JsonProperty("tieneAtencion")
        boolean hasAttention,
        @JsonProperty("atencionPublicId")
        String attentionPublicId,
        @JsonProperty("atencionEstado")
        String attentionStatus
) {
    /**
     * Constructor de compatibilidad (20 parámetros) para preservar retrocompatibilidad.
     */
    public AppointmentResponse(
            String publicId,
            String slotPublicId,
            String patientPublicId,
            String patientName,
            String professionalPublicId,
            String professionalName,
            String specialtyPublicId,
            String specialtyName,
            String sitePublicId,
            String siteName,
            String siteAddress,
            Instant startDateTime,
            Instant endDateTime,
            String modality,
            String status,
            String triagePublicId,
            String originAppointmentPublicId,
            String triageLevel,
            String consultationReason,
            Instant createdAt
    ) {
        this(
                publicId,
                slotPublicId,
                patientPublicId,
                patientName,
                professionalPublicId,
                professionalName,
                specialtyPublicId,
                specialtyName,
                sitePublicId,
                siteName,
                siteAddress,
                startDateTime,
                endDateTime,
                modality,
                status,
                triagePublicId,
                originAppointmentPublicId,
                triageLevel,
                consultationReason,
                createdAt,
                false,
                null,
                null
        );
    }

    /**
     * Constructor de compatibilidad (18 parámetros) para preservar retrocompatibilidad con tests y servicios existentes.
     */
    public AppointmentResponse(
            String publicId,
            String slotPublicId,
            String patientPublicId,
            String patientName,
            String professionalPublicId,
            String professionalName,
            String specialtyPublicId,
            String specialtyName,
            String sitePublicId,
            String siteName,
            String siteAddress,
            Instant startDateTime,
            Instant endDateTime,
            String modality,
            String status,
            String triagePublicId,
            String originAppointmentPublicId,
            Instant createdAt
    ) {
        this(
                publicId,
                slotPublicId,
                patientPublicId,
                patientName,
                professionalPublicId,
                professionalName,
                specialtyPublicId,
                specialtyName,
                sitePublicId,
                siteName,
                siteAddress,
                startDateTime,
                endDateTime,
                modality,
                status,
                triagePublicId,
                originAppointmentPublicId,
                null,
                null,
                createdAt,
                false,
                null,
                null
        );
    }
}
