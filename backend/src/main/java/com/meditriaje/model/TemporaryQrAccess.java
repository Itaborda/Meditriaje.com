package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo de dominio inmutable que representa un acceso temporal mediante código QR (ADR-010, §5.17, §5.18).
 */
public record TemporaryQrAccess(
        Long id,
        String publicId,
        Long patientId,
        String tokenHash,
        String pinHash,
        boolean includeAllergies,
        boolean includeMedications,
        boolean includeAttentions,
        boolean includeContact,
        int maxAccesses,
        int accessCount,
        boolean revoked,
        int failedPinAttempts,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt
) {
    public TemporaryQrAccess {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(patientId, "pacienteId no puede ser nulo");
        Objects.requireNonNull(tokenHash, "tokenHash no puede ser nulo");
        Objects.requireNonNull(expiresAt, "expiraAt no puede ser nulo");
    }

    /**
     * Constructor de compatibilidad para instancias previas sin contador de fallos de PIN.
     */
    public TemporaryQrAccess(
            Long id,
            String publicId,
            Long patientId,
            String tokenHash,
            String pinHash,
            boolean includeAllergies,
            boolean includeMedications,
            boolean includeAttentions,
            boolean includeContact,
            int maxAccesses,
            int accessCount,
            boolean revoked,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                id,
                publicId,
                patientId,
                tokenHash,
                pinHash,
                includeAllergies,
                includeMedications,
                includeAttentions,
                includeContact,
                maxAccesses,
                accessCount,
                revoked,
                0,
                expiresAt,
                createdAt,
                updatedAt
        );
    }

    /**
     * Determina si el acceso temporal está activo y puede ser utilizado para consultar el resumen.
     */
    public boolean isActive(Instant now) {
        return resolveStatus(now) == QrAccessStatus.ACTIVO;
    }

    /**
     * Indica si el acceso requiere verificación de PIN numérico.
     */
    public boolean requiresPin() {
        return pinHash != null && !pinHash.isBlank();
    }

    /**
     * Resuelve el estado actual del token temporal respecto a un instante de tiempo.
     */
    public QrAccessStatus resolveStatus(Instant now) {
        if (revoked || failedPinAttempts >= 3) {
            return QrAccessStatus.REVOCADO;
        }
        if (now.isAfter(expiresAt)) {
            return QrAccessStatus.EXPIRADO;
        }
        if (accessCount >= maxAccesses) {
            return QrAccessStatus.AGOTADO;
        }
        return QrAccessStatus.ACTIVO;
    }
}
