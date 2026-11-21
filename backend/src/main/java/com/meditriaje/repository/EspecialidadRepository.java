package com.meditriaje.repository;

import com.meditriaje.model.Especialidad;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio para la entidad {@code ESPECIALIDAD} (ADR-001, ADR-003, ADR-006, HU-10). No contiene SQL: delega en
 * {@code PKG_STAFF} a través de {@link PackageGateway}.
 */
@Repository
public class EspecialidadRepository {

    static final String PACKAGE = "PKG_STAFF";

    static final RowMapper<Especialidad> ESPECIALIDAD_MAPPER = (rs, rowNum) -> new Especialidad(
            rs.getLong("ID"),
            rs.getString("PUBLIC_ID"),
            rs.getString("NOMBRE"),
            rs.getInt("DURACION_SLOT_MIN"),
            rs.getString("ESTADO")
    );

    static final ProcedureSpec CREATE_SPECIALTY = ProcedureSpec.procedure(PACKAGE, "create_specialty")
            .in("p_public_id", Types.VARCHAR)
            .in("p_name", Types.VARCHAR)
            .in("p_slot_minutes", Types.NUMERIC)
            .out("o_specialty_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec UPDATE_SPECIALTY = ProcedureSpec.procedure(PACKAGE, "update_specialty")
            .in("p_public_id", Types.VARCHAR)
            .in("p_name", Types.VARCHAR)
            .in("p_slot_minutes", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_SPECIALTY_BY_PUBLIC_ID =
            ProcedureSpec.procedure(PACKAGE, "get_specialty_by_public_id")
                    .in("p_public_id", Types.VARCHAR)
                    .cursor("o_specialty", ESPECIALIDAD_MAPPER)
                    .build();

    static final ProcedureSpec GET_SPECIALTY_BY_ID = ProcedureSpec.procedure(PACKAGE, "get_specialty_by_id")
            .in("p_specialty_id", Types.NUMERIC)
            .cursor("o_specialty", ESPECIALIDAD_MAPPER)
            .build();

    static final ProcedureSpec COUNT_SPECIALTIES_BY_NAME =
            ProcedureSpec.function(PACKAGE, "count_specialties_by_name", "result", Types.NUMERIC)
                    .in("p_name", Types.VARCHAR)
                    .build();

    static final ProcedureSpec COUNT_OTHER_SPECIALTIES_BY_NAME =
            ProcedureSpec.function(PACKAGE, "count_other_specialties_by_name", "result", Types.NUMERIC)
                    .in("p_name", Types.VARCHAR)
                    .in("p_public_id", Types.VARCHAR)
                    .build();

    static final ProcedureSpec CHANGE_SPECIALTY_STATUS = ProcedureSpec.procedure(PACKAGE, "change_specialty_status")
            .in("p_public_id", Types.VARCHAR)
            .in("p_status", Types.VARCHAR)
            .build();

    static final ProcedureSpec LIST_SPECIALTIES = ProcedureSpec.procedure(PACKAGE, "list_specialties")
            .in("p_status", Types.VARCHAR)
            .in("p_offset", Types.NUMERIC)
            .in("p_size", Types.NUMERIC)
            .cursor("o_rows", ESPECIALIDAD_MAPPER)
            .build();

    static final ProcedureSpec COUNT_SPECIALTIES =
            ProcedureSpec.function(PACKAGE, "count_specialties", "result", Types.NUMERIC)
                    .in("p_status", Types.VARCHAR)
                    .build();

    private final PackageGateway gateway;

    public EspecialidadRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public Especialidad crear(String publicId, String nombre, int duracionSlotMin) {
        Map<String, Object> out = gateway.execute(CREATE_SPECIALTY, new MapSqlParameterSource()
                .addValue("p_public_id", publicId)
                .addValue("p_name", nombre)
                .addValue("p_slot_minutes", duracionSlotMin));
        Number key = (Number) out.get("o_specialty_id");
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para la especialidad.");
        }
        return new Especialidad(key.longValue(), publicId, nombre, duracionSlotMin, "ACTIVO");
    }

    public void actualizar(String publicId, String nombre, int duracionSlotMin) {
        gateway.execute(UPDATE_SPECIALTY, new MapSqlParameterSource()
                .addValue("p_public_id", publicId)
                .addValue("p_name", nombre)
                .addValue("p_slot_minutes", duracionSlotMin));
    }

    public Optional<Especialidad> buscarPorPublicId(String publicId) {
        List<Especialidad> resultados = gateway.list(
                GET_SPECIALTY_BY_PUBLIC_ID,
                new MapSqlParameterSource().addValue("p_public_id", publicId, Types.VARCHAR),
                "o_specialty");
        return resultados.stream().findFirst();
    }

    public Optional<Especialidad> buscarPorId(Long id) {
        List<Especialidad> resultados = gateway.list(
                GET_SPECIALTY_BY_ID,
                new MapSqlParameterSource().addValue("p_specialty_id", id, Types.NUMERIC),
                "o_specialty");
        return resultados.stream().findFirst();
    }

    public boolean existePorNombre(String nombre) {
        Number count = gateway.callFunction(
                COUNT_SPECIALTIES_BY_NAME,
                new MapSqlParameterSource().addValue("p_name", nombre, Types.VARCHAR));
        return count != null && count.longValue() > 0;
    }

    public boolean existePorNombreYNoPublicId(String nombre, String publicId) {
        Number count = gateway.callFunction(COUNT_OTHER_SPECIALTIES_BY_NAME, new MapSqlParameterSource()
                .addValue("p_name", nombre, Types.VARCHAR)
                .addValue("p_public_id", publicId, Types.VARCHAR));
        return count != null && count.longValue() > 0;
    }

    public void cambiarEstado(String publicId, String nuevoEstado) {
        gateway.execute(CHANGE_SPECIALTY_STATUS, new MapSqlParameterSource()
                .addValue("p_public_id", publicId, Types.VARCHAR)
                .addValue("p_status", nuevoEstado, Types.VARCHAR));
    }

    public List<Especialidad> listar(int page, int size, String estadoFiltro) {
        int offset = Math.max(0, page) * Math.max(1, size);
        int limit = Math.max(1, size);
        return gateway.list(LIST_SPECIALTIES, new MapSqlParameterSource()
                .addValue("p_status", statusFilter(estadoFiltro), Types.VARCHAR)
                .addValue("p_offset", offset, Types.NUMERIC)
                .addValue("p_size", limit, Types.NUMERIC), "o_rows");
    }

    public long contar(String estadoFiltro) {
        Number count = gateway.callFunction(
                COUNT_SPECIALTIES,
                new MapSqlParameterSource().addValue("p_status", statusFilter(estadoFiltro), Types.VARCHAR));
        return count != null ? count.longValue() : 0L;
    }

    /** Un filtro en blanco equivale a no filtrar; el estado se compara en mayúsculas. */
    private static String statusFilter(String estadoFiltro) {
        return estadoFiltro != null && !estadoFiltro.isBlank() ? estadoFiltro.trim().toUpperCase() : null;
    }
}
