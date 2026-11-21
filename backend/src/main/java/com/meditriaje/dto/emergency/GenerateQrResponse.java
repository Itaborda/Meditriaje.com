package com.meditriaje.dto.emergency;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Respuesta inmediata a la creación del acceso temporal. Contiene el token en texto plano para renderizar el QR.
 */
public record GenerateQrResponse(
        String publicId,
        String token,
        String qrUrl,
        @JsonProperty("expiraAt")
        Instant expiresAt,
        @JsonProperty("maxAccesos")
        int maxAccesses,
        @JsonProperty("requierePin")
        boolean requiresPin,
        @JsonProperty("incluirAlergias")
        boolean includeAllergies,
        @JsonProperty("incluirMedicamentos")
        boolean includeMedications,
        @JsonProperty("incluirAtenciones")
        boolean includeAttentions,
        @JsonProperty("incluirContacto")
        boolean includeContact,
        Instant createdAt
) {
}
