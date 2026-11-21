package com.meditriaje.repository;

import com.meditriaje.dto.emergency.ValoracionTriajeResponse;
import com.meditriaje.model.ValoracionTriaje;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Valoraciones clínicas de triaje de urgencias ({@code VALORACION_TRIAJE}, inmutable, Fase U, ADR-027, U04) a través
 * de {@code PKG_TRIAGE}. No contiene SQL.
 */
@Repository
public class ValoracionTriajeRepository {

    static final String PACKAGE = "PKG_TRIAGE";

    static final RowMapper<ValoracionTriaje> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        return new ValoracionTriaje(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("EPISODIO_ID"),
                rs.getInt("VERSION"),
                rs.getString("NIVEL"),
                rs.getString("MOTIVO_CONSULTA"),
                rs.getString("HALLAZGOS_CLINICOS"),
                rs.getString("PRESION_ARTERIAL"),
                nullableInt(rs, "FRECUENCIA_CARDIACA"),
                nullableInt(rs, "FRECUENCIA_RESPIRATORIA"),
                nullableInt(rs, "SATURACION_OXIGENO"),
                rs.getBigDecimal("TEMPERATURA"),
                nullableInt(rs, "ESCALA_GLASGOW"),
                rs.getLong("EVALUADOR_USUARIO_ID"),
                rs.getInt("ES_REEVALUACION") == 1,
                rs.getString("MOTIVO_REEVALUACION"),
                tsCreated != null ? tsCreated.toInstant() : null);
    };

    static final RowMapper<ValoracionTriajeResponse> RESPONSE_MAPPER = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        return new ValoracionTriajeResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("EPISODIO_PUB_ID"),
                rs.getInt("VERSION"),
                rs.getString("NIVEL"),
                rs.getString("MOTIVO_CONSULTA"),
                rs.getString("HALLAZGOS_CLINICOS"),
                rs.getString("PRESION_ARTERIAL"),
                nullableInt(rs, "FRECUENCIA_CARDIACA"),
                nullableInt(rs, "FRECUENCIA_RESPIRATORIA"),
                nullableInt(rs, "SATURACION_OXIGENO"),
                rs.getBigDecimal("TEMPERATURA"),
                nullableInt(rs, "ESCALA_GLASGOW"),
                rs.getString("EVALUADOR_NOMBRE"),
                rs.getInt("ES_REEVALUACION") == 1,
                rs.getString("MOTIVO_REEVALUACION"),
                tsCreated != null ? tsCreated.toInstant() : null);
    };

    static final ProcedureSpec GET_NEXT_ASSESSMENT_VERSION =
            ProcedureSpec.function(PACKAGE, "get_next_assessment_version", "result", Types.NUMERIC)
                    .in("p_episode_id", Types.NUMERIC)
                    .build();

    static final ProcedureSpec CREATE_ASSESSMENT = ProcedureSpec.procedure(PACKAGE, "create_assessment")
            .in("p_public_id", Types.VARCHAR)
            .in("p_episode_id", Types.NUMERIC)
            .in("p_version", Types.NUMERIC)
            .in("p_level", Types.VARCHAR)
            .in("p_chief_complaint", Types.VARCHAR)
            .in("p_findings", Types.VARCHAR)
            .in("p_blood_pressure", Types.VARCHAR)
            .in("p_heart_rate", Types.NUMERIC)
            .in("p_respiratory_rate", Types.NUMERIC)
            .in("p_oxygen_saturation", Types.NUMERIC)
            .in("p_temperature", Types.NUMERIC)
            .in("p_glasgow", Types.NUMERIC)
            .in("p_evaluator_user_id", Types.NUMERIC)
            .in("p_is_reassessment", Types.NUMERIC)
            .in("p_reassessment_reason", Types.VARCHAR)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec LIST_ASSESSMENTS_BY_EPISODE =
            ProcedureSpec.procedure(PACKAGE, "list_assessments_by_episode")
                    .in("p_episode_id", Types.NUMERIC)
                    .cursor("o_rows", RESPONSE_MAPPER)
                    .build();

    static final ProcedureSpec GET_LATEST_ASSESSMENT = ProcedureSpec.procedure(PACKAGE, "get_latest_assessment")
            .in("p_episode_id", Types.NUMERIC)
            .cursor("o_row", ROW_MAPPER)
            .build();

    private final PackageGateway gateway;

    public ValoracionTriajeRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public int obtenerSiguienteVersion(Long episodioId) {
        Number version = gateway.callFunction(
                GET_NEXT_ASSESSMENT_VERSION,
                new MapSqlParameterSource().addValue("p_episode_id", episodioId, Types.NUMERIC));
        return version != null ? version.intValue() : 1;
    }

    public ValoracionTriaje guardar(ValoracionTriaje valoracion) {
        Map<String, Object> out = gateway.execute(CREATE_ASSESSMENT, new MapSqlParameterSource()
                .addValue("p_public_id", valoracion.publicId(), Types.VARCHAR)
                .addValue("p_episode_id", valoracion.episodioId(), Types.NUMERIC)
                .addValue("p_version", valoracion.version(), Types.NUMERIC)
                .addValue("p_level", valoracion.nivel(), Types.VARCHAR)
                .addValue("p_chief_complaint", valoracion.motivoConsulta(), Types.VARCHAR)
                .addValue("p_findings", valoracion.hallazgosClinicos(), Types.VARCHAR)
                .addValue("p_blood_pressure", valoracion.presionArterial(), Types.VARCHAR)
                .addValue("p_heart_rate", valoracion.frecuenciaCardiaca(), Types.NUMERIC)
                .addValue("p_respiratory_rate", valoracion.frecuenciaRespiratoria(), Types.NUMERIC)
                .addValue("p_oxygen_saturation", valoracion.saturacionOxigeno(), Types.NUMERIC)
                .addValue("p_temperature", valoracion.temperatura(), Types.NUMERIC)
                .addValue("p_glasgow", valoracion.escalaGlasgow(), Types.NUMERIC)
                .addValue("p_evaluator_user_id", valoracion.evaluadorUsuarioId(), Types.NUMERIC)
                .addValue("p_is_reassessment", valoracion.esReevaluacion() ? 1 : 0, Types.NUMERIC)
                .addValue("p_reassessment_reason", valoracion.motivoReevaluacion(), Types.VARCHAR));

        Number id = (Number) out.get("o_id");
        Long generatedId = id != null ? id.longValue() : null;

        return new ValoracionTriaje(
                generatedId,
                valoracion.publicId(),
                valoracion.episodioId(),
                valoracion.version(),
                valoracion.nivel(),
                valoracion.motivoConsulta(),
                valoracion.hallazgosClinicos(),
                valoracion.presionArterial(),
                valoracion.frecuenciaCardiaca(),
                valoracion.frecuenciaRespiratoria(),
                valoracion.saturacionOxigeno(),
                valoracion.temperatura(),
                valoracion.escalaGlasgow(),
                valoracion.evaluadorUsuarioId(),
                valoracion.esReevaluacion(),
                valoracion.motivoReevaluacion(),
                Instant.now());
    }

    public List<ValoracionTriajeResponse> listarPorEpisodioId(Long episodioId) {
        return gateway.list(
                LIST_ASSESSMENTS_BY_EPISODE,
                new MapSqlParameterSource().addValue("p_episode_id", episodioId, Types.NUMERIC),
                "o_rows");
    }

    public Optional<ValoracionTriaje> buscarUltimaPorEpisodioId(Long episodioId) {
        List<ValoracionTriaje> rows = gateway.list(
                GET_LATEST_ASSESSMENT,
                new MapSqlParameterSource().addValue("p_episode_id", episodioId, Types.NUMERIC),
                "o_row");
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
