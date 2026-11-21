package com.meditriaje.repository;

import com.meditriaje.dto.hospital.ProcedureResponse;
import com.meditriaje.model.HospitalProcedure;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
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

/** Procedimientos hospitalarios a través de {@code PKG_HOSPITAL}. No contiene SQL. */
@Repository
public class HospitalProcedureRepository {

    static final String PACKAGE = "PKG_HOSPITAL";

    static final RowMapper<HospitalProcedure> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp tsInicio = rs.getTimestamp("INICIO_AT");
        Timestamp tsFin = rs.getTimestamp("FIN_AT");
        Timestamp tsCreado = rs.getTimestamp("CREADO_AT");
        return new HospitalProcedure(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("EPISODIO_ID"),
                rs.getString("TIPO_PROCEDIMIENTO"),
                rs.getString("DESCRIPCION"),
                rs.getLong("PROFESIONAL_ID"),
                rs.getObject("SALA_ID", Long.class),
                rs.getString("ESTADO"),
                tsInicio != null ? tsInicio.toInstant() : null,
                tsFin != null ? tsFin.toInstant() : null,
                rs.getString("OBSERVACIONES"),
                tsCreado != null ? tsCreado.toInstant() : null);
    };

    static final RowMapper<ProcedureResponse> RESPONSE_MAPPER = (rs, rowNum) -> {
        Timestamp tsInicio = rs.getTimestamp("INICIO_AT");
        Timestamp tsFin = rs.getTimestamp("FIN_AT");
        Timestamp tsCreado = rs.getTimestamp("CREADO_AT");
        return new ProcedureResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("EPISODIO_PUB_ID"),
                rs.getString("TIPO_PROCEDIMIENTO"),
                rs.getString("DESCRIPCION"),
                rs.getString("PROFESIONAL_NOMBRE"),
                rs.getString("SALA_CODIGO"),
                rs.getString("ESTADO"),
                tsInicio != null ? tsInicio.toInstant() : null,
                tsFin != null ? tsFin.toInstant() : null,
                rs.getString("OBSERVACIONES"),
                tsCreado != null ? tsCreado.toInstant() : null);
    };

    static final ProcedureSpec CREATE_PROCEDURE = ProcedureSpec.procedure(PACKAGE, "create_procedure")
            .in("p_public_id", Types.VARCHAR)
            .in("p_episode_id", Types.NUMERIC)
            .in("p_procedure_type", Types.VARCHAR)
            .in("p_description", Types.VARCHAR)
            .in("p_professional_id", Types.NUMERIC)
            .in("p_room_id", Types.NUMERIC)
            .in("p_status", Types.VARCHAR)
            .in("p_start_at", Types.TIMESTAMP)
            .in("p_observations", Types.VARCHAR)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_PROCEDURE_BY_ID = ProcedureSpec.procedure(PACKAGE, "get_procedure_by_id")
            .in("p_id", Types.NUMERIC)
            .cursor("o_row", ROW_MAPPER)
            .build();

    static final ProcedureSpec GET_PROCEDURE_BY_PUBLIC_ID =
            ProcedureSpec.procedure(PACKAGE, "get_procedure_by_public_id")
                    .in("p_public_id", Types.VARCHAR)
                    .cursor("o_row", ROW_MAPPER)
                    .build();

    static final ProcedureSpec UPDATE_PROCEDURE_STATUS =
            ProcedureSpec.procedure(PACKAGE, "update_procedure_status")
                    .in("p_id", Types.NUMERIC)
                    .in("p_status", Types.VARCHAR)
                    .in("p_end_at", Types.TIMESTAMP)
                    .in("p_observations", Types.VARCHAR)
                    .build();

    static final ProcedureSpec LIST_PROCEDURES_BY_EPISODE =
            ProcedureSpec.procedure(PACKAGE, "list_procedures_by_episode")
                    .in("p_episode_id", Types.NUMERIC)
                    .cursor("o_rows", RESPONSE_MAPPER)
                    .build();

    private final PackageGateway gateway;

    public HospitalProcedureRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public HospitalProcedure save(HospitalProcedure p) {
        Map<String, Object> out = gateway.execute(CREATE_PROCEDURE, new MapSqlParameterSource()
                .addValue("p_public_id", p.publicId(), Types.VARCHAR)
                .addValue("p_episode_id", p.episodeId(), Types.NUMERIC)
                .addValue("p_procedure_type", p.procedureType(), Types.VARCHAR)
                .addValue("p_description", p.description(), Types.VARCHAR)
                .addValue("p_professional_id", p.professionalId(), Types.NUMERIC)
                .addValue("p_room_id", p.procedureRoomId(), Types.NUMERIC)
                .addValue("p_status", p.status() != null ? p.status() : "PROGRAMADO", Types.VARCHAR)
                .addValue("p_start_at", p.startedAt() != null ? Timestamp.from(p.startedAt()) : null, Types.TIMESTAMP)
                .addValue("p_observations", p.notes(), Types.VARCHAR));
        Number key = (Number) out.get("o_id");
        Long id = key != null ? key.longValue() : null;
        return findById(id)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar procedimiento recién guardado"));
    }

    public Optional<HospitalProcedure> findById(Long id) {
        if (id == null) return Optional.empty();
        return first(gateway.list(GET_PROCEDURE_BY_ID, new MapSqlParameterSource("p_id", id), "o_row"));
    }

    public Optional<HospitalProcedure> findByPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) return Optional.empty();
        return first(gateway.list(
                GET_PROCEDURE_BY_PUBLIC_ID, new MapSqlParameterSource("p_public_id", publicId), "o_row"));
    }

    public void updateStatus(Long id, String nuevoEstado, Instant finAt, String observaciones) {
        gateway.execute(UPDATE_PROCEDURE_STATUS, new MapSqlParameterSource()
                .addValue("p_id", id, Types.NUMERIC)
                .addValue("p_status", nuevoEstado, Types.VARCHAR)
                .addValue("p_end_at", finAt != null ? Timestamp.from(finAt) : null, Types.TIMESTAMP)
                .addValue("p_observations", observaciones, Types.VARCHAR));
    }

    public List<ProcedureResponse> listByEpisodeId(Long episodioId) {
        return gateway.list(
                LIST_PROCEDURES_BY_EPISODE, new MapSqlParameterSource("p_episode_id", episodioId), "o_rows");
    }

    private static <T> Optional<T> first(List<T> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
