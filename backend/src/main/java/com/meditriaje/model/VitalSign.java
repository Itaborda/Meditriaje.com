package com.meditriaje.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Modelo de dominio inmutable para los parámetros fisiológicos {@code SIGNO_VITAL}
 * (ADR-008, HU-07).
 */
public record VitalSign(
        Long id,
        Long attentionId,
        Integer systolicPressure,
        Integer diastolicPressure,
        Integer heartRate,
        Integer respiratoryRate,
        BigDecimal temperature,
        Integer oxygenSaturation,
        BigDecimal weightKg,
        BigDecimal heightCm
) {
    public VitalSign {
        Objects.requireNonNull(attentionId, "atencionId no puede ser nulo");
    }

    public VitalSign(
            Long attentionId,
            Integer systolicPressure,
            Integer diastolicPressure,
            Integer heartRate,
            Integer respiratoryRate,
            BigDecimal temperature,
            Integer oxygenSaturation,
            BigDecimal weightKg,
            BigDecimal heightCm
    ) {
        this(null, attentionId, systolicPressure, diastolicPressure, heartRate, respiratoryRate,
                temperature, oxygenSaturation, weightKg, heightCm);
    }
}
