package com.meditriaje.triage;

import java.util.List;
import java.util.Objects;

/**
 * Resultado de orientación de triaje (prototipo). No contiene diagnósticos, medicamentos ni tiempos de espera.
 * Si {@code emergencia} es true, la ruta es {@link RutaSugerida#URGENCIAS} y no se ofrece cita.
 */
public record TriageResult(
        PriorityLevel level,
        SuggestedRoute route,
        boolean emergency,
        String rulesVersion,
        String message,
        String notice,
        List<String> alarmSymptoms) {

    public TriageResult {
        Objects.requireNonNull(level, "nivel");
        Objects.requireNonNull(route, "ruta");
        Objects.requireNonNull(rulesVersion, "versionReglas");
        Objects.requireNonNull(message, "mensaje");
        Objects.requireNonNull(notice, "aviso");
        alarmSymptoms = List.copyOf(alarmSymptoms);
    }
}
