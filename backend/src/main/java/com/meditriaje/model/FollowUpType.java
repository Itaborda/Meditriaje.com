package com.meditriaje.model;

/**
 * Tipos permitidos para las tareas de seguimiento post-atención (ADR-015).
 */
public enum FollowUpType {
    CONTROL_MEDICO,
    EVOLUCION_SINTOMAS,
    EXAMEN_PENDIENTE,
    ADHERENCIA_TRATAMIENTO;

    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        for (FollowUpType t : values()) {
            if (t.name().equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }
}
