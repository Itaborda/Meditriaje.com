package com.meditriaje.exception;

/**
 * Se lanza cuando los datos de entrada no cumplen las reglas de negocio.
 * Mapeado a HTTP 400.
 */
public class InvalidDataException extends MediTriajeException {

    public InvalidDataException(String detail) {
        super("DATOS_INVALIDOS", detail);
    }
}
