package com.meditriaje.model;

import java.time.Instant;

/**
 * Habitación o sala individual/compartida dentro de un área hospitalaria (Fase H, ADR-024).
 */
public record HospitalRoom(
        Long id,
        String publicId,
        Long areaId,
        String code,
        String type,
        String status,
        Instant createdAt
) {
}
