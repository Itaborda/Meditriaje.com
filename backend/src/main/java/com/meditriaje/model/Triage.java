package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo de dominio inmutable para la entidad Triaje (ADR-003, ADR-009, HU-02).
 */
public record Triage(
        Long id,
        String publicId,
        Long patientId,
        String rulesVersion,
        String priorityLevel,
        String suggestedRoute,
        boolean isEmergency,
        String notes,
        Instant createdAt
) {
    public Triage {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(patientId, "pacienteId no puede ser nulo");
        Objects.requireNonNull(rulesVersion, "versionReglas no puede ser nula");
        Objects.requireNonNull(priorityLevel, "nivelPrioridad no puede ser nulo");
        Objects.requireNonNull(suggestedRoute, "rutaSugerida no puede ser nula");
    }

    public Triage(
            String publicId,
            Long patientId,
            String rulesVersion,
            String priorityLevel,
            String suggestedRoute,
            boolean isEmergency,
            String notes
    ) {
        this(null, publicId, patientId, rulesVersion, priorityLevel, suggestedRoute, isEmergency, notes, null);
    }
}
