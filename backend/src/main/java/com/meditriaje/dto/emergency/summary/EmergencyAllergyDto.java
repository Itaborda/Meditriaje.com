package com.meditriaje.dto.emergency.summary;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Registro de alergia o hipersensibilidad en el resumen de emergencia (ADR-010, §5.17, T4).
 */
public record EmergencyAllergyDto(
        @JsonProperty("sustancia")
        String substance,
        @JsonProperty("reaccion")
        String reaction,
        @JsonProperty("severidad")
        String severity,
        @JsonProperty("origen")
        String origin
) {
    /**
     * Constructor de conveniencia compatible hacia atrás (V008).
     */
    public EmergencyAllergyDto(String substance, String reaction, String severity) {
        this(substance, reaction, severity, "PROFESIONAL");
    }
}
