package com.meditriaje.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Representa una valoración clínica presencial de triaje estructurada y firmada por un profesional (Fase U, ADR-027).
 * Inmutable y versionada append-only.
 */
public record TriageAssessment(
        Long id,
        String publicId,
        Long episodeId,
        Integer version,
        String level,
        String consultationReason,
        String clinicalFindings,
        String bloodPressure,
        Integer heartRate,
        Integer respiratoryRate,
        Integer oxygenSaturation,
        BigDecimal temperature,
        Integer glasgowScale,
        Long evaluatorUserId,
        boolean isReevaluation,
        String reevaluationReason,
        Instant createdAt
) {
    public TriageAssessment {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(episodeId, "episodioId no puede ser nulo");
        Objects.requireNonNull(version, "version no puede ser nula");
        Objects.requireNonNull(level, "nivel no puede ser nulo");
        Objects.requireNonNull(consultationReason, "motivoConsulta no puede ser nulo");
        Objects.requireNonNull(evaluatorUserId, "evaluadorUsuarioId no puede ser nulo");
    }
}
