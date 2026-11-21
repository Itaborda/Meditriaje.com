package com.meditriaje.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Registro de afiliación de un paciente a una EPS (Fase A, ADR-025).
 * Regímenes: CONTRIBUTIVO, SUBSIDIADO, ESPECIAL, NO_ASEGURADO.
 */
public record PatientAffiliation(
        Long id,
        String publicId,
        Long patientId,
        String documentType,
        String documentNumber,
        Long epsId,
        String regime,
        String affiliateType,
        String status,
        LocalDate affiliationDate,
        String verificationSource,
        Instant lastVerifiedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
