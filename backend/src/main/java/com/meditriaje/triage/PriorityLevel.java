package com.meditriaje.triage;

/**
 * Nivel de prioridad de triaje (ADR-009). El orden de declaración es el orden de urgencia:
 * {@code I} es el más urgente y {@code V} el menos urgente.
 */
public enum PriorityLevel {
    I(SuggestedRoute.URGENCIAS),
    II(SuggestedRoute.ATENCION_PRIORITARIA),
    III(SuggestedRoute.CITA_PRESENCIAL),
    IV(SuggestedRoute.CITA_TELEMEDICINA),
    V(SuggestedRoute.CONSULTA_PROGRAMADA);

    private final SuggestedRoute route;

    PriorityLevel(SuggestedRoute route) {
        this.route = route;
    }

    /** Mapeo nivel → ruta (prototipo, no validado clínicamente). */
    public SuggestedRoute route() {
        return route;
    }

    /** Devuelve el más urgente de los dos niveles. */
    public static PriorityLevel mostUrgent(PriorityLevel first, PriorityLevel second) {
        return first.ordinal() <= second.ordinal() ? first : second;
    }
}
