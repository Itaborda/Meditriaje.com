package com.meditriaje.dto.allergy;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Petición de registro de hipersensibilidad o alergia clínica (D3, T4).
 */
public record RegisterAllergyRequest(
        @JsonProperty("sustancia")
        @NotBlank(message = "La sustancia es obligatoria.")
        @Size(min = 2, max = 100, message = "La sustancia debe tener entre 2 y 100 caracteres.")
        String substance,

        @JsonProperty("reaccion")
        @Size(max = 200, message = "La reaccion no puede exceder 200 caracteres.")
        String reaction,

        @JsonProperty("severidad")
        @NotBlank(message = "La severidad es obligatoria.")
        @Pattern(regexp = "^(LEVE|MODERADA|GRAVE)$", message = "La severidad debe ser LEVE, MODERADA o GRAVE.")
        String severity,

        @JsonProperty("atencionPublicId")
        String attentionPublicId
) {
    public RegisterAllergyRequest(String substance, String reaction, String severity) {
        this(substance, reaction, severity, null);
    }
}
