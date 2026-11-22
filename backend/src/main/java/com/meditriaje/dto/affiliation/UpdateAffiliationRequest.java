package com.meditriaje.dto.affiliation;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateAffiliationRequest(
        @JsonProperty("epsPublicId")
        @NotBlank(message = "epsPublicId es obligatorio")
        String epsPublicId,

        @JsonProperty("regimen")
        @NotBlank(message = "regimen es obligatorio")
        @Pattern(regexp = "CONTRIBUTIVO|SUBSIDIADO|ESPECIAL|NO_ASEGURADO", message = "Regimen no válido")
        String regime,

        @JsonProperty("tipoAfiliado")
        @NotBlank(message = "tipoAfiliado es obligatorio")
        @Pattern(regexp = "COTIZANTE|BENEFICIARIO|ADICIONAL|CABEZA_FAMILIA", message = "tipoAfiliado no válido")
        String affiliateType,

        @JsonProperty("estado")
        @NotBlank(message = "estado es obligatorio")
        @Pattern(regexp = "ACTIVO|SUSPENDIDO|RETIRADO|NO_AFILIADO", message = "Estado no válido")
        String status
) {
}
