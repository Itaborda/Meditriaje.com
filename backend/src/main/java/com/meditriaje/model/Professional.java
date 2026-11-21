package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo inmutable para el profesional asistencial (ADR-003, ADR-006, HU-10, Ley 1164/2007).
 */
public record Professional(
        Long id,
        Long userId,
        String publicId,
        Long specialtyId,
        String documentType,
        String documentNumber,
        String medicalLicense,
        String firstNames,
        String lastNames,
        String phone,
        Instant createdAt,
        Instant updatedAt
) {
    public Professional {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(medicalLicense, "registroMedico no puede ser nulo");
        Objects.requireNonNull(firstNames, "nombres no puede ser nulo");
        Objects.requireNonNull(lastNames, "apellidos no puede ser nulo");
        if (documentType == null || documentType.isBlank()) {
            documentType = "CC";
        }
    }

    public Professional(
            Long id,
            Long userId,
            String publicId,
            Long specialtyId,
            String medicalLicense,
            String firstNames,
            String lastNames,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, userId, publicId, specialtyId, "CC", null, medicalLicense, firstNames, lastNames, null, createdAt, updatedAt);
    }

    public Professional(
            Long userId,
            String publicId,
            Long specialtyId,
            String medicalLicense,
            String firstNames,
            String lastNames
    ) {
        this(null, userId, publicId, specialtyId, "CC", null, medicalLicense, firstNames, lastNames, null, null, null);
    }

    public Professional(
            Long userId,
            String publicId,
            Long specialtyId,
            String documentType,
            String documentNumber,
            String medicalLicense,
            String firstNames,
            String lastNames,
            String phone
    ) {
        this(null, userId, publicId, specialtyId, documentType, documentNumber, medicalLicense, firstNames, lastNames, phone, null, null);
    }
}
