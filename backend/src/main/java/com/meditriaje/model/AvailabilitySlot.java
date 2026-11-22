package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo inmutable para el slot o turno de disponibilidad asistencial (ADR-003, ADR-005, ADR-006, HU-10).
 */
public record AvailabilitySlot(
        Long id,
        String publicId,
        Long professionalId,
        Long siteId,
        Long specialtyId,
        Instant startDateTime,
        Instant endDateTime,
        String modality,
        String status
) {
    public AvailabilitySlot {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(professionalId, "profesionalId no puede ser nulo");
        Objects.requireNonNull(siteId, "sedeId no puede ser nulo");
        Objects.requireNonNull(specialtyId, "especialidadId no puede ser nulo");
        Objects.requireNonNull(startDateTime, "fechaHoraInicio no puede ser nulo");
        Objects.requireNonNull(endDateTime, "fechaHoraFin no puede ser nulo");
        Objects.requireNonNull(modality, "modalidad no puede ser nulo");
        Objects.requireNonNull(status, "estado no puede ser nulo");
    }

    public AvailabilitySlot(
            String publicId,
            Long professionalId,
            Long siteId,
            Long specialtyId,
            Instant startDateTime,
            Instant endDateTime,
            String modality,
            String status
    ) {
        this(null, publicId, professionalId, siteId, specialtyId, startDateTime, endDateTime, modality, status);
    }
}
