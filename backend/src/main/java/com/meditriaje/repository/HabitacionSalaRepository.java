package com.meditriaje.repository;

import com.meditriaje.model.HabitacionSala;
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

/** Habitaciones y salas hospitalarias a través de {@code PKG_HOSPITAL}. No contiene SQL. */
@Repository
public class HabitacionSalaRepository {

    static final String PACKAGE = "PKG_HOSPITAL";

    static final RowMapper<HabitacionSala> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("CREADO_AT");
        return new HabitacionSala(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("AREA_ID"),
                rs.getString("CODIGO"),
                rs.getString("TIPO"),
                rs.getString("ESTADO"),
                ts != null ? ts.toInstant() : null);
    };

    static final ProcedureSpec CREATE_ROOM = ProcedureSpec.procedure(PACKAGE, "create_room")
            .in("p_public_id", Types.VARCHAR)
            .in("p_area_id", Types.NUMERIC)
            .in("p_code", Types.VARCHAR)
            .in("p_type", Types.VARCHAR)
            .in("p_status", Types.VARCHAR)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_ROOM_BY_ID = ProcedureSpec.procedure(PACKAGE, "get_room_by_id")
            .in("p_id", Types.NUMERIC)
            .cursor("o_row", ROW_MAPPER)
            .build();

    static final ProcedureSpec GET_ROOM_BY_PUBLIC_ID = ProcedureSpec.procedure(PACKAGE, "get_room_by_public_id")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_row", ROW_MAPPER)
            .build();

    static final ProcedureSpec LIST_ROOMS_BY_AREA = ProcedureSpec.procedure(PACKAGE, "list_rooms_by_area")
            .in("p_area_id", Types.NUMERIC)
            .cursor("o_rows", ROW_MAPPER)
            .build();

    private final PackageGateway gateway;

    public HabitacionSalaRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public HabitacionSala guardar(HabitacionSala habitacion) {
        Map<String, Object> out = gateway.execute(CREATE_ROOM, new MapSqlParameterSource()
                .addValue("p_public_id", habitacion.publicId())
                .addValue("p_area_id", habitacion.areaId())
                .addValue("p_code", habitacion.codigo())
                .addValue("p_type", habitacion.tipo() != null ? habitacion.tipo() : "COMPARTIDA")
                .addValue("p_status", habitacion.estado() != null ? habitacion.estado() : "ACTIVA"));
        Number key = (Number) out.get("o_id");
        Long id = key != null ? key.longValue() : null;
        return buscarPorId(id).orElseThrow(() -> new IllegalStateException("Error al recuperar habitación recién guardada"));
    }

    public Optional<HabitacionSala> buscarPorId(Long id) {
        if (id == null) return Optional.empty();
        return first(gateway.list(GET_ROOM_BY_ID, new MapSqlParameterSource("p_id", id), "o_row"));
    }

    public Optional<HabitacionSala> buscarPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) return Optional.empty();
        return first(gateway.list(GET_ROOM_BY_PUBLIC_ID, new MapSqlParameterSource("p_public_id", publicId), "o_row"));
    }

    public List<HabitacionSala> listarPorAreaId(Long areaId) {
        return gateway.list(LIST_ROOMS_BY_AREA, new MapSqlParameterSource("p_area_id", areaId), "o_rows");
    }

    private static <T> Optional<T> first(List<T> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
