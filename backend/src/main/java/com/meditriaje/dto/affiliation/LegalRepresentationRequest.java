package com.meditriaje.dto.affiliation;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LegalRepresentationRequest(
        @JsonProperty("menorPublicId")
        @NotBlank(message = "menorPublicId es obligatorio")
        String minorPublicId,

        @JsonProperty("representantePublicId")
        @NotBlank(message = "representantePublicId es obligatorio")
        String representativePublicId,

        @JsonProperty("parentesco")
        @NotBlank(message = "parentesco es obligatorio")
        @Pattern(regexp = "PADRE|MADRE|TUTOR_LEGAL|CUIDADOR_AUTORIZADO", message = "Parentesco no válido")
        String relationship,

        @JsonProperty("documentoSoporte")
        String supportingDocument
) {
}
