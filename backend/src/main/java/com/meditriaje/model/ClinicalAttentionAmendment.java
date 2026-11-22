package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo de dominio inmutable para la enmienda o aclaración médica append-only (ADR-008, HU-07).
 */
public record ClinicalAttentionAmendment(
        Long id,
        Long attentionId,
        Long professionalId,
        String reason,
        String content,
        Instant amendedAt
) {
    public ClinicalAttentionAmendment {
        Objects.requireNonNull(attentionId, "atencionId no puede ser nulo");
        Objects.requireNonNull(professionalId, "profesionalId no puede ser nulo");
        Objects.requireNonNull(reason, "motivo no puede ser nulo");
        Objects.requireNonNull(content, "contenido no puede ser nulo");
    }
}
