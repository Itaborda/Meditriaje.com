package com.meditriaje.repository;

import com.meditriaje.model.AsignacionAsistencial;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Repositorio para {@code ASIGNACION_ASISTENCIAL} (Fase U, ADR-022, U06). No contiene SQL: delega en
 * {@code PKG_STAFF} a través de {@link PackageGateway}.
 */
@Repository
public class AsignacionAsistencialRepository {

    static final String PACKAGE = "PKG_STAFF";

    static final RowMapper<AsignacionAsistencial> ASIGNACION_MAPPER = (rs, rowNum) -> {
        Timestamp tsInicio = rs.getTimestamp("FECHA_INICIO");
        Timestamp tsFin = rs.getTimestamp("FECHA_FIN");
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        return new AsignacionAsistencial(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("EPISODIO_ID"),
                rs.getLong("PROFESIONAL_ID"),
                rs.getString("FUNCION"),
                rs.getLong("ASIGNADO_POR_USUARIO_ID"),
                tsInicio != null ? tsInicio.toInstant() : null,
                tsFin != null ? tsFin.toInstant() : null,
                rs.getInt("ACTIVO") == 1,
                tsCreated != null ? tsCreated.toInstant() : null
        );
    };

    static final ProcedureSpec CREATE_ASSIGNMENT = ProcedureSpec.procedure(PACKAGE, "create_assignment")
            .in("p_public_id", Types.VARCHAR)
            .in("p_episode_id", Types.NUMERIC)
            .in("p_professional_id", Types.NUMERIC)
            .in("p_role", Types.VARCHAR)
            .in("p_assigned_by_user_id", Types.NUMERIC)
            .in("p_start_at", Types.TIMESTAMP)
            .in("p_active", Types.NUMERIC)
            .out("o_assignment_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec DEACTIVATE_PREVIOUS_ASSIGNMENT =
            ProcedureSpec.procedure(PACKAGE, "deactivate_previous_assignment")
                    .in("p_episode_id", Types.NUMERIC)
                    .in("p_role", Types.VARCHAR)
                    .out("o_updated", Types.NUMERIC)
                    .build();

    static final ProcedureSpec COUNT_ACTIVE_ASSIGNMENTS =
            ProcedureSpec.function(PACKAGE, "count_active_assignments", "result", Types.NUMERIC)
                    .in("p_professional_id", Types.NUMERIC)
                    .in("p_episode_id", Types.NUMERIC)
                    .build();

    static final ProcedureSpec LIST_ASSIGNMENTS_BY_EPISODE =
            ProcedureSpec.procedure(PACKAGE, "list_assignments_by_episode")
                    .in("p_episode_id", Types.NUMERIC)
                    .cursor("o_rows", ASIGNACION_MAPPER)
                    .build();

    private final PackageGateway gateway;

    public AsignacionAsistencialRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public AsignacionAsistencial guardar(AsignacionAsistencial asignacion) {
        Map<String, Object> out = gateway.execute(CREATE_ASSIGNMENT, new MapSqlParameterSource()
                .addValue("p_public_id", asignacion.publicId(), Types.VARCHAR)
                .addValue("p_episode_id", asignacion.episodioId(), Types.NUMERIC)
                .addValue("p_professional_id", asignacion.profesionalId(), Types.NUMERIC)
                .addValue("p_role", asignacion.funcion(), Types.VARCHAR)
                .addValue("p_assigned_by_user_id", asignacion.asignadoPorUsuarioId(), Types.NUMERIC)
                .addValue("p_start_at", asignacion.fechaInicio() != null
                        ? Timestamp.from(asignacion.fechaInicio()) : Timestamp.from(Instant.now()), Types.TIMESTAMP)
                .addValue("p_active", asignacion.activo() ? 1 : 0, Types.NUMERIC));

        Number id = (Number) out.get("o_assignment_id");
        Long generatedId = id != null ? id.longValue() : null;

        return new AsignacionAsistencial(
                generatedId,
                asignacion.publicId(),
                asignacion.episodioId(),
                asignacion.profesionalId(),
                asignacion.funcion(),
                asignacion.asignadoPorUsuarioId(),
                asignacion.fechaInicio() != null ? asignacion.fechaInicio() : Instant.now(),
                null,
                true,
                Instant.now()
        );
    }

    public int inactivarAsignacionPreviaPorFuncion(Long episodioId, String funcion) {
        Map<String, Object> out = gateway.execute(DEACTIVATE_PREVIOUS_ASSIGNMENT, new MapSqlParameterSource()
                .addValue("p_episode_id", episodioId)
                .addValue("p_role", funcion));
        Number updated = (Number) out.get("o_updated");
        return updated != null ? updated.intValue() : 0;
    }

    public boolean tieneAsignacionActiva(Long profesionalId, Long episodioId) {
        Number count = gateway.callFunction(COUNT_ACTIVE_ASSIGNMENTS, new MapSqlParameterSource()
                .addValue("p_professional_id", profesionalId)
                .addValue("p_episode_id", episodioId));
        return count != null && count.longValue() > 0;
    }

    public List<AsignacionAsistencial> listarPorEpisodioId(Long episodioId) {
        return gateway.list(LIST_ASSIGNMENTS_BY_EPISODE, new MapSqlParameterSource("p_episode_id", episodioId), "o_rows");
    }
}
