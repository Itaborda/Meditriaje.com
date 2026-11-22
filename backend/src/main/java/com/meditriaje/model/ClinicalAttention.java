package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo de dominio inmutable para la cabecera del acto médico asistencial {@code ATENCION}
 * (ADR-007, ADR-008, HU-07).
 */
public record ClinicalAttention(
        Long id,
        String publicId,
        Long appointmentId,
        Long patientId,
        Long professionalId,
        Long primaryDiagnosisId,
        String consultationReason,
        String progressNote,
        String instructions,
        String status,
        Instant closedAt,
        Instant createdAt
) {
    public ClinicalAttention {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(appointmentId, "citaId no puede ser nulo");
        Objects.requireNonNull(patientId, "pacienteId no puede ser nulo");
        Objects.requireNonNull(professionalId, "profesionalId no puede ser nulo");
        Objects.requireNonNull(status, "estado no puede ser nulo");
    }

    public ClinicalAttention(
            Long id,
            String publicId,
            Long appointmentId,
            Long patientId,
            Long professionalId,
            String status
    ) {
        this(id, publicId, appointmentId, patientId, professionalId, null, null, null, null, status, null, Instant.now());
    }

    public boolean isClosed() {
        return "CERRADA".equalsIgnoreCase(status);
    }

    public boolean isOpen() {
        return "ABIERTA".equalsIgnoreCase(status);
    }
}
