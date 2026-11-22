package com.meditriaje.model;

import java.time.Instant;

/**
 * Registro inmutable de una notificación o recordatorio de cita médica.
 */
public record AppointmentReminder(
        Long id,
        String publicId,
        Long appointmentId,
        Long patientId,
        String type,
        String channel,
        String recipient,
        String deliveryStatus,
        String errorMessage,
        Instant sentAt
) {
    public AppointmentReminder {
        if (publicId == null || publicId.isBlank()) {
            throw new IllegalArgumentException("publicId no puede ser nulo ni vacío");
        }
        if (appointmentId == null) {
            throw new IllegalArgumentException("citaId no puede ser nulo");
        }
        if (patientId == null) {
            throw new IllegalArgumentException("pacienteId no puede ser nulo");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("tipo no puede ser nulo ni vacío");
        }
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("destinatario no puede ser nulo ni vacío");
        }
        if (channel == null) {
            channel = "EMAIL";
        }
        if (deliveryStatus == null) {
            deliveryStatus = "ENVIADO";
        }
    }
}
