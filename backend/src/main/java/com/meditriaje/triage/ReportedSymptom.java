package com.meditriaje.triage;

import com.meditriaje.exception.InvalidDataException;

import java.math.BigDecimal;

/** Síntoma reportado por el paciente: duración en horas (>= 0) e intensidad 0-10. */
public record ReportedSymptom(String symptomCode, BigDecimal durationHours, int intensity) {

    public static final int MAX_INTENSITY = 10;

    public ReportedSymptom {
        if (symptomCode == null || symptomCode.isBlank()) {
            throw new InvalidDataException("El código del síntoma es obligatorio");
        }
        if (durationHours == null || durationHours.signum() < 0) {
            throw new InvalidDataException("La duración debe ser mayor o igual a 0");
        }
        if (intensity < 0 || intensity > MAX_INTENSITY) {
            throw new InvalidDataException("La intensidad debe estar entre 0 y 10");
        }
    }
}
