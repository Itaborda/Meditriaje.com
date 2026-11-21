package com.meditriaje.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Modelo inmutable para el paciente (ADR-002, ADR-003, HU-09).
 */
public record Patient(
        Long id,
        Long userId,
        String publicId,
        String documentType,
        String documentNumber,
        String firstNames,
        String lastNames,
        LocalDate birthDate,
        String phone,
        Instant createdAt,
        Instant updatedAt
) {
    public Patient {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(documentType, "tipoDocumento no puede ser nulo");
        Objects.requireNonNull(documentNumber, "numeroDocumento no puede ser nulo");
        Objects.requireNonNull(firstNames, "nombres no puede ser nulo");
        Objects.requireNonNull(lastNames, "apellidos no puede ser nulo");
    }

    public Patient(
            Long id,
            Long userId,
            String publicId,
            String documentType,
            String documentNumber,
            String firstNames,
            String lastNames,
            LocalDate birthDate,
            String phone
    ) {
        this(id, userId, publicId, documentType, documentNumber, firstNames, lastNames, birthDate, phone, null, null);
    }
}
