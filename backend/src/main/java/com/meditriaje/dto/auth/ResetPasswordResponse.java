package com.meditriaje.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta tras restablecer exitosamente la contraseña (F2.1, ADR-014).
 */
public record ResetPasswordResponse(
        @JsonProperty("mensaje")
        String message
) {
    public static final String DEFAULT_MESSAGE =
            "Contrasena restablecida exitosamente. Ahora puedes iniciar sesion con tu nueva clave.";

    public static ResetPasswordResponse defaultResponse() {
        return new ResetPasswordResponse(DEFAULT_MESSAGE);
    }
}
