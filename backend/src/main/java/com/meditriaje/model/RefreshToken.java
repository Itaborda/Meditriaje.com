package com.meditriaje.model;

import java.time.Instant;

/**
 * Entidad de dominio para el token de refresco rotativo (ADR-002).
 */
public record RefreshToken(
        Long id,
        Long userId,
        String tokenHash,
        Instant expiresAt,
        boolean revoked,
        Instant createdAt
) {
    /**
     * Retorna verdadero si el token ha superado su fecha de expiración.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
