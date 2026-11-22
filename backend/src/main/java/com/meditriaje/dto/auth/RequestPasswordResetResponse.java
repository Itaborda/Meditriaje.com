package com.meditriaje.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta genérica ante solicitud de recuperación de contraseña para evitar enumeración (F2.1, ADR-014).
 */
public record RequestPasswordResetResponse(
        @JsonProperty("mensaje")
        String message
) {
    public static final String DEFAULT_MESSAGE =
            "Si el correo se encuentra registrado en el sistema, recibiras un codigo de 6 digitos para restablecer tu contrasena.";

    public static RequestPasswordResetResponse defaultResponse() {
        return new RequestPasswordResetResponse(DEFAULT_MESSAGE);
    }
}
