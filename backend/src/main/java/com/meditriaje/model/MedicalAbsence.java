package com.meditriaje.model;

import java.time.Instant;

/**
 * Ausencia programada, permiso, incapacidad o bloqueo de agenda médica (Fase C, ADR-026).
 */
public record MedicalAbsence(
        Long id,
        String publicId,
        Long professionalId,
        Instant startDate,
        Instant endDate,
        String reason,
        String status,
        Long registeredByUserId,
        Instant createdAt
) {
}
