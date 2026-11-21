package com.meditriaje.repository;

import com.meditriaje.model.HospitalArea;
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

/** Áreas hospitalarias a través de {@code PKG_HOSPITAL}. No contiene SQL. */
@Repository
public class HospitalAreaRepository {

    static final String PACKAGE = "PKG_HOSPITAL";

    static final RowMapper<HospitalArea> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("CREADO_AT");
        return new HospitalArea(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                1L,
                rs.getString("CODIGO"),
                rs.getString("NOMBRE"),
                rs.getString("TIPO"),
                rs.getString("PISO"),
                rs.getString("ESTADO"),
                ts != null ? ts.toInstant() : null);
    };

    static final ProcedureSpec CREATE_AREA = ProcedureSpec.procedure(PACKAGE, "create_area")
            .in("p_public_id", Types.VARCHAR)
            .in("p_code", Types.VARCHAR)
            .in("p_name", Types.VARCHAR)
            .in("p_type", Types.VARCHAR)
            .in("p_floor", Types.VARCHAR)
            .in("p_status", Types.VARCHAR)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_AREA_BY_ID = ProcedureSpec.procedure(PACKAGE, "get_area_by_id")
            .in("p_id", Types.NUMERIC)
            .cursor("o_row", ROW_MAPPER)
            .build();

    static final ProcedureSpec GET_AREA_BY_PUBLIC_ID = ProcedureSpec.procedure(PACKAGE, "get_area_by_public_id")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_row", ROW_MAPPER)
            .build();

    static final ProcedureSpec LIST_AREAS_BY_SITE = ProcedureSpec.procedure(PACKAGE, "list_areas_by_site")
            .in("p_site_id", Types.NUMERIC)
            .cursor("o_rows", ROW_MAPPER)
            .build();

    private final PackageGateway gateway;

    public HospitalAreaRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    /** Registra el área en la única sede del hospital (la resuelve el paquete) y la devuelve completa. */
    public HospitalArea save(HospitalArea area) {
        Map<String, Object> out = gateway.execute(CREATE_AREA, new MapSqlParameterSource()
                .addValue("p_public_id", area.publicId(), Types.VARCHAR)
                .addValue("p_code", area.code(), Types.VARCHAR)
                .addValue("p_name", area.name(), Types.VARCHAR)
                .addValue("p_type", area.type(), Types.VARCHAR)
                .addValue("p_floor", area.floor(), Types.VARCHAR)
                .addValue("p_status", area.status() != null ? area.status() : "ACTIVA", Types.VARCHAR));
        Number key = (Number) out.get("o_id");
        Long id = key != null ? key.longValue() : null;
        return findById(id).orElseThrow(() -> new IllegalStateException("Error al recuperar área recién guardada"));
    }

    public Optional<HospitalArea> findById(Long id) {
        if (id == null) return Optional.empty();
        return first(gateway.list(GET_AREA_BY_ID, new MapSqlParameterSource("p_id", id), "o_row"));
    }

    public Optional<HospitalArea> findByPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) return Optional.empty();
        return first(gateway.list(GET_AREA_BY_PUBLIC_ID, new MapSqlParameterSource("p_public_id", publicId), "o_row"));
    }

    public List<HospitalArea> listBySiteId(Long sedeId) {
        return gateway.list(LIST_AREAS_BY_SITE, new MapSqlParameterSource("p_site_id", sedeId), "o_rows");
    }

    private static <T> Optional<T> first(List<T> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
