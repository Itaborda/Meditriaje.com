package com.meditriaje.exception;

/**
 * Excepción lanzada cuando una dirección IP o cliente supera el límite de solicitudes permitidas (HTTP 429).
 */
public class RateLimitExceededException extends MediTriajeException {

    public RateLimitExceededException(String message) {
        super("LIMITE_PETICIONES_EXCEDIDO", message);
    }
}
