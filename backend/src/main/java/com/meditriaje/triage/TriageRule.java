package com.meditriaje.triage;

import com.meditriaje.exception.InvalidDataException;

import java.math.BigDecimal;

/**
 * Regla de triaje de prototipo. Duración: {@code [min, max)} (max nulo = sin tope).
 * Intensidad: rango cerrado {@code [intensidadMin, intensidadMax]}.
 */
public record TriageRule(
        String symptomCode,
        BigDecimal minDurationHours,
        BigDecimal maxDurationHours,
        int minIntensity,
        int maxIntensity,
        PriorityLevel level) {

    public TriageRule {
        if (symptomCode == null || symptomCode.isBlank()) {
            throw new InvalidDataException("La regla requiere código de síntoma");
        }
        if (minDurationHours == null || minDurationHours.signum() < 0) {
            throw new InvalidDataException("La duración mínima de la regla debe ser >= 0");
        }
        if (maxDurationHours != null && maxDurationHours.compareTo(minDurationHours) <= 0) {
            throw new InvalidDataException("La duración máxima de la regla debe ser mayor que la mínima");
        }
        if (minIntensity < 0 || maxIntensity > ReportedSymptom.MAX_INTENSITY || minIntensity > maxIntensity) {
            throw new InvalidDataException("Rango de intensidad de la regla inválido");
        }
        if (level == null) {
            throw new InvalidDataException("La regla requiere nivel de prioridad");
        }
    }

    /** Indica si la regla aplica a la duración e intensidad dadas. */
    public boolean applies(BigDecimal durationHours, int intensity) {
        boolean durOk = durationHours.compareTo(minDurationHours) >= 0
                && (maxDurationHours == null || durationHours.compareTo(maxDurationHours) < 0);
        return durOk && intensity >= minIntensity && intensity <= maxIntensity;
    }
}
