package com.meditriaje.exception;

/**
 * Se lanza ante conflictos de estado o incompatibilidad operacional en recursos (HTTP 409 Conflict).
 * Ejemplo: Intentar cancelar o marcar inasistencia en una cita con atención clínica ya vinculada.
 */
public class OperationConflictException extends MediTriajeException {

    public OperationConflictException(String message) {
        super("CONFLICTO_OPERACION", message);
    }
}
