package com.meditriaje.model;

import com.meditriaje.exception.InvalidDataException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentStateMachineTest {

    // =========================================================================
    // ESTADOS FINALES (ADR-006)
    // =========================================================================

    @Test
    @DisplayName("esEstadoFinal retorna true para ATENDIDA, CANCELADA, NO_ASISTIO y REPROGRAMADA")
    void isFinalState_finalStates_returnsTrue() {
        assertThat(AppointmentStateMachine.isFinalState(AppointmentStatus.ATENDIDA)).isTrue();
        assertThat(AppointmentStateMachine.isFinalState(AppointmentStatus.CANCELADA)).isTrue();
        assertThat(AppointmentStateMachine.isFinalState(AppointmentStatus.NO_ASISTIO)).isTrue();
        assertThat(AppointmentStateMachine.isFinalState(AppointmentStatus.REPROGRAMADA)).isTrue();
    }

    @Test
    @DisplayName("esEstadoFinal retorna false para PROGRAMADA, CONFIRMADA y null")
    void isFinalState_nonFinalStates_returnsFalse() {
        assertThat(AppointmentStateMachine.isFinalState(AppointmentStatus.PROGRAMADA)).isFalse();
        assertThat(AppointmentStateMachine.isFinalState(AppointmentStatus.CONFIRMADA)).isFalse();
        assertThat(AppointmentStateMachine.isFinalState(null)).isFalse();
    }

    // =========================================================================
    // TRANSICIONES VÁLIDAS DESDE PROGRAMADA (ADR-006, Decisión D2)
    // =========================================================================

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"ATENDIDA", "CANCELADA", "NO_ASISTIO", "REPROGRAMADA"})
    @DisplayName("Transiciones válidas desde PROGRAMADA son permitidas (D2)")
    void isValidTransition_fromScheduled_validTargets_returnsTrue(AppointmentStatus target) {
        assertThat(AppointmentStateMachine.isValidTransition(AppointmentStatus.PROGRAMADA, target)).isTrue();
        assertThatCode(() -> AppointmentStateMachine.validateTransition(AppointmentStatus.PROGRAMADA, target))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"CONFIRMADA", "PROGRAMADA"})
    @DisplayName("PROGRAMADA no puede transicionar a estados reservados ni a sí misma")
    void isValidTransition_fromScheduled_invalidTargets_returnsFalse(AppointmentStatus invalidTarget) {
        assertThat(AppointmentStateMachine.isValidTransition(AppointmentStatus.PROGRAMADA, invalidTarget)).isFalse();
        assertThatThrownBy(() -> AppointmentStateMachine.validateTransition(AppointmentStatus.PROGRAMADA, invalidTarget))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Transicion de estado no permitida de PROGRAMADA a " + invalidTarget + ".");
    }

    @Test
    @DisplayName("PROGRAMADA no puede transicionar a null")
    void isValidTransition_fromScheduled_nullTarget_returnsFalse() {
        assertThat(AppointmentStateMachine.isValidTransition(AppointmentStatus.PROGRAMADA, null)).isFalse();
    }

    // =========================================================================
    // TRANSICIONES DESDE CONFIRMADA (COMPATIBILIDAD LEGACY D2)
    // =========================================================================

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"ATENDIDA", "CANCELADA", "NO_ASISTIO", "REPROGRAMADA"})
    @DisplayName("Transiciones legacy válidas desde CONFIRMADA son permitidas (D2)")
    void isValidTransition_fromConfirmed_validTargets_returnsTrue(AppointmentStatus target) {
        assertThat(AppointmentStateMachine.isValidTransition(AppointmentStatus.CONFIRMADA, target)).isTrue();
        assertThatCode(() -> AppointmentStateMachine.validateTransition(AppointmentStatus.CONFIRMADA, target))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"PROGRAMADA", "CONFIRMADA"})
    @DisplayName("CONFIRMADA no puede retroceder a PROGRAMADA ni a sí misma")
    void isValidTransition_fromConfirmed_invalidTargets_returnsFalse(AppointmentStatus invalidTarget) {
        assertThat(AppointmentStateMachine.isValidTransition(AppointmentStatus.CONFIRMADA, invalidTarget)).isFalse();
        assertThatThrownBy(() -> AppointmentStateMachine.validateTransition(AppointmentStatus.CONFIRMADA, invalidTarget))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Transicion de estado no permitida de CONFIRMADA a " + invalidTarget + ".");
    }

    @Test
    @DisplayName("CONFIRMADA no puede transicionar a null")
    void isValidTransition_fromConfirmed_nullTarget_returnsFalse() {
        assertThat(AppointmentStateMachine.isValidTransition(AppointmentStatus.CONFIRMADA, null)).isFalse();
    }

    // =========================================================================
    // INMUTABILIDAD DE ESTADOS TERMINALES (ADR-006)
    // =========================================================================

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"ATENDIDA", "CANCELADA", "NO_ASISTIO", "REPROGRAMADA"})
    @DisplayName("Estados finales no permiten ninguna transición saliente")
    void isValidTransition_fromFinalStates_noTransitionAllowed(AppointmentStatus finalState) {
        for (AppointmentStatus target : AppointmentStatus.values()) {
            assertThat(AppointmentStateMachine.isValidTransition(finalState, target)).isFalse();
            assertThatThrownBy(() -> AppointmentStateMachine.validateTransition(finalState, target))
                    .isInstanceOf(InvalidDataException.class)
                    .hasMessage("Transicion de estado no permitida de " + finalState + " a " + target + ".");
        }
    }

    // =========================================================================
    // CASOS NULOS O INVÁLIDOS
    // =========================================================================

    @Test
    @DisplayName("Transición con origen nulo es rechazada")
    void validateTransition_nullOrigin_throwsInvalidDataException() {
        assertThat(AppointmentStateMachine.isValidTransition(null, AppointmentStatus.CANCELADA)).isFalse();
        assertThatThrownBy(() -> AppointmentStateMachine.validateTransition(null, AppointmentStatus.CANCELADA))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Transicion de estado no permitida de null a CANCELADA.");
    }

    @Test
    @DisplayName("Transición con destino nulo es rechazada")
    void validateTransition_nullTarget_throwsInvalidDataException() {
        assertThat(AppointmentStateMachine.isValidTransition(AppointmentStatus.PROGRAMADA, null)).isFalse();
        assertThatThrownBy(() -> AppointmentStateMachine.validateTransition(AppointmentStatus.PROGRAMADA, null))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Transicion de estado no permitida de PROGRAMADA a null.");
    }
}
