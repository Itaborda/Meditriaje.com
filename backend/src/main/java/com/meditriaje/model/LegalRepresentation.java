package com.meditriaje.model;

import java.time.Instant;

/**
 * Vinculación de un menor de edad o persona bajo custodia con su tutor o representante legal (Fase C, ADR-026).
 */
public record LegalRepresentation(
        Long id,
        String publicId,
        Long minorPatientId,
        Long representativePatientId,
        String relationship,
        String supportingDocument,
        boolean verified,
        Instant createdAt
) {
}
