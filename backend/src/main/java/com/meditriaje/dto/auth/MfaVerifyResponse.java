package com.meditriaje.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Respuesta tras verificar y activar exitosamente MFA TOTP, entregando códigos de respaldo (ADR-014, F2.1.4).
 */
public record MfaVerifyResponse(
        @JsonProperty("mfaHabilitado")
        boolean mfaEnabled,
        List<String> backupCodes,
        @JsonProperty("mensaje")
        String message
) {
}
