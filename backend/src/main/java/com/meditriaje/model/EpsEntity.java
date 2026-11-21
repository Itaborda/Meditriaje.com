package com.meditriaje.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Entidad Promotora de Salud (EPS) aseguradora colombiana (Fase A, ADR-025).
 */
public record EpsEntity(
        Long id,
        String publicId,
        @JsonProperty("codigoMinSalud")
        String minSaludCode,
        @JsonProperty("nombre")
        String name,
        @JsonProperty("nit")
        String nit,
        @JsonProperty("regimenHabitual")
        String defaultRegime,
        @JsonProperty("estado")
        String status,
        @JsonProperty("creadoAt")
        Instant createdAt
) {
}
