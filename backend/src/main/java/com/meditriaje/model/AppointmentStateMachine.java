package com.meditriaje.model;

import com.meditriaje.exception.InvalidDataException;

import java.util.Map;
import java.util.Set;

/**
 * Máquina de estados formal para el ciclo de vida de una cita médica (ADR-006, Decisión D2).
 * Flujo activo simplificado: PROGRAMADA -> ATENDIDA | CANCELADA | NO_ASISTIO.
 * Los estados REPROGRAMADA y CONFIRMADA quedan reservados (no se utilizan en el flujo estándar).
 * Por compatibilidad con registros preexistentes o legacy, CONFIRMADA permite transicionar a:
 * ATENDIDA, CANCELADA o NO_ASISTIO.
 */
public final class AppointmentStateMachine {

    private static final Set<AppointmentStatus> FINAL_STATES = Set.of(
            AppointmentStatus.ATENDIDA,
            AppointmentStatus.CANCELADA,
            AppointmentStatus.NO_ASISTIO,
            AppointmentStatus.REPROGRAMADA
    );

    private static final Map<AppointmentStatus, Set<AppointmentStatus>> ALLOWED_TRANSITIONS = Map.of(
            AppointmentStatus.PROGRAMADA, Set.of(
                    AppointmentStatus.ATENDIDA,
                    AppointmentStatus.CANCELADA,
                    AppointmentStatus.NO_ASISTIO,
                    AppointmentStatus.REPROGRAMADA
            ),
            AppointmentStatus.CONFIRMADA, Set.of(
                    AppointmentStatus.ATENDIDA,
                    AppointmentStatus.CANCELADA,
                    AppointmentStatus.NO_ASISTIO,
                    AppointmentStatus.REPROGRAMADA
            )
    );

    private AppointmentStateMachine() {
    }

    /**
     * Determina si un estado es terminal/final (inmutable sin transiciones salientes permitidas).
     *
     * @param estado Estado a evaluar.
     * @return {@code true} si el estado es final (ATENDIDA, CANCELADA, NO_ASISTIO, REPROGRAMADA).
     */
    public static boolean isFinalState(AppointmentStatus status) {
        if (status == null) {
            return false;
        }
        return FINAL_STATES.contains(status);
    }

    /**
     * Evalúa si una transición entre dos estados de cita es permitida por las reglas de negocio.
     *
     * @param origen  Estado actual de la cita.
     * @param destino Estado objetivo al que se desea transicionar.
     * @return {@code true} si la transición es válida, {@code false} en caso contrario.
     */
    public static boolean isValidTransition(AppointmentStatus from, AppointmentStatus to) {
        if (from == null || to == null) {
            return false;
        }
        Set<AppointmentStatus> validTargets = ALLOWED_TRANSITIONS.get(from);
        return validTargets != null && validTargets.contains(to);
    }

    /**
     * Valida la transición de estado arrojando {@link DatosInvalidosException} si la transición no es permitida.
     *
     * @param origen  Estado actual de la cita.
     * @param destino Estado objetivo al que se desea transicionar.
     * @throws DatosInvalidosException si la transición de estado no es válida.
     */
    public static void validateTransition(AppointmentStatus from, AppointmentStatus to) {
        if (!isValidTransition(from, to)) {
            throw new InvalidDataException("Transicion de estado no permitida de " + from + " a " + to + ".");
        }
    }
}
