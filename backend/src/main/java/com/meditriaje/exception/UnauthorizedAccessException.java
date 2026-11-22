package com.meditriaje.exception;

/**
 * Se lanza cuando un usuario autenticado intenta acceder a un recurso
 * al que no tiene autorización. Mapeado a HTTP 403.
 *
 * <p>Distinción clave: autenticado != autorizado.
 * Un paciente puede estar autenticado pero no autorizado a ver datos de otro paciente.</p>
 */
public class UnauthorizedAccessException extends MediTriajeException {

    public UnauthorizedAccessException() {
        this("No tiene permisos para realizar esta operacion.");
    }

    public UnauthorizedAccessException(String message) {
        super("ACCESO_NO_AUTORIZADO", message);
    }
}
