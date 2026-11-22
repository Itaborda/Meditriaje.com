package com.meditriaje.model;

import java.util.Objects;

/**
 * Modelo de dominio inmutable para el catálogo de síntomas (ADR-009, HU-02).
 */
public record Symptom(
        Long id,
        String publicId,
        String code,
        String name,
        String category,
        boolean isAlarm,
        String status
) {
    public Symptom {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(code, "codigo no puede ser nulo");
        Objects.requireNonNull(name, "nombre no puede ser nulo");
        Objects.requireNonNull(category, "categoria no puede ser nula");
        Objects.requireNonNull(status, "estado no puede ser nulo");
    }
}
