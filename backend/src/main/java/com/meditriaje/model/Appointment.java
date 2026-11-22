package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo inmutable para la entidad Cita asistencial (ADR-002, ADR-003, ADR-006, HU-04).
 */
public record Appointment(
        Long id,
        String publicId,
        Long slotId,
        Long patientId,
        Long triageId,
        Long originAppointmentId,
        String status,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt
) {
    public Appointment {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(slotId, "slotId no puede ser nulo");
        Objects.requireNonNull(patientId, "pacienteId no puede ser nulo");
        Objects.requireNonNull(status, "estado no puede ser nulo");
    }

    public Appointment(
            String publicId,
            Long slotId,
            Long patientId,
            Long triageId,
            Long originAppointmentId,
            String status,
            String cancellationReason
    ) {
        this(null, publicId, slotId, patientId, triageId, originAppointmentId, status, cancellationReason, null, null);
    }
}
