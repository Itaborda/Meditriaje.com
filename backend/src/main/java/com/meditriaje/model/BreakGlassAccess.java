package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo de dominio inmutable para un registro de acceso de emergencia Break-Glass (ADR-007, ADR-017).
 * Representa una autorización temporal excepcional otorgada a un profesional asistencial
 * para acceder a la historia clínica de un paciente ante una situación de urgencia vital.
 */
public record BreakGlassAccess(
        Long id,
        String publicId,
        Long professionalId,
        Long patientId,
        String reason,
        Instant expirationDate,
        Instant createdAt
) {
    public BreakGlassAccess {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(professionalId, "profesionalId no puede ser nulo");
        Objects.requireNonNull(patientId, "pacienteId no puede ser nulo");
        Objects.requireNonNull(reason, "motivo no puede ser nulo");
        Objects.requireNonNull(expirationDate, "fechaExpiracion no puede ser nula");
        if (reason.trim().length() < 20) {
            throw new IllegalArgumentException("El motivo de emergencia debe tener al menos 20 caracteres.");
        }
    }

    /**
     * Determina si el acceso break-glass se encuentra activo (no expirado) en el instante dado.
     */
    public boolean isActive(Instant now) {
        Objects.requireNonNull(now, "ahora no puede ser nulo");
        return now.isBefore(expirationDate);
    }
}
