package com.meditriaje.repository;

import com.meditriaje.dto.hospital.MovimientoResponse;
import com.meditriaje.model.MovimientoPaciente;
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

/** Movimientos intrahospitalarios (append-only) a través de {@code PKG_HOSPITAL}. No contiene SQL. */
@Repository
public class MovimientoPacienteRepository {

    static final String PACKAGE = "PKG_HOSPITAL";

    static final RowMapper<MovimientoPaciente> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp tsFecha = rs.getTimestamp("FECHA_MOVIMIENTO");
        Timestamp tsCreado = rs.getTimestamp("CREADO_AT");
        return new MovimientoPaciente(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("EPISODIO_ID"),
                rs.getObject("AREA_ORIGEN_ID", Long.class),
                rs.getLong("AREA_DESTINO_ID"),
                rs.getObject("CAMA_ORIGEN_ID", Long.class),
                rs.getObject("CAMA_DESTINO_ID", Long.class),
                tsFecha != null ? tsFecha.toInstant() : null,
                rs.getString("MOTIVO_TRASLADO"),
                rs.getLong("REGISTRADO_POR_USUARIO_ID"),
                tsCreado != null ? tsCreado.toInstant() : null);
    };

    static final RowMapper<MovimientoResponse> RESPONSE_MAPPER = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("FECHA_MOVIMIENTO");
        return new MovimientoResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("EPISODIO_PUB_ID"),
                rs.getString("AREA_ORIGEN_NOMBRE"),
                rs.getString("AREA_DESTINO_NOMBRE"),
                rs.getString("CAMA_ORIGEN_CODIGO"),
                rs.getString("CAMA_DESTINO_CODIGO"),
                ts != null ? ts.toInstant() : null,
                rs.getString("MOTIVO_TRASLADO"),
                rs.getString("REGISTRADO_POR_NOMBRE"));
    };

    static final ProcedureSpec CREATE_MOVEMENT = ProcedureSpec.procedure(PACKAGE, "create_movement")
            .in("p_public_id", Types.VARCHAR)
            .in("p_episode_id", Types.NUMERIC)
            .in("p_origin_area_id", Types.NUMERIC)
            .in("p_target_area_id", Types.NUMERIC)
            .in("p_origin_bed_id", Types.NUMERIC)
            .in("p_target_bed_id", Types.NUMERIC)
            .in("p_moved_at", Types.TIMESTAMP)
            .in("p_reason", Types.VARCHAR)
            .in("p_registered_by_user_id", Types.NUMERIC)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_MOVEMENT_BY_ID = ProcedureSpec.procedure(PACKAGE, "get_movement_by_id")
            .in("p_id", Types.NUMERIC)
            .cursor("o_row", ROW_MAPPER)
            .build();

    static final ProcedureSpec LIST_MOVEMENTS_BY_EPISODE =
            ProcedureSpec.procedure(PACKAGE, "list_movements_by_episode")
                    .in("p_episode_id", Types.NUMERIC)
                    .cursor("o_rows", RESPONSE_MAPPER)
                    .build();

    private final PackageGateway gateway;

    public MovimientoPacienteRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public MovimientoPaciente guardar(MovimientoPaciente mov) {
        Map<String, Object> out = gateway.execute(CREATE_MOVEMENT, new MapSqlParameterSource()
                .addValue("p_public_id", mov.publicId(), Types.VARCHAR)
                .addValue("p_episode_id", mov.episodioId(), Types.NUMERIC)
                .addValue("p_origin_area_id", mov.areaOrigenId(), Types.NUMERIC)
                .addValue("p_target_area_id", mov.areaDestinoId(), Types.NUMERIC)
                .addValue("p_origin_bed_id", mov.camaOrigenId(), Types.NUMERIC)
                .addValue("p_target_bed_id", mov.camaDestinoId(), Types.NUMERIC)
                .addValue("p_moved_at", Timestamp.from(mov.fechaMovimiento()), Types.TIMESTAMP)
                .addValue("p_reason", mov.motivoTraslado(), Types.VARCHAR)
                .addValue("p_registered_by_user_id", mov.registradoPorUsuarioId(), Types.NUMERIC));
        Number key = (Number) out.get("o_id");
        Long id = key != null ? key.longValue() : null;
        return buscarPorId(id).orElseThrow(() -> new IllegalStateException("Error al recuperar movimiento recién guardado"));
    }

    public Optional<MovimientoPaciente> buscarPorId(Long id) {
        if (id == null) return Optional.empty();
        List<MovimientoPaciente> list = gateway.list(GET_MOVEMENT_BY_ID, new MapSqlParameterSource("p_id", id), "o_row");
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<MovimientoResponse> listarPorEpisodioId(Long episodioId) {
        return gateway.list(
                LIST_MOVEMENTS_BY_EPISODE, new MapSqlParameterSource("p_episode_id", episodioId), "o_rows");
    }
}
