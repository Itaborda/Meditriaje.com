package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meditriaje.dto.allergy.AllergyResponse;
import com.meditriaje.model.Allergy;
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
class AllergyRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final AllergyRepository alergiaRepository = new AllergyRepository(gateway);

    @Test
    @DisplayName("listarPorPacienteId: por defecto pide solo las activas")
    void listByPatientId_returnsList() {
        Allergy alergia = new Allergy(1L, "ale-uuid-1", 10L, "Penicilina", "Anafilaxia", "GRAVE", "ACTIVA",
                "PROFESIONAL", 1L, null, null, null, null, Instant.now(), Instant.now());
        when(gateway.<Allergy>list(eq(AllergyRepository.LIST_BY_PATIENT_ID),
                        argThat(p -> Long.valueOf(10L).equals(p.getValue("p_patient_id"))
                                && Integer.valueOf(1).equals(p.getValue("p_only_active"))), eq("o_rows")))
                .thenReturn(List.of(alergia));

        List<Allergy> resultado = alergiaRepository.listByPatientId(10L);

        assertThat(resultado).containsExactly(alergia);
    }

    @Test
    @DisplayName("listarPorPacienteId: con id nulo retorna lista vacía sin consultar la base")
    void listByPatientId_nullId() {
        assertThat(alergiaRepository.listByPatientId(null)).isEmpty();
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("listarPorPacientePublicId: envía la bandera de inactivas")
    void listByPatientPublicId_success() {
        AllergyResponse response = new AllergyResponse("ale-uuid-1", "pac-uuid-10", "Ibuprofeno", "Erupcion",
                "MODERADA", "ACTIVA", "PACIENTE", true, null, Instant.now(), null, null);
        when(gateway.<AllergyResponse>list(eq(AllergyRepository.LIST_BY_PATIENT_PUBLIC_ID),
                        argThat(p -> "pac-uuid-10".equals(p.getValue("p_patient_public_id"))
                                && Integer.valueOf(1).equals(p.getValue("p_include_inactive"))), eq("o_rows")))
                .thenReturn(List.of(response));

        assertThat(alergiaRepository.listByPatientPublicId("pac-uuid-10", true)).containsExactly(response);
    }

    @Test
    @DisplayName("buscarEntidadPorPublicId y buscarPorPublicId usan rutinas distintas")
    void findByPublicId_success() {
        Allergy alergia = new Allergy(1L, "ale-uuid-1", 10L, "Dipirona", "Hipotension", "GRAVE", "ACTIVA",
                "PROFESIONAL", 2L, null, null, null, null, Instant.now(), Instant.now());
        AllergyResponse response = new AllergyResponse("ale-uuid-1", "pac-uuid-10", "Dipirona", "Hipotension",
                "GRAVE", "ACTIVA", "PROFESIONAL", false, null, Instant.now(), null, null);
        when(gateway.<Allergy>single(eq(AllergyRepository.GET_BY_PUBLIC_ID), any(SqlParameterSource.class),
                        eq("o_row")))
                .thenReturn(Optional.of(alergia));
        when(gateway.<AllergyResponse>single(eq(AllergyRepository.GET_VIEW_BY_PUBLIC_ID),
                        any(SqlParameterSource.class), eq("o_row")))
                .thenReturn(Optional.of(response));

        assertThat(alergiaRepository.findEntityByPublicId("ale-uuid-1")).contains(alergia);
        assertThat(alergiaRepository.findByPublicId("ale-uuid-1")).contains(response);
    }

    @Test
    @DisplayName("existeActivaPorSustancia: true si el conteo es mayor que cero")
    void existsActiveBySubstance_true() {
        when(gateway.<Object>callFunction(eq(AllergyRepository.COUNT_ACTIVE_BY_SUBSTANCE),
                        any(SqlParameterSource.class)))
                .thenReturn(BigDecimal.ONE);

        assertThat(alergiaRepository.existsActiveBySubstance(10L, "Penicilina")).isTrue();
        assertThat(alergiaRepository.existsActiveBySubstance(10L, " ")).isFalse();
    }

    @Test
    @DisplayName("crear: recorta la sustancia, aplica valores por defecto y devuelve el ID")
    void create_success() {
        when(gateway.execute(eq(AllergyRepository.CREATE_ALLERGY),
                        argThat(p -> "Ibuprofeno".equals(p.getValue("p_substance"))
                                && "ACTIVA".equals(p.getValue("p_status"))
                                && "PROFESIONAL".equals(p.getValue("p_origin")))))
                .thenReturn(Map.of("o_id", new BigDecimal("55")));

        Allergy nueva = new Allergy(null, 10L, " Ibuprofeno ", "Erupcion", "MODERADA", null);

        assertThat(alergiaRepository.create(nueva)).isEqualTo(55L);
    }

    @Test
    @DisplayName("inactivar: devuelve las filas afectadas")
    void inactivate_success() {
        Instant inactInstant = Instant.now();
        when(gateway.execute(eq(AllergyRepository.INACTIVATE_ALLERGY),
                        argThat(p -> Long.valueOf(55L).equals(p.getValue("p_id"))
                                && "Prueba negativa".equals(p.getValue("p_reason"))
                                && Timestamp.from(inactInstant).equals(p.getValue("p_inactivated_at")))))
                .thenReturn(Map.of("o_affected", BigDecimal.ONE));

        assertThat(alergiaRepository.inactivate(55L, 99L, "Prueba negativa ", inactInstant)).isEqualTo(1);
    }
}
