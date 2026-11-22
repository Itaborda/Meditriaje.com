package com.meditriaje.triage;

import com.meditriaje.exception.InvalidDataException;

/** Síntoma del catálogo. {@code esAlarma} = alarma incondicional (ADR-009). */
public record TriageSymptom(String code, boolean isAlarm) {

    public TriageSymptom {
        if (code == null || code.isBlank()) {
            throw new InvalidDataException("El código del síntoma es obligatorio");
        }
    }
}
