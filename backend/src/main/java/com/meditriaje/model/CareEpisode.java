package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Representa un episodio de atención hospitalario independiente del agendamiento de citas (Fase U, ADR-022).
 */
public record CareEpisode(
        Long id,
        String publicId,
        Long patientId,
        String type,
        Long siteId,
        String status,
        Instant admittedAt,
        Instant dischargedAt,
        String admissionReason,
        Instant createdAt,
        Instant updatedAt
) {
    public CareEpisode {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(type, "tipo no puede ser nulo");
        Objects.requireNonNull(siteId, "sedeId no puede ser nulo");
        Objects.requireNonNull(status, "estado no puede ser nulo");
    }
}
