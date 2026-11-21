package com.meditriaje.model;

import java.util.Objects;

/**
 * Modelo de dominio inmutable para el catálogo de patologías {@code DIAGNOSTICO_CIE10} (ADR-013).
 */
public record Cie10Diagnosis(
        Long id,
        String code,
        String description,
        String status
) {
    public Cie10Diagnosis {
        Objects.requireNonNull(code, "codigo no puede ser nulo");
        Objects.requireNonNull(description, "descripcion no puede ser nulo");
        Objects.requireNonNull(status, "estado no puede ser nulo");
    }

    public boolean isActive() {
        return "ACTIVO".equalsIgnoreCase(status);
    }
}
