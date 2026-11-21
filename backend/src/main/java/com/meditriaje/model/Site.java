package com.meditriaje.model;

import java.util.Objects;

/**
 * Modelo inmutable para la sede física o centro de atención médica (ADR-003, HU-10).
 */
public record Site(
        Long id,
        Long institutionId,
        String publicId,
        String name,
        String address,
        String city,
        String status
) {
    public Site {
        Objects.requireNonNull(institutionId, "institucionId no puede ser nulo");
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(name, "nombre no puede ser nulo");
        Objects.requireNonNull(address, "direccion no puede ser nulo");
        Objects.requireNonNull(city, "ciudad no puede ser nulo");
        Objects.requireNonNull(status, "estado no puede ser nulo");
    }
}
