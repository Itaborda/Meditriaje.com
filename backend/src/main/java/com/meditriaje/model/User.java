package com.meditriaje.model;

import java.time.Instant;

/**
 * Entidad de dominio para la cuenta de usuario del sistema (F2.1, ADR-014).
 * Incluye soporte para autenticación de dos factores (TOTP RFC 6238).
 */
public record User(
        Long id,
        String publicId,
        String email,
        String passwordHash,
        String status,
        int failedAttempts,
        Instant lockedUntil,
        Instant createdAt,
        boolean mustChangePassword,
        boolean mfaEnabled,
        String mfaSecret,
        Instant mfaConfiguredAt
) {
    public User(
            Long id,
            String publicId,
            String email,
            String passwordHash,
            String status,
            int failedAttempts,
            Instant lockedUntil,
            Instant createdAt,
            boolean mustChangePassword
    ) {
        this(id, publicId, email, passwordHash, status, failedAttempts, lockedUntil, createdAt, mustChangePassword, false, null, null);
    }

    public User(
            Long id,
            String publicId,
            String email,
            String passwordHash,
            String status,
            int failedAttempts,
            Instant lockedUntil,
            Instant createdAt
    ) {
        this(id, publicId, email, passwordHash, status, failedAttempts, lockedUntil, createdAt, false, false, null, null);
    }

    /**
     * Retorna verdadero si la cuenta está actualmente bloqueada por intentos fallidos.
     */
    public boolean isLocked() {
        if ("BLOQUEADO".equalsIgnoreCase(status)) {
            if (lockedUntil == null) {
                return true;
            }
            return Instant.now().isBefore(lockedUntil);
        }
        return false;
    }
}
