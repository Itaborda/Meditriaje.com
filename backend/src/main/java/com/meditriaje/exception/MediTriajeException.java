package com.meditriaje.exception;

/**
 * Excepción base de dominio. Todas las excepciones de negocio la extienden.
 */
public abstract class MediTriajeException extends RuntimeException {

    private final String code;

    protected MediTriajeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
