package com.meditriaje.repository;

import com.meditriaje.model.HospitalDischarge;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/** Egresos hospitalarios a través de {@code PKG_HOSPITAL}. No contiene SQL. */
@Repository
public class HospitalDischargeRepository {

    static final String PACKAGE = "PKG_HOSPITAL";

    static final RowMapper<HospitalDischarge> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp tsEgreso = rs.getTimestamp("FECHA_EGRESO");
        Timestamp tsCreado = rs.getTimestamp("CREADO_AT");
        return new HospitalDischarge(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("EPISODIO_ID"),
                rs.getLong("MEDICO_EGRESO_ID"),
                tsEgreso != null ? tsEgreso.toInstant() : null,
                rs.getString("TIPO_DESTINO"),
                rs.getString("DIAGNOSTICO_EGRESO"),
                rs.getString("EPICRISIS_RESUMEN"),
                rs.getString("PLAN_MANEJO"),
                tsCreado != null ? tsCreado.toInstant() : null);
    };

    static final ProcedureSpec CREATE_DISCHARGE = ProcedureSpec.procedure(PACKAGE, "create_discharge")
            .in("p_public_id", Types.VARCHAR)
            .in("p_episode_id", Types.NUMERIC)
            .in("p_doctor_id", Types.NUMERIC)
            .in("p_discharged_at", Types.TIMESTAMP)
            .in("p_destination_type", Types.VARCHAR)
            .in("p_diagnosis", Types.VARCHAR)
            .in("p_epicrisis", Types.VARCHAR)
            .in("p_care_plan", Types.VARCHAR)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_DISCHARGE_BY_ID = ProcedureSpec.procedure(PACKAGE, "get_discharge_by_id")
            .in("p_id", Types.NUMERIC)
            .cursor("o_row", ROW_MAPPER)
            .build();

    static final ProcedureSpec GET_DISCHARGE_BY_EPISODE =
            ProcedureSpec.procedure(PACKAGE, "get_discharge_by_episode")
                    .in("p_episode_id", Types.NUMERIC)
                    .cursor("o_row", ROW_MAPPER)
                    .build();

    private final PackageGateway gateway;

    public HospitalDischargeRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public HospitalDischarge save(HospitalDischarge egreso) {
        Map<String, Object> out = gateway.execute(CREATE_DISCHARGE, new MapSqlParameterSource()
                .addValue("p_public_id", egreso.publicId(), Types.VARCHAR)
                .addValue("p_episode_id", egreso.episodeId(), Types.NUMERIC)
                .addValue("p_doctor_id", egreso.dischargingPhysicianId(), Types.NUMERIC)
                .addValue("p_discharged_at", Timestamp.from(egreso.dischargeDate()), Types.TIMESTAMP)
                .addValue("p_destination_type", egreso.destinationType(), Types.VARCHAR)
                .addValue("p_diagnosis", egreso.dischargeDiagnosis(), Types.VARCHAR)
                .addValue("p_epicrisis", egreso.epicrisisSummary(), Types.VARCHAR)
                .addValue("p_care_plan", egreso.managementPlan(), Types.VARCHAR));
        Number key = (Number) out.get("o_id");
        Long id = key != null ? key.longValue() : null;
        return findById(id).orElseThrow(() -> new IllegalStateException("Error al recuperar egreso recién guardado"));
    }

    public Optional<HospitalDischarge> findById(Long id) {
        if (id == null) return Optional.empty();
        List<HospitalDischarge> list = gateway.list(GET_DISCHARGE_BY_ID, new MapSqlParameterSource("p_id", id), "o_row");
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<HospitalDischarge> findByEpisodeId(Long episodioId) {
        List<HospitalDischarge> list = gateway.list(
                GET_DISCHARGE_BY_EPISODE, new MapSqlParameterSource("p_episode_id", episodioId), "o_row");
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
