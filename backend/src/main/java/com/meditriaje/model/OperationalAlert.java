package com.meditriaje.model;

import java.time.Instant;

/**
 * Modelo de alerta operativa de saturación y demoras hospitalarias (Fase O, ADR-027).
 */
public record OperationalAlert(
        Long id,
        String publicId,
        Long siteId,
        String alertType,
        String severityLevel,
        String message,
        String status,
        Long acknowledgedByUserId,
        Instant acknowledgedAt,
        String acknowledgmentReason,
        Instant createdAt
) {
}
