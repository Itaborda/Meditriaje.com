package com.meditriaje.exception;

/**
 * Se lanza cuando un token de sesión (access o refresh) es nulo, inválido, expirado o revocado.
 * Mapeado a HTTP 401 Unauthorized.
 */
public class InvalidTokenException extends MediTriajeException {

    public InvalidTokenException() {
        super("TOKEN_INVALIDO", "Token de sesion invalido o expirado.");
    }

    public InvalidTokenException(String message) {
        super("TOKEN_INVALIDO", message);
    }
}
