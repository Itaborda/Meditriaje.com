package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meditriaje.dto.affiliation.AfiliacionResponse;
import com.meditriaje.model.AfiliacionPaciente;
import com.meditriaje.repository.plsql.PackageGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** El repositorio de AFILIACION_PACIENTE delega en PKG_AFFILIATIONS a través de la pasarela. */
class AfiliacionPacienteRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final AfiliacionPacienteRepository repository = new AfiliacionPacienteRepository(gateway);

    @Test
    void guardar_insertaYRecuperaPorId() {
        AfiliacionPaciente nueva = new AfiliacionPaciente(
                null, "af-1", null, "CC", "1001", 2L, "CONTRIBUTIVO", null, null, null, null, null, null, null);
        AfiliacionPaciente guardada = new AfiliacionPaciente(
                10L, "af-1", null, "CC", "1001", 2L, "CONTRIBUTIVO", "COTIZANTE", "ACTIVO",
                null, "CARGA_MASIVA", Instant.now(), Instant.now(), Instant.now());

        when(gateway.execute(eq(AfiliacionPacienteRepository.CREATE_AFFILIATION),
                        argThat(p -> "af-1".equals(p.getValue("p_public_id"))
                                && p.getValue("p_patient_id") == null
                                && p.getValue("p_affiliate_type") == null)))
                .thenReturn(Map.of("o_id", BigDecimal.TEN));
        when(gateway.<AfiliacionPaciente>single(eq(AfiliacionPacienteRepository.GET_AFFILIATION_BY_ID),
                        argThat(p -> Long.valueOf(10L).equals(p.getValue("p_id"))), eq("o_row")))
                .thenReturn(Optional.of(guardada));

        assertThat(repository.guardar(nueva)).isEqualTo(guardada);
    }

    @Test
    void guardar_fallaSiNoSeRecuperaLaFila() {
        AfiliacionPaciente nueva = new AfiliacionPaciente(
                null, "af-1", null, "CC", "1001", 2L, "CONTRIBUTIVO", null, null, null, null, null, null, null);
        when(gateway.execute(eq(AfiliacionPacienteRepository.CREATE_AFFILIATION), any(SqlParameterSource.class)))
                .thenReturn(Map.of());

        assertThatThrownBy(() -> repository.guardar(nueva)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buscarPorIdNuloNoConsultaLaBase() {
        assertThat(repository.buscarPorId(null)).isEmpty();
        verifyNoInteractions(gateway);
    }

    @Test
    void actualizacionesYVistasUsanSusRutinas() {
        AfiliacionResponse resp = new AfiliacionResponse(
                "af-1", "pac-1", "CC", "1001", "Ana Diaz", "EPS037", "NUEVA EPS",
                "CONTRIBUTIVO", "COTIZANTE", "ACTIVO", null, "CARGA_MASIVA", Instant.now());
        when(gateway.<AfiliacionResponse>single(eq(AfiliacionPacienteRepository.GET_AFFILIATION_DETAIL_BY_DOCUMENT),
                        any(SqlParameterSource.class), eq("o_row")))
                .thenReturn(Optional.of(resp));
        when(gateway.<AfiliacionResponse>single(eq(AfiliacionPacienteRepository.GET_AFFILIATION_DETAIL_BY_PATIENT_ID),
                        any(SqlParameterSource.class), eq("o_row")))
                .thenReturn(Optional.of(resp));

        Instant verifAt = Instant.now();
        repository.actualizarAfiliacion(10L, 3L, "SUBSIDIADO", "BENEFICIARIO", "ACTIVO", verifAt);
        repository.vincularPaciente("CC", "1001", 77L);

        verify(gateway).execute(eq(AfiliacionPacienteRepository.UPDATE_AFFILIATION),
                argThat(p -> Long.valueOf(10L).equals(p.getValue("p_id"))
                        && Long.valueOf(3L).equals(p.getValue("p_eps_id"))));
        verify(gateway).execute(eq(AfiliacionPacienteRepository.LINK_PATIENT_AFFILIATION),
                argThat(p -> Long.valueOf(77L).equals(p.getValue("p_patient_id"))));
        assertThat(repository.buscarDetallePorDocumento("CC", "1001")).contains(resp);
        assertThat(repository.buscarDetallePorPacienteId(77L)).contains(resp);
    }
}
