package com.meditriaje.exception;

/**
 * Se lanza cuando un recurso solicitado no existe en el sistema.
 * Mapeado a HTTP 404.
 */
public class ResourceNotFoundException extends MediTriajeException {

    public ResourceNotFoundException(String resource) {
        super("RECURSO_NO_ENCONTRADO", "El recurso solicitado no fue encontrado: " + resource);
    }
}
