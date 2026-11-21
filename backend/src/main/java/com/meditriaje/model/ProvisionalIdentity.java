package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Representa la identificación temporal de un paciente indocumentado o no identificado (NN) (Fase U, ADR-023).
 */
public record ProvisionalIdentity(
        Long id,
        String publicId,
        Long episodeId,
        String provisionalCode,
        String physicalDescription,
        Integer apparentAge,
        String apparentGender,
        String arrivalCondition,
        String status,
        Long linkedPatientId,
        Long confirmedByUserId,
        Instant confirmedAt,
        Instant createdAt
) {
    public ProvisionalIdentity {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(episodeId, "episodioId no puede ser nulo");
        Objects.requireNonNull(provisionalCode, "codigoProvisional no puede ser nulo");
        Objects.requireNonNull(status, "estado no puede ser nulo");
    }
}
