package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo de dominio inmutable que representa una hipersensibilidad o alergia de un paciente (ADR-008, V008, V016).
 */
public record Allergy(
        Long id,
        String publicId,
        Long patientId,
        String substance,
        String reaction,
        String severity,
        String status,
        String origin,
        Long registeredByUserId,
        Long attentionId,
        Instant inactivatedAt,
        Long inactivatedByUserId,
        String inactivationReason,
        Instant createdAt,
        Instant updatedAt
) {
    public Allergy {
        Objects.requireNonNull(patientId, "pacienteId no puede ser nulo");
        Objects.requireNonNull(substance, "sustancia no puede ser nula");
        Objects.requireNonNull(severity, "severidad no puede ser nula");
    }

    /**
     * Constructor de compatibilidad hacia atrás para código y pruebas existentes (V008).
     */
    public Allergy(Long id, Long patientId, String substance, String reaction, String severity, Instant createdAt) {
        this(
                id,
                null,
                patientId,
                substance,
                reaction,
                severity,
                "ACTIVA",
                "PROFESIONAL",
                null,
                null,
                null,
                null,
                null,
                createdAt,
                createdAt
        );
    }
}
