package com.meditriaje.model;

import java.util.Objects;

/**
 * Representa un evento inmutable registrado en la bitácora de auditoría.
 *
 * <p><strong>Regla de privacidad médica (ADR-011):</strong> Esta estructura no contiene
 * ni acepta campos de contenido clínico (diagnósticos, evolución, signos vitales, medicamentos)
 * ni credenciales/secretos. Solo almacena metadatos del evento.</p>
 */
public record AuditEvent(
        Long userId,
        AuditableAction action,
        String resourceType,
        String resourcePublicId,
        AuditResult result,
        String sourceIp
) {
    public AuditEvent {
        Objects.requireNonNull(action, "La accion auditable no puede ser nula");
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("El tipo de recurso es obligatorio");
        }
        Objects.requireNonNull(result, "El resultado de auditoria no puede ser nulo");
        if (sourceIp == null || sourceIp.isBlank()) {
            throw new IllegalArgumentException("La IP de origen es obligatoria");
        }
    }
}
