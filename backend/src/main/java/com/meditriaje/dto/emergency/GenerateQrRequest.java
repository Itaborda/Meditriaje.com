package com.meditriaje.dto.emergency;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;

/**
 * Solicitud de generación de token y código QR de acceso temporal al resumen de salud (ADR-010).
 */
public record GenerateQrRequest(
        @Pattern(regexp = "^[0-9]{4}$", message = "El PIN opcional debe ser exactamente de 4 dígitos numéricos.")
        String pin,
        @JsonProperty("incluirAlergias")
        Boolean includeAllergies,
        @JsonProperty("incluirMedicamentos")
        Boolean includeMedications,
        @JsonProperty("incluirAtenciones")
        Boolean includeAttentions,
        @JsonProperty("incluirContacto")
        Boolean includeContact
) {
    @JsonProperty("incluirAlergias")
    public boolean getIncludeAllergies() {
        return includeAllergies == null || includeAllergies;
    }

    @JsonProperty("incluirMedicamentos")
    public boolean getIncludeMedications() {
        return includeMedications == null || includeMedications;
    }

    @JsonProperty("incluirAtenciones")
    public boolean getIncludeAttentions() {
        return includeAttentions == null || includeAttentions;
    }

    @JsonProperty("incluirContacto")
    public boolean getIncludeContact() {
        return includeContact == null || includeContact;
    }
}
