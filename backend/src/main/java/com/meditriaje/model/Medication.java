package com.meditriaje.model;

import java.util.Objects;

/**
 * Modelo de dominio inmutable para el catálogo maestro de fármacos {@code MEDICAMENTO} (M7.1, HU-08).
 */
public record Medication(
        Long id,
        String publicId,
        String code,
        String brandName,
        String activeIngredient,
        String presentation,
        String concentration,
        String status
) {
    public Medication {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(code, "codigo no puede ser nulo");
        Objects.requireNonNull(brandName, "nombreComercial no puede ser nulo");
        Objects.requireNonNull(activeIngredient, "principioActivo no puede ser nulo");
        Objects.requireNonNull(presentation, "presentacion no puede ser nula");
        Objects.requireNonNull(concentration, "concentracion no puede ser nula");
        Objects.requireNonNull(status, "estado no puede ser nulo");
    }

    public boolean isActive() {
        return "ACTIVO".equalsIgnoreCase(status);
    }
}
