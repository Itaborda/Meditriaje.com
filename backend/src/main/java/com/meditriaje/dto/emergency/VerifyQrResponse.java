package com.meditriaje.dto.emergency;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Información pública preliminar de un token QR escaneado (indica vigencia y si requiere PIN).
 */
public record VerifyQrResponse(
        @JsonProperty("valido")
        boolean valid,
        @JsonProperty("requierePin")
        boolean requiresPin,
        @JsonProperty("estado")
        String status,
        @JsonProperty("expiraAt")
        Instant expiresAt
) {
}
