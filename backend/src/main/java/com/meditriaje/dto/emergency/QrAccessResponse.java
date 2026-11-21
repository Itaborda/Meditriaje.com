package com.meditriaje.dto.emergency;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Detalle de un token temporal generado para consulta en el portal del paciente.
 */
public record QrAccessResponse(
        String publicId,
        @JsonProperty("estado")
        String status,
        @JsonProperty("maxAccesos")
        int maxAccesses,
        @JsonProperty("accesosRealizados")
        int accessCount,
        @JsonProperty("revocado")
        boolean revoked,
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
        @JsonProperty("expiraAt")
        Instant expiresAt,
        Instant createdAt
) {
}
