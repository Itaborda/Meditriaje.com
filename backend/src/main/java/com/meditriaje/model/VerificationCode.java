package com.meditriaje.model;

import java.time.Instant;

/**
 * Entidad de dominio inmutable que representa un código numérico OTP de verificación (F2.1, ADR-014).
 * Almacena el hash SHA-256 del código para recuperación de contraseña y enrolamiento MFA.
 */
public record VerificationCode(
        Long id,
        String publicId,
        Long userId,
        String type,
        String codeHash,
        Instant expirationDate,
        int failedAttempts,
        int maxAttempts,
        boolean used,
        Instant createdAt
) {
    public static final String PASSWORD_RECOVERY_TYPE = "RECUPERACION_PASSWORD";

    public boolean isExpired(Instant now) {
        return expirationDate != null && now.isAfter(expirationDate);
    }

    public boolean reachedMaxAttempts() {
        return failedAttempts >= maxAttempts;
    }

    public boolean isValid(Instant now) {
        return !used && !isExpired(now) && !reachedMaxAttempts();
    }
}
