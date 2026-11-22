package com.meditriaje.model;

import java.time.Instant;

/**
 * Registro de asignación longitudinal de una cama a un episodio de atención (Fase H, ADR-024).
 * Cumple la regla de ocupación única por intervalo.
 */
public record BedOccupancy(
        Long id,
        String publicId,
        Long episodeId,
        Long bedId,
        Instant startedAt,
        Instant endedAt,
        String status,
        Long assignedByUserId,
        String assignmentReason,
        Instant createdAt
) {
}
