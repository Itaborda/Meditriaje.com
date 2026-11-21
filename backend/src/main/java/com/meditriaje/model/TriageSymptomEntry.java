package com.meditriaje.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Modelo de dominio inmutable para la entidad TriajeSintoma (ADR-009, HU-02).
 */
public record TriageSymptomEntry(
        Long id,
        Long triageId,
        Long symptomId,
        BigDecimal durationHours,
        int intensity
) {
    public TriageSymptomEntry {
        Objects.requireNonNull(symptomId, "sintomaId no puede ser nulo");
        Objects.requireNonNull(durationHours, "duracionHoras no puede ser nula");
    }

    public TriageSymptomEntry(Long triageId, Long symptomId, BigDecimal durationHours, int intensity) {
        this(null, triageId, symptomId, durationHours, intensity);
    }
}
