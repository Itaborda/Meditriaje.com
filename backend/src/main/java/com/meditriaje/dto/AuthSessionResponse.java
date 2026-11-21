package com.meditriaje.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Respuesta de sesión autenticada (ADR-002, ADR-003, ADR-014).
 * Los tokens de acceso y refresco viajan exclusivamente en cookies HttpOnly y NO en este body.
 * Cuando mfaRequerido es true, se entrega mfaChallengeToken para completar el segundo factor.
 */
public record AuthSessionResponse(
        String publicId,
        String email,
        List<String> roles,
        @JsonProperty("mensaje")
        String message,
        @JsonProperty("debeCambiarPassword")
        boolean mustChangePassword,
        @JsonProperty("mfaRequerido")
        boolean mfaRequired,
        @JsonProperty("mfaChallengeToken")
        String mfaChallengeToken
) {
    public AuthSessionResponse(String publicId, String email, List<String> roles, String message) {
        this(publicId, email, roles, message, false, false, null);
    }

    public AuthSessionResponse(String publicId, String email, List<String> roles, String message, boolean mustChangePassword) {
        this(publicId, email, roles, message, mustChangePassword, false, null);
    }

    public static AuthSessionResponse mfaRequired(String publicId, String email, List<String> roles, String challengeToken) {
        return new AuthSessionResponse(
                publicId,
                email,
                roles,
                "Autenticacion de segundo factor requerida.",
                false,
                true,
                challengeToken
        );
    }
}
