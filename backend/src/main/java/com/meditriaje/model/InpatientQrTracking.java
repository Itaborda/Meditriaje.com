package com.meditriaje.model;

import java.time.Instant;

/**
 * Modelo para el código QR seguro de seguimiento intrahospitalario del paciente (Fase O, ADR-028).
 */
public record InpatientQrTracking(
        Long id,
        String publicId,
        Long episodeId,
        String qrTokenCode,
        String currentLocationText,
        String status,
        Instant createdAt,
        Instant expiresAt
) {
}
