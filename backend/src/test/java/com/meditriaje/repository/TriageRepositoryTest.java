package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meditriaje.dto.triage.SymptomCatalogResponse;
import com.meditriaje.dto.triage.TriageResponse;
import com.meditriaje.model.Triage;
import com.meditriaje.model.TriageSymptomEntry;
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
class TriageRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final TriageRepository repository = new TriageRepository(gateway);

    @Test
    void declaresPkgTriageRoutines() {
        assertThat(TriageRepository.CREATE_TRIAGE.qualifiedName()).isEqualTo("PKG_TRIAGE.create_triage");
        assertThat(TriageRepository.CREATE_TRIAGE.parameters())
                .extracting(ProcedureSpec.Param::name)
                .containsExactly("p_public_id", "p_patient_id", "p_rules_version", "p_priority_level",
                        "p_suggested_route", "p_is_emergency", "p_observations", "o_id");
        assertThat(TriageRepository.GET_TRIAGE_DETAIL.qualifiedName()).isEqualTo("PKG_TRIAGE.get_triage_detail");
        assertThat(TriageRepository.LIST_ACTIVE_SYMPTOM_IDS.qualifiedName())
                .isEqualTo("PKG_TRIAGE.list_active_symptom_ids");
    }

    @Test
    void saveTriage_sendsParamsAndReturnsGeneratedId() {
        Triage triaje = new Triage(
                "triaje-uuid-1", 10L, "v1-prototipo", "III", "CITA_PRESENCIAL", false, "Observacion de prueba");
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(gateway.execute(eq(TriageRepository.CREATE_TRIAGE), params.capture()))
                .thenReturn(Map.of("o_id", new BigDecimal("777")));

        Long id = repository.saveTriage(triaje);

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
    void saveSymptoms_recordsEachSymptom() {
        List<TriageSymptomEntry> sintomas = List.of(
                new TriageSymptomEntry(1L, 101L, BigDecimal.valueOf(12.5), 6),
                new TriageSymptomEntry(1L, 102L, BigDecimal.valueOf(2.0), 3));
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);

        repository.saveSymptoms(1L, sintomas);

        verify(gateway, times(2)).execute(eq(TriageRepository.ADD_TRIAGE_SYMPTOM), params.capture());
        SqlParameterSource first = params.getAllValues().get(0);
        assertThat(first.getValue("p_triage_id")).isEqualTo(1L);
        assertThat(first.getValue("p_symptom_id")).isEqualTo(101L);
        assertThat(first.getValue("p_duration_hours")).isEqualTo(BigDecimal.valueOf(12.5));
        assertThat(first.getValue("p_intensity")).isEqualTo(6);
        assertThat(params.getAllValues().get(1).getValue("p_symptom_id")).isEqualTo(102L);
    }

    @Test
    void saveSymptoms_withEmptyList_doesNotCallPackage() {
        repository.saveSymptoms(1L, List.of());
        verify(gateway, never()).execute(any(ProcedureSpec.class), any(SqlParameterSource.class));
    }

    @Test
    void findByPublicId_groupsTriageRowsWithSymptoms() {
        Instant creado = Instant.parse("2026-10-10T12:00:00Z");
        List<TriageRepository.DetailRow> rows = List.of(
                detalle("FIEBRE", "Fiebre", BigDecimal.valueOf(24.0), 8, false, creado),
                detalle("DOLOR_TORACICO", "Dolor toracico", BigDecimal.valueOf(1.0), 9, true, creado));
        when(gateway.<TriageRepository.DetailRow>list(
                        eq(TriageRepository.GET_TRIAGE_DETAIL), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(rows);

        Optional<TriageResponse> resultado = repository.findByPublicId("triaje-uuid-1");

        assertThat(resultado).isPresent();
        TriageResponse resp = resultado.get();
        assertThat(resp.publicId()).isEqualTo("triaje-uuid-1");
        assertThat(resp.patientPublicId()).isEqualTo("pac-uuid-1");
        assertThat(resp.priorityLevel()).isEqualTo("II");
        assertThat(resp.isEmergency()).isFalse();
        assertThat(resp.createdAt()).isEqualTo(creado);
        assertThat(resp.symptoms()).extracting(s -> s.code()).containsExactly("FIEBRE", "DOLOR_TORACICO");
        assertThat(resp.alarmSymptoms()).containsExactly("DOLOR_TORACICO");
    }

    @Test
    void findByPublicId_triageWithoutSymptoms_returnsEmptyList() {
        when(gateway.<TriageRepository.DetailRow>list(
                        eq(TriageRepository.GET_TRIAGE_DETAIL), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of(detalle(null, null, null, 0, false, null)));

        TriageResponse resp = repository.findByPublicId("triaje-uuid-1").orElseThrow();

        assertThat(resp.symptoms()).isEmpty();
        assertThat(resp.alarmSymptoms()).isEmpty();
    }

    @Test
    void findByPublicId_nonExistent_returnsEmpty() {
        when(gateway.list(eq(TriageRepository.GET_TRIAGE_DETAIL), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of());

        assertThat(repository.findByPublicId("no-existe")).isEmpty();
    }

    @Test
    void findEntityByPublicIdAndById_returnFirstRow() {
        Triage triaje = new Triage(
                1L, "triaje-uuid-1", 10L, "v1-prototipo", "III", "CITA_PRESENCIAL", false, null, Instant.now());
        when(gateway.<Triage>list(eq(TriageRepository.GET_TRIAGE_BY_PUBLIC_ID), any(SqlParameterSource.class),
                        eq("o_row")))
                .thenReturn(List.of(triaje));
        when(gateway.<Triage>list(eq(TriageRepository.GET_TRIAGE_BY_ID), any(SqlParameterSource.class), eq("o_row")))
                .thenReturn(List.of(triaje));

        assertThat(repository.findEntityByPublicId("triaje-uuid-1")).contains(triaje);
        assertThat(repository.findEntityById(1L)).contains(triaje);
    }

    @Test
    void listActiveSymptomCatalog_mapsRow() throws SQLException {
        SymptomCatalogResponse item = new SymptomCatalogResponse("sintoma-uuid-1", "FIEBRE", "Fiebre", "GENERAL", false);
        when(gateway.<SymptomCatalogResponse>list(
                        eq(TriageRepository.LIST_ACTIVE_SYMPTOM_CATALOG), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of(item));

        assertThat(repository.listActiveSymptomCatalog()).containsExactly(item);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("PUBLIC_ID")).thenReturn("sintoma-uuid-1");
        when(rs.getString("CODIGO")).thenReturn("FIEBRE");
        when(rs.getString("NOMBRE")).thenReturn("Fiebre");
        when(rs.getString("CATEGORIA")).thenReturn("GENERAL");
        when(rs.getInt("ES_ALARMA")).thenReturn(0);
        assertThat(TriageRepository.CATALOG_MAPPER.mapRow(rs, 0)).isEqualTo(item);
    }

    @Test
    void getSymptomCodeToIdMap_returnsMapping() {
        when(gateway.<Map.Entry<String, Long>>list(
                        eq(TriageRepository.LIST_ACTIVE_SYMPTOM_IDS), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of(
                        new AbstractMap.SimpleImmutableEntry<>("FIEBRE", 10L),
                        new AbstractMap.SimpleImmutableEntry<>("TOS", 20L)));

        assertThat(repository.getSymptomCodeToIdMap())
                .containsEntry("FIEBRE", 10L)
                .containsEntry("TOS", 20L);
    }

    @Test
    void detailMapper_readsCursorColumns() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        Instant creado = Instant.parse("2026-10-10T12:00:00Z");
        when(rs.getString("TRIAJE_PUBLIC_ID")).thenReturn("triaje-uuid-1");
        when(rs.getString("RUTA_SUGERIDA")).thenReturn("ATENCION_PRIORITARIA");
        when(rs.getInt("ES_EMERGENCIA")).thenReturn(1);
        when(rs.getTimestamp("CREATED_AT")).thenReturn(Timestamp.from(creado));
        when(rs.getString("SINTOMA_CODIGO")).thenReturn("FIEBRE");
        when(rs.getInt("ES_ALARMA")).thenReturn(1);

        TriageRepository.DetailRow row = TriageRepository.DETAIL_MAPPER.mapRow(rs, 0);

        assertThat(row.triagePublicId()).isEqualTo("triaje-uuid-1");
        assertThat(row.isEmergency()).isTrue();
        assertThat(row.createdAt()).isEqualTo(creado);
        assertThat(row.symptomCode()).isEqualTo("FIEBRE");
        assertThat(row.isAlarm()).isTrue();
    }

    private static TriageRepository.DetailRow detalle(
            String codigo, String nombre, BigDecimal duracion, int intensidad, boolean alarma, Instant creado) {
        return new TriageRepository.DetailRow("triaje-uuid-1", "pac-uuid-1", "v1-prototipo", "II",
                "ATENCION_PRIORITARIA", false, "Fiebre moderada", creado, codigo, nombre, duracion, intensidad,
                alarma);
    }
}
