package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo de dominio inmutable para la prescripción médica {@code RECETA} (M7.1, HU-08, ADR-008).
 * Inmutable desde su inserción.
 */
public record Prescription(
        Long id,
        String publicId,
        Long attentionId,
        Long patientId,
        Long professionalId,
        int validityDays,
        Instant createdAt
) {
    public Prescription {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(attentionId, "atencionId no puede ser nulo");
        Objects.requireNonNull(patientId, "pacienteId no puede ser nulo");
        Objects.requireNonNull(professionalId, "profesionalId no puede ser nulo");
        if (validityDays <= 0) {
            throw new IllegalArgumentException("La vigencia de la receta debe ser mayor a 0 dias.");
        }
    }
}
