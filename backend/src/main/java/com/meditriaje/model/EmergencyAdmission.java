package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Representa la llegada y admisión presencial a urgencias (Fase U, ADR-022).
 */
public record EmergencyAdmission(
        Long id,
        String publicId,
        Long episodeId,
        String arrivalMode,
        String companionName,
        String companionContact,
        String briefReason,
        String notes,
        Long registeredByUserId,
        Instant createdAt
) {
    public EmergencyAdmission {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(episodeId, "episodioId no puede ser nulo");
        Objects.requireNonNull(arrivalMode, "medioLlegada no puede ser nulo");
        Objects.requireNonNull(briefReason, "motivoResumido no puede ser nulo");
        Objects.requireNonNull(registeredByUserId, "registradoPorUsuarioId no puede ser nulo");
    }
}
