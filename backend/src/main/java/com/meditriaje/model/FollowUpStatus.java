package com.meditriaje.model;

/**
 * Estados del ciclo de vida de un seguimiento post-atención (ADR-015).
 */
public enum FollowUpStatus {
    PENDIENTE,
    COMPLETADO,
    CANCELADO;

    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        for (FollowUpStatus e : values()) {
            if (e.name().equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }
}
