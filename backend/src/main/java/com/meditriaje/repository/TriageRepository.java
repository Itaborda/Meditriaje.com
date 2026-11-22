package com.meditriaje.repository;

import com.meditriaje.dto.triage.SymptomCatalogResponse;
import com.meditriaje.dto.triage.SymptomItemResponse;
import com.meditriaje.dto.triage.TriageResponse;
import com.meditriaje.model.Triage;
import com.meditriaje.model.TriageSymptomEntry;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import com.meditriaje.triage.RuleBasedTriageEngine;
import com.meditriaje.triage.SuggestedRoute;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Persistencia y consulta inmutable de triajes clínicos (ADR-001, ADR-009, HU-02) a través de {@code PKG_TRIAGE}.
 * No contiene SQL.
 */
@Repository
public class TriageRepository {

    static final String PACKAGE = "PKG_TRIAGE";

    static final RowMapper<Triage> TRIAGE_MAPPER = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        return new Triage(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("PACIENTE_ID"),
                rs.getString("VERSION_REGLAS"),
                rs.getString("NIVEL_PRIORIDAD"),
                rs.getString("RUTA_SUGERIDA"),
                rs.getInt("ES_EMERGENCIA") == 1,
                rs.getString("OBSERVACIONES"),
                tsCreated != null ? tsCreated.toInstant() : null);
    };

    /** Fila plana del triaje consolidado: datos del triaje repetidos y, si existe, un síntoma. */
    record DetailRow(
            String triagePublicId,
            String patientPublicId,
            String rulesVersion,
            String priorityLevel,
            String suggestedRoute,
            boolean isEmergency,
            String notes,
            Instant createdAt,
            String symptomCode,
            String symptomName,
            BigDecimal durationHours,
            int intensity,
            boolean isAlarm) {}

    static final RowMapper<DetailRow> DETAIL_MAPPER = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        return new DetailRow(
                rs.getString("TRIAJE_PUBLIC_ID"),
                rs.getString("PACIENTE_PUBLIC_ID"),
                rs.getString("VERSION_REGLAS"),
                rs.getString("NIVEL_PRIORIDAD"),
                rs.getString("RUTA_SUGERIDA"),
                rs.getInt("ES_EMERGENCIA") == 1,
                rs.getString("OBSERVACIONES"),
                tsCreated != null ? tsCreated.toInstant() : null,
                rs.getString("SINTOMA_CODIGO"),
                rs.getString("SINTOMA_NOMBRE"),
                rs.getBigDecimal("DURACION_HORAS"),
                rs.getInt("INTENSIDAD"),
                rs.getInt("ES_ALARMA") == 1);
    };

    static final RowMapper<SymptomCatalogResponse> CATALOG_MAPPER = (rs, rowNum) -> new SymptomCatalogResponse(
            rs.getString("PUBLIC_ID"),
            rs.getString("CODIGO"),
            rs.getString("NOMBRE"),
            rs.getString("CATEGORIA"),
            rs.getInt("ES_ALARMA") == 1);

    static final RowMapper<Map.Entry<String, Long>> SYMPTOM_ID_MAPPER =
            (rs, rowNum) -> new AbstractMap.SimpleImmutableEntry<>(rs.getString("CODIGO"), rs.getLong("ID"));

    static final ProcedureSpec CREATE_TRIAGE = ProcedureSpec.procedure(PACKAGE, "create_triage")
            .in("p_public_id", Types.VARCHAR)
            .in("p_patient_id", Types.NUMERIC)
            .in("p_rules_version", Types.VARCHAR)
            .in("p_priority_level", Types.VARCHAR)
            .in("p_suggested_route", Types.VARCHAR)
            .in("p_is_emergency", Types.NUMERIC)
            .in("p_observations", Types.VARCHAR)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec ADD_TRIAGE_SYMPTOM = ProcedureSpec.procedure(PACKAGE, "add_triage_symptom")
            .in("p_triage_id", Types.NUMERIC)
            .in("p_symptom_id", Types.NUMERIC)
            .in("p_duration_hours", Types.NUMERIC)
            .in("p_intensity", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_TRIAGE_DETAIL = ProcedureSpec.procedure(PACKAGE, "get_triage_detail")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_rows", DETAIL_MAPPER)
            .build();

    static final ProcedureSpec GET_TRIAGE_BY_PUBLIC_ID = ProcedureSpec.procedure(PACKAGE, "get_triage_by_public_id")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_row", TRIAGE_MAPPER)
            .build();

    static final ProcedureSpec GET_TRIAGE_BY_ID = ProcedureSpec.procedure(PACKAGE, "get_triage_by_id")
            .in("p_id", Types.NUMERIC)
            .cursor("o_row", TRIAGE_MAPPER)
            .build();

    static final ProcedureSpec LIST_ACTIVE_SYMPTOM_CATALOG =
            ProcedureSpec.procedure(PACKAGE, "list_active_symptom_catalog")
                    .cursor("o_rows", CATALOG_MAPPER)
                    .build();

    static final ProcedureSpec LIST_ACTIVE_SYMPTOM_IDS = ProcedureSpec.procedure(PACKAGE, "list_active_symptom_ids")
            .cursor("o_rows", SYMPTOM_ID_MAPPER)
            .build();

    private final PackageGateway gateway;

    public TriageRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "PackageGateway no puede ser nulo");
    }

    /**
     * Inserta un triaje evaluado y retorna el ID interno autogenerado (ADR-003, ADR-009).
     */
    public Long saveTriage(Triage triaje) {
        Objects.requireNonNull(triaje, "El triaje no puede ser nulo");
        Map<String, Object> out = gateway.execute(CREATE_TRIAGE, new MapSqlParameterSource()
                .addValue("p_public_id", triaje.publicId(), Types.VARCHAR)
                .addValue("p_patient_id", triaje.patientId(), Types.NUMERIC)
                .addValue("p_rules_version", triaje.rulesVersion(), Types.VARCHAR)
                .addValue("p_priority_level", triaje.priorityLevel(), Types.VARCHAR)
                .addValue("p_suggested_route", triaje.suggestedRoute(), Types.VARCHAR)
                .addValue("p_is_emergency", triaje.isEmergency() ? 1 : 0, Types.NUMERIC)
                .addValue("p_observations", triaje.notes(), Types.VARCHAR));

        Number key = (Number) out.get("o_id");
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para el triaje.");
        }
        return key.longValue();
    }

    /**
     * Inserta los síntomas reportados en la evaluación de triaje (ADR-009), uno por llamada, dentro de la misma
     * transacción del servicio.
     */
    public void saveSymptoms(Long triajeId, List<TriageSymptomEntry> sintomas) {
        Objects.requireNonNull(triajeId, "triajeId no puede ser nulo");
        if (sintomas == null || sintomas.isEmpty()) {
            return;
        }
        for (TriageSymptomEntry s : sintomas) {
            gateway.execute(ADD_TRIAGE_SYMPTOM, new MapSqlParameterSource()
                    .addValue("p_triage_id", triajeId)
                    .addValue("p_symptom_id", s.symptomId())
                    .addValue("p_duration_hours", s.durationHours())
                    .addValue("p_intensity", s.intensity()));
        }
    }

    /**
     * Consulta consolidada de un triaje por su identificador público UUID con sus síntomas y paciente (ADR-003, ADR-009).
     */
    public Optional<TriageResponse> findByPublicId(String publicId) {
        Objects.requireNonNull(publicId, "El publicId no puede ser nulo");
        List<DetailRow> rows =
                gateway.list(GET_TRIAGE_DETAIL, new MapSqlParameterSource("p_public_id", publicId), "o_rows");
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        DetailRow head = rows.get(0);
        List<SymptomItemResponse> sintomas = new ArrayList<>();
        List<String> sintomasAlarma = new ArrayList<>();
        for (DetailRow row : rows) {
            if (row.symptomCode() != null) {
                sintomas.add(new SymptomItemResponse(
                        row.symptomCode(), row.symptomName(), row.durationHours(), row.intensity(), row.isAlarm()));
                if (row.isAlarm()) {
                    sintomasAlarma.add(row.symptomCode());
                }
            }
        }
        sintomasAlarma.sort(String::compareTo);

        String mensaje = head.isEmergency()
                ? RuleBasedTriageEngine.EMERGENCY_MESSAGE
                : RuleBasedTriageEngine.messageFor(SuggestedRoute.valueOf(head.suggestedRoute()));

        return Optional.of(new TriageResponse(
                head.triagePublicId(),
                head.patientPublicId(),
                head.priorityLevel(),
                head.suggestedRoute(),
                head.isEmergency(),
                head.rulesVersion(),
                mensaje,
                RuleBasedTriageEngine.NOTICE,
                sintomasAlarma,
                sintomas,
                head.notes(),
                head.createdAt()));
    }

    /**
     * Busca la entidad Triaje por su clave pública UUID (ADR-003, ADR-009).
     */
    public Optional<Triage> findEntityByPublicId(String publicId) {
        Objects.requireNonNull(publicId, "El publicId no puede ser nulo");
        List<Triage> resultados =
                gateway.list(GET_TRIAGE_BY_PUBLIC_ID, new MapSqlParameterSource("p_public_id", publicId), "o_row");
        return resultados.stream().findFirst();
    }

    /**
     * Busca la entidad Triaje por su ID interno numérico (ADR-003).
     */
    public Optional<Triage> findEntityById(Long id) {
        Objects.requireNonNull(id, "El id no puede ser nulo");
        List<Triage> resultados = gateway.list(GET_TRIAGE_BY_ID, new MapSqlParameterSource("p_id", id), "o_row");
        return resultados.stream().findFirst();
    }

    /**
     * Lista los síntomas activos del catálogo ordenados por categoría y nombre (ADR-009).
     */
    public List<SymptomCatalogResponse> listActiveSymptomCatalog() {
        return gateway.list(LIST_ACTIVE_SYMPTOM_CATALOG, EmptySqlParameterSource.INSTANCE, "o_rows");
    }

    /**
     * Mapeo rápido de código a ID interno numérico de los síntomas activos en base de datos.
     */
    public Map<String, Long> getSymptomCodeToIdMap() {
        List<Map.Entry<String, Long>> rows =
                gateway.list(LIST_ACTIVE_SYMPTOM_IDS, EmptySqlParameterSource.INSTANCE, "o_rows");
        Map<String, Long> mapa = new HashMap<>();
        for (Map.Entry<String, Long> row : rows) {
            mapa.put(row.getKey(), row.getValue());
        }
        return mapa;
    }
}
