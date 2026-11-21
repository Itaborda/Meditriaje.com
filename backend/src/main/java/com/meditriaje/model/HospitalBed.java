package com.meditriaje.model;

import java.time.Instant;

/**
 * Cama física hospitalaria con control de estado y ocupación (Fase H, ADR-024).
 * Estados: DISPONIBLE, OCUPADA, LIMPIEZA, MANTENIMIENTO, INACTIVA.
 */
public record HospitalBed(
        Long id,
        String publicId,
        Long roomId,
        String code,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
