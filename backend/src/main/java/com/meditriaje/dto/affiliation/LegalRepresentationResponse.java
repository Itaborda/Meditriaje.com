package com.meditriaje.dto.affiliation;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LegalRepresentationResponse(
        String publicId,
        @JsonProperty("menorPublicId")
        String minorPublicId,
        @JsonProperty("menorNombre")
        String minorName,
        @JsonProperty("menorDocumento")
        String minorDocument,
        @JsonProperty("representantePublicId")
        String representativePublicId,
        @JsonProperty("representanteNombre")
        String representativeName,
        @JsonProperty("representanteDocumento")
        String representativeDocument,
        @JsonProperty("parentesco")
        String relationship,
        @JsonProperty("verificado")
        boolean verified
) {
}
