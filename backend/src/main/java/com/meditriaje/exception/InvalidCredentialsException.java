package com.meditriaje.exception;

/**
 * Se lanza cuando la autenticación falla (credenciales incorrectas o cuenta bloqueada).
 * Mapeado a HTTP 401 Unauthorized.
 * Nunca revela si el correo existe o no en el sistema (HU-01).
 */
public class InvalidCredentialsException extends MediTriajeException {

    public InvalidCredentialsException() {
        super("CREDENCIALES_INVALIDAS", "Credenciales invalidas.");
    }

    public InvalidCredentialsException(String message) {
        super("CREDENCIALES_INVALIDAS", message);
    }
}
