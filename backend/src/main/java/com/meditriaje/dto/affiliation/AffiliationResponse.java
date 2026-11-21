package com.meditriaje.dto.affiliation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;

public record AffiliationResponse(
        String publicId,
        @JsonProperty("pacientePublicId")
        String patientPublicId,
        @JsonProperty("tipoDocumento")
        String documentType,
        @JsonProperty("numeroDocumento")
        String documentNumber,
        @JsonProperty("pacienteNombre")
        String patientName,
        @JsonProperty("epsCodigo")
        String epsCode,
        @JsonProperty("epsNombre")
        String epsName,
        @JsonProperty("regimen")
        String regime,
        @JsonProperty("tipoAfiliado")
        String affiliateType,
        @JsonProperty("estado")
        String status,
        @JsonProperty("fechaAfiliacion")
        LocalDate affiliationDate,
        @JsonProperty("fuenteVerificacion")
        String verificationSource,
        @JsonProperty("ultimaVerificacionAt")
        Instant lastVerifiedAt
) {
}
