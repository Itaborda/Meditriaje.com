package com.meditriaje.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Representa una tarea o plan de seguimiento post-atención médica (ADR-015).
 * Mapea la tabla {@code SEGUIMIENTO_POST_ATENCION}.
 */
public record PostAttentionFollowUp(
        Long id,
        String publicId,
        Long attentionId,
        Long patientId,
        Long professionalId,
        String type,
        String instructions,
        LocalDate suggestedControlDate,
        String status,
        Instant patientRespondedAt,
        String patientReport,
        Instant createdAt,
        Instant updatedAt
) {
}
