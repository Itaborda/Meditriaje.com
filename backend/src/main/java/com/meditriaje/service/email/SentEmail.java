package com.meditriaje.service.email;

import java.time.Instant;

/**
 * Representación inmutable de un correo electrónico despachado por el servicio.
 */
public record SentEmail(
        String recipient,
        String subject,
        String htmlBody,
        Instant sentAt
) {
}
