package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meditriaje.dto.allergy.AlergiaResponse;
import com.meditriaje.model.Alergia;
import com.meditriaje.repository.plsql.PackageGateway;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** El repositorio de alergias delega en PKG_PATIENTS a través de la pasarela. */
class AlergiaRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final AlergiaRepository alergiaRepository = new AlergiaRepository(gateway);

    @Test
    @DisplayName("listarPorPacienteId: por defecto pide solo las activas")
    void listarPorPacienteId_retornaLista() {
        Alergia alergia = new Alergia(1L, "ale-uuid-1", 10L, "Penicilina", "Anafilaxia", "GRAVE", "ACTIVA",
                "PROFESIONAL", 1L, null, null, null, null, Instant.now(), Instant.now());
        when(gateway.<Alergia>list(eq(AlergiaRepository.LIST_BY_PATIENT_ID),
                        argThat(p -> Long.valueOf(10L).equals(p.getValue("p_patient_id"))
                                && Integer.valueOf(1).equals(p.getValue("p_only_active"))), eq("o_rows")))
                .thenReturn(List.of(alergia));

        List<Alergia> resultado = alergiaRepository.listarPorPacienteId(10L);

        assertThat(resultado).containsExactly(alergia);
    }

    @Test
    @DisplayName("listarPorPacienteId: con id nulo retorna lista vacía sin consultar la base")
    void listarPorPacienteId_idNulo() {
        assertThat(alergiaRepository.listarPorPacienteId(null)).isEmpty();
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("listarPorPacientePublicId: envía la bandera de inactivas")
    void listarPorPacientePublicId_exito() {
        AlergiaResponse response = new AlergiaResponse("ale-uuid-1", "pac-uuid-10", "Ibuprofeno", "Erupcion",
                "MODERADA", "ACTIVA", "PACIENTE", true, null, Instant.now(), null, null);
        when(gateway.<AlergiaResponse>list(eq(AlergiaRepository.LIST_BY_PATIENT_PUBLIC_ID),
                        argThat(p -> "pac-uuid-10".equals(p.getValue("p_patient_public_id"))
                                && Integer.valueOf(1).equals(p.getValue("p_include_inactive"))), eq("o_rows")))
                .thenReturn(List.of(response));

        assertThat(alergiaRepository.listarPorPacientePublicId("pac-uuid-10", true)).containsExactly(response);
    }

    @Test
    @DisplayName("buscarEntidadPorPublicId y buscarPorPublicId usan rutinas distintas")
    void buscarPorPublicId_exito() {
        Alergia alergia = new Alergia(1L, "ale-uuid-1", 10L, "Dipirona", "Hipotension", "GRAVE", "ACTIVA",
                "PROFESIONAL", 2L, null, null, null, null, Instant.now(), Instant.now());
        AlergiaResponse response = new AlergiaResponse("ale-uuid-1", "pac-uuid-10", "Dipirona", "Hipotension",
                "GRAVE", "ACTIVA", "PROFESIONAL", false, null, Instant.now(), null, null);
        when(gateway.<Alergia>single(eq(AlergiaRepository.GET_BY_PUBLIC_ID), any(SqlParameterSource.class),
                        eq("o_row")))
                .thenReturn(Optional.of(alergia));
        when(gateway.<AlergiaResponse>single(eq(AlergiaRepository.GET_VIEW_BY_PUBLIC_ID),
                        any(SqlParameterSource.class), eq("o_row")))
                .thenReturn(Optional.of(response));

        assertThat(alergiaRepository.buscarEntidadPorPublicId("ale-uuid-1")).contains(alergia);
        assertThat(alergiaRepository.buscarPorPublicId("ale-uuid-1")).contains(response);
    }

    @Test
    @DisplayName("existeActivaPorSustancia: true si el conteo es mayor que cero")
    void existeActivaPorSustancia_true() {
        when(gateway.<Object>callFunction(eq(AlergiaRepository.COUNT_ACTIVE_BY_SUBSTANCE),
                        any(SqlParameterSource.class)))
                .thenReturn(BigDecimal.ONE);

        assertThat(alergiaRepository.existeActivaPorSustancia(10L, "Penicilina")).isTrue();
        assertThat(alergiaRepository.existeActivaPorSustancia(10L, " ")).isFalse();
    }

    @Test
    @DisplayName("crear: recorta la sustancia, aplica valores por defecto y devuelve el ID")
    void crear_exito() {
        when(gateway.execute(eq(AlergiaRepository.CREATE_ALLERGY),
                        argThat(p -> "Ibuprofeno".equals(p.getValue("p_substance"))
                                && "ACTIVA".equals(p.getValue("p_status"))
                                && "PROFESIONAL".equals(p.getValue("p_origin")))))
                .thenReturn(Map.of("o_id", new BigDecimal("55")));

        Alergia nueva = new Alergia(null, 10L, " Ibuprofeno ", "Erupcion", "MODERADA", null);

        assertThat(alergiaRepository.crear(nueva)).isEqualTo(55L);
    }

    @Test
    @DisplayName("inactivar: devuelve las filas afectadas")
    void inactivar_exito() {
        Instant inactInstant = Instant.now();
        when(gateway.execute(eq(AlergiaRepository.INACTIVATE_ALLERGY),
                        argThat(p -> Long.valueOf(55L).equals(p.getValue("p_id"))
                                && "Prueba negativa".equals(p.getValue("p_reason"))
                                && Timestamp.from(inactInstant).equals(p.getValue("p_inactivated_at")))))
                .thenReturn(Map.of("o_affected", BigDecimal.ONE));

        assertThat(alergiaRepository.inactivar(55L, 99L, "Prueba negativa ", inactInstant)).isEqualTo(1);
    }
}
