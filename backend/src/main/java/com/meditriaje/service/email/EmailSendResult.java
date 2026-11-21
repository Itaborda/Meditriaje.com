package com.meditriaje.service.email;

/**
 * Representa el resultado explícito del intento de envío de un correo electrónico.
 * Garantiza visibilidad operativa ante fallos (ADR-015, T3 Plan Post-Auditoría).
 */
public record EmailSendResult(
        boolean success,
        String code,
        String message
) {
    public EmailSendResult {
        code = (code != null && !code.isBlank()) ? code.trim() : (success ? "OK" : "ERROR");
        message = EmailUtil.sanitizeErrorMessage(message, null);
    }

    public static EmailSendResult succeeded() {
        return new EmailSendResult(true, "OK", "Correo enviado exitosamente.");
    }

    public static EmailSendResult simulatedSuccess(String transport) {
        return new EmailSendResult(true, "SIMULADO", "Correo simulado por transporte " + transport + ".");
    }

    public static EmailSendResult failed(String code, String message) {
        return new EmailSendResult(false, code, message);
    }
}
