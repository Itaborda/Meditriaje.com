package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meditriaje.dto.triage.CatalogoSintomaResponse;
import com.meditriaje.dto.triage.TriajeResponse;
import com.meditriaje.model.Triaje;
import com.meditriaje.model.TriajeSintoma;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** El repositorio de triajes no contiene SQL: declara las rutinas de PKG_TRIAGE y delega en la pasarela. */
class TriajeRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final TriajeRepository repository = new TriajeRepository(gateway);

    @Test
    void declaraLasRutinasDePkgTriage() {
        assertThat(TriajeRepository.CREATE_TRIAGE.qualifiedName()).isEqualTo("PKG_TRIAGE.create_triage");
        assertThat(TriajeRepository.CREATE_TRIAGE.parameters())
                .extracting(ProcedureSpec.Param::name)
                .containsExactly("p_public_id", "p_patient_id", "p_rules_version", "p_priority_level",
                        "p_suggested_route", "p_is_emergency", "p_observations", "o_id");
        assertThat(TriajeRepository.GET_TRIAGE_DETAIL.qualifiedName()).isEqualTo("PKG_TRIAGE.get_triage_detail");
        assertThat(TriajeRepository.LIST_ACTIVE_SYMPTOM_IDS.qualifiedName())
                .isEqualTo("PKG_TRIAGE.list_active_symptom_ids");
    }

    @Test
    void guardarTriaje_enviaParametrosYRetornaIdGenerado() {
        Triaje triaje = new Triaje(
                "triaje-uuid-1", 10L, "v1-prototipo", "III", "CITA_PRESENCIAL", false, "Observacion de prueba");
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(gateway.execute(eq(TriajeRepository.CREATE_TRIAGE), params.capture()))
                .thenReturn(Map.of("o_id", new BigDecimal("777")));

        Long id = repository.guardarTriaje(triaje);

        assertThat(id).isEqualTo(777L);
        SqlParameterSource sent = params.getValue();
        assertThat(sent.getValue("p_public_id")).isEqualTo("triaje-uuid-1");
        assertThat(sent.getValue("p_patient_id")).isEqualTo(10L);
        assertThat(sent.getValue("p_rules_version")).isEqualTo("v1-prototipo");
        assertThat(sent.getValue("p_priority_level")).isEqualTo("III");
        assertThat(sent.getValue("p_suggested_route")).isEqualTo("CITA_PRESENCIAL");
        assertThat(sent.getValue("p_is_emergency")).isEqualTo(0);
        assertThat(sent.getValue("p_observations")).isEqualTo("Observacion de prueba");
    }

    @Test
    void guardarSintomas_registraCadaSintoma() {
        List<TriajeSintoma> sintomas = List.of(
                new TriajeSintoma(1L, 101L, BigDecimal.valueOf(12.5), 6),
                new TriajeSintoma(1L, 102L, BigDecimal.valueOf(2.0), 3));
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);

        repository.guardarSintomas(1L, sintomas);

        verify(gateway, times(2)).execute(eq(TriajeRepository.ADD_TRIAGE_SYMPTOM), params.capture());
        SqlParameterSource first = params.getAllValues().get(0);
        assertThat(first.getValue("p_triage_id")).isEqualTo(1L);
        assertThat(first.getValue("p_symptom_id")).isEqualTo(101L);
        assertThat(first.getValue("p_duration_hours")).isEqualTo(BigDecimal.valueOf(12.5));
        assertThat(first.getValue("p_intensity")).isEqualTo(6);
        assertThat(params.getAllValues().get(1).getValue("p_symptom_id")).isEqualTo(102L);
    }

    @Test
    void guardarSintomas_conListaVacia_noLlamaAlPaquete() {
        repository.guardarSintomas(1L, List.of());
        verify(gateway, never()).execute(any(ProcedureSpec.class), any(SqlParameterSource.class));
    }

    @Test
    void buscarPorPublicId_agrupaLasFilasDelTriajeConSusSintomas() {
        Instant creado = Instant.parse("2026-10-10T12:00:00Z");
        List<TriajeRepository.DetailRow> rows = List.of(
                detalle("FIEBRE", "Fiebre", BigDecimal.valueOf(24.0), 8, false, creado),
                detalle("DOLOR_TORACICO", "Dolor toracico", BigDecimal.valueOf(1.0), 9, true, creado));
        when(gateway.<TriajeRepository.DetailRow>list(
                        eq(TriajeRepository.GET_TRIAGE_DETAIL), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(rows);

        Optional<TriajeResponse> resultado = repository.buscarPorPublicId("triaje-uuid-1");

        assertThat(resultado).isPresent();
        TriajeResponse resp = resultado.get();
        assertThat(resp.publicId()).isEqualTo("triaje-uuid-1");
        assertThat(resp.pacientePublicId()).isEqualTo("pac-uuid-1");
        assertThat(resp.nivelPrioridad()).isEqualTo("II");
        assertThat(resp.esEmergencia()).isFalse();
        assertThat(resp.createdAt()).isEqualTo(creado);
        assertThat(resp.sintomas()).extracting(s -> s.codigo()).containsExactly("FIEBRE", "DOLOR_TORACICO");
        assertThat(resp.sintomasAlarma()).containsExactly("DOLOR_TORACICO");
    }

    @Test
    void buscarPorPublicId_triajeSinSintomas_retornaListaVacia() {
        when(gateway.<TriajeRepository.DetailRow>list(
                        eq(TriajeRepository.GET_TRIAGE_DETAIL), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of(detalle(null, null, null, 0, false, null)));

        TriajeResponse resp = repository.buscarPorPublicId("triaje-uuid-1").orElseThrow();

        assertThat(resp.sintomas()).isEmpty();
        assertThat(resp.sintomasAlarma()).isEmpty();
    }

    @Test
    void buscarPorPublicId_inexistente_retornaEmpty() {
        when(gateway.list(eq(TriajeRepository.GET_TRIAGE_DETAIL), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of());

        assertThat(repository.buscarPorPublicId("no-existe")).isEmpty();
    }

    @Test
    void buscarEntidadPorPublicIdYPorId_retornanLaPrimeraFila() {
        Triaje triaje = new Triaje(
                1L, "triaje-uuid-1", 10L, "v1-prototipo", "III", "CITA_PRESENCIAL", false, null, Instant.now());
        when(gateway.<Triaje>list(eq(TriajeRepository.GET_TRIAGE_BY_PUBLIC_ID), any(SqlParameterSource.class),
                        eq("o_row")))
                .thenReturn(List.of(triaje));
        when(gateway.<Triaje>list(eq(TriajeRepository.GET_TRIAGE_BY_ID), any(SqlParameterSource.class), eq("o_row")))
                .thenReturn(List.of(triaje));

        assertThat(repository.buscarEntidadPorPublicId("triaje-uuid-1")).contains(triaje);
        assertThat(repository.buscarEntidadPorId(1L)).contains(triaje);
    }

    @Test
    void listarCatalogoSintomasActivos_mapeaLaFila() throws SQLException {
        CatalogoSintomaResponse item = new CatalogoSintomaResponse("sintoma-uuid-1", "FIEBRE", "Fiebre", "GENERAL", false);
        when(gateway.<CatalogoSintomaResponse>list(
                        eq(TriajeRepository.LIST_ACTIVE_SYMPTOM_CATALOG), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of(item));

        assertThat(repository.listarCatalogoSintomasActivos()).containsExactly(item);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("PUBLIC_ID")).thenReturn("sintoma-uuid-1");
        when(rs.getString("CODIGO")).thenReturn("FIEBRE");
        when(rs.getString("NOMBRE")).thenReturn("Fiebre");
        when(rs.getString("CATEGORIA")).thenReturn("GENERAL");
        when(rs.getInt("ES_ALARMA")).thenReturn(0);
        assertThat(TriajeRepository.CATALOG_MAPPER.mapRow(rs, 0)).isEqualTo(item);
    }

    @Test
    void obtenerMapaCodigoAIdSintomas_retornaMapeo() {
        when(gateway.<Map.Entry<String, Long>>list(
                        eq(TriajeRepository.LIST_ACTIVE_SYMPTOM_IDS), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of(
                        new AbstractMap.SimpleImmutableEntry<>("FIEBRE", 10L),
                        new AbstractMap.SimpleImmutableEntry<>("TOS", 20L)));

        assertThat(repository.obtenerMapaCodigoAIdSintomas())
                .containsEntry("FIEBRE", 10L)
                .containsEntry("TOS", 20L);
    }

    @Test
    void detailMapper_leeColumnasDelCursor() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        Instant creado = Instant.parse("2026-10-10T12:00:00Z");
        when(rs.getString("TRIAJE_PUBLIC_ID")).thenReturn("triaje-uuid-1");
        when(rs.getString("RUTA_SUGERIDA")).thenReturn("ATENCION_PRIORITARIA");
        when(rs.getInt("ES_EMERGENCIA")).thenReturn(1);
        when(rs.getTimestamp("CREATED_AT")).thenReturn(Timestamp.from(creado));
        when(rs.getString("SINTOMA_CODIGO")).thenReturn("FIEBRE");
        when(rs.getInt("ES_ALARMA")).thenReturn(1);

        TriajeRepository.DetailRow row = TriajeRepository.DETAIL_MAPPER.mapRow(rs, 0);

        assertThat(row.triajePublicId()).isEqualTo("triaje-uuid-1");
        assertThat(row.esEmergencia()).isTrue();
        assertThat(row.createdAt()).isEqualTo(creado);
        assertThat(row.sintomaCodigo()).isEqualTo("FIEBRE");
        assertThat(row.esAlarma()).isTrue();
    }

    private static TriajeRepository.DetailRow detalle(
            String codigo, String nombre, BigDecimal duracion, int intensidad, boolean alarma, Instant creado) {
        return new TriajeRepository.DetailRow("triaje-uuid-1", "pac-uuid-1", "v1-prototipo", "II",
                "ATENCION_PRIORITARIA", false, "Fiebre moderada", creado, codigo, nombre, duracion, intensidad,
                alarma);
    }
}
