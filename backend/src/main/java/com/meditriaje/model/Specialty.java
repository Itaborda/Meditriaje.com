package com.meditriaje.model;

import java.util.Objects;

/**
 * Modelo inmutable para la especialidad médica (ADR-003, ADR-006, HU-10).
 */
public record Specialty(
        Long id,
        String publicId,
        String name,
        int slotDurationMinutes,
        String status
) {
    public Specialty {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(name, "nombre no puede ser nulo");
        Objects.requireNonNull(status, "estado no puede ser nulo");
    }
}
