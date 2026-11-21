package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Representa la asignación de un profesional asistencial a un episodio de atención (Fase U, ADR-022).
 */
public record CareAssignment(
        Long id,
        String publicId,
        Long episodeId,
        Long professionalId,
        String roleFunction,
        Long assignedByUserId,
        Instant startDate,
        Instant endDate,
        boolean active,
        Instant createdAt
) {
    public CareAssignment {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(episodeId, "episodioId no puede ser nulo");
        Objects.requireNonNull(professionalId, "profesionalId no puede ser nulo");
        Objects.requireNonNull(roleFunction, "funcion no puede ser nula");
        Objects.requireNonNull(assignedByUserId, "asignadoPorUsuarioId no puede ser nulo");
    }
}
