package com.meditriaje.repository;

import com.meditriaje.dto.hospital.ProcedimientoResponse;
import com.meditriaje.model.ProcedimientoHospitalario;
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
public class ProcedimientoHospitalarioRepository {

    static final String PACKAGE = "PKG_HOSPITAL";

    static final RowMapper<ProcedimientoHospitalario> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp tsInicio = rs.getTimestamp("INICIO_AT");
        Timestamp tsFin = rs.getTimestamp("FIN_AT");
        Timestamp tsCreado = rs.getTimestamp("CREADO_AT");
        return new ProcedimientoHospitalario(
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

    static final RowMapper<ProcedimientoResponse> RESPONSE_MAPPER = (rs, rowNum) -> {
        Timestamp tsInicio = rs.getTimestamp("INICIO_AT");
        Timestamp tsFin = rs.getTimestamp("FIN_AT");
        Timestamp tsCreado = rs.getTimestamp("CREADO_AT");
        return new ProcedimientoResponse(
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

    public ProcedimientoHospitalarioRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public ProcedimientoHospitalario guardar(ProcedimientoHospitalario p) {
        Map<String, Object> out = gateway.execute(CREATE_PROCEDURE, new MapSqlParameterSource()
                .addValue("p_public_id", p.publicId(), Types.VARCHAR)
                .addValue("p_episode_id", p.episodioId(), Types.NUMERIC)
                .addValue("p_procedure_type", p.tipoProcedimiento(), Types.VARCHAR)
                .addValue("p_description", p.descripcion(), Types.VARCHAR)
                .addValue("p_professional_id", p.profesionalId(), Types.NUMERIC)
                .addValue("p_room_id", p.salaId(), Types.NUMERIC)
                .addValue("p_status", p.estado() != null ? p.estado() : "PROGRAMADO", Types.VARCHAR)
                .addValue("p_start_at", p.inicioAt() != null ? Timestamp.from(p.inicioAt()) : null, Types.TIMESTAMP)
                .addValue("p_observations", p.observaciones(), Types.VARCHAR));
        Number key = (Number) out.get("o_id");
        Long id = key != null ? key.longValue() : null;
        return buscarPorId(id)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar procedimiento recién guardado"));
    }

    public Optional<ProcedimientoHospitalario> buscarPorId(Long id) {
        if (id == null) return Optional.empty();
        return first(gateway.list(GET_PROCEDURE_BY_ID, new MapSqlParameterSource("p_id", id), "o_row"));
    }

    public Optional<ProcedimientoHospitalario> buscarPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) return Optional.empty();
        return first(gateway.list(
                GET_PROCEDURE_BY_PUBLIC_ID, new MapSqlParameterSource("p_public_id", publicId), "o_row"));
    }

    public void actualizarEstado(Long id, String nuevoEstado, Instant finAt, String observaciones) {
        gateway.execute(UPDATE_PROCEDURE_STATUS, new MapSqlParameterSource()
                .addValue("p_id", id, Types.NUMERIC)
                .addValue("p_status", nuevoEstado, Types.VARCHAR)
                .addValue("p_end_at", finAt != null ? Timestamp.from(finAt) : null, Types.TIMESTAMP)
                .addValue("p_observations", observaciones, Types.VARCHAR));
    }

    public List<ProcedimientoResponse> listarPorEpisodioId(Long episodioId) {
        return gateway.list(
                LIST_PROCEDURES_BY_EPISODE, new MapSqlParameterSource("p_episode_id", episodioId), "o_rows");
    }

    private static <T> Optional<T> first(List<T> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
