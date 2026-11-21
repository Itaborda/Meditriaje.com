package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo de dominio inmutable para la cabecera del evento de dispensación en farmacia {@code DISPENSACION} (F2.4, ADR-016).
 * Inmutable tras su inserción.
 */
public record Dispensation(
        Long id,
        String publicId,
        Long prescriptionId,
        Long siteId,
        Long userId,
        String notes,
        Instant createdAt
) {
    public Dispensation {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(prescriptionId, "recetaId no puede ser nulo");
        Objects.requireNonNull(siteId, "sedeId no puede ser nula");
        Objects.requireNonNull(userId, "usuarioId no puede ser nulo");
    }
}
