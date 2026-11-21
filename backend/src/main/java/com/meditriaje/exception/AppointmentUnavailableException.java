package com.meditriaje.exception;

/**
 * Se lanza cuando un horario de cita ya no está disponible (doble reserva).
 * Mapeado a HTTP 409 Conflict.
 */
public class AppointmentUnavailableException extends MediTriajeException {

    public AppointmentUnavailableException() {
        this("El horario solicitado ya no esta disponible.");
    }

    public AppointmentUnavailableException(String message) {
        super("CITA_NO_DISPONIBLE", message);
    }
}
