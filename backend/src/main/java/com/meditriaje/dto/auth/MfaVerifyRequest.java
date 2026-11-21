package com.meditriaje.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Solicitud de confirmación de enrolamiento TOTP con el primer código válido (ADR-014, F2.1.4).
 */
public record MfaVerifyRequest(
        @JsonProperty("codigo")
        @NotBlank(message = "El codigo TOTP es obligatorio.")
        @Pattern(regexp = "^[0-9]{6}$", message = "El codigo TOTP debe contener exactamente 6 digitos numericos.")
        String code
) {
}
