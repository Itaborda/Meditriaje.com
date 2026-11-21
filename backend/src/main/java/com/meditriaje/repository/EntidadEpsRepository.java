package com.meditriaje.repository;

import com.meditriaje.model.EntidadEps;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Repositorio de {@code ENTIDAD_EPS} a través de {@code PKG_AFFILIATIONS} (Fase A, ADR-025). No contiene
 * SQL: declara las rutinas del paquete y delega en {@link PackageGateway}.
 */
@Repository
public class EntidadEpsRepository {

    static final String PACKAGE = "PKG_AFFILIATIONS";

    static final RowMapper<EntidadEps> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("CREADO_AT");
        return new EntidadEps(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getString("CODIGO_MINSALUD"),
                rs.getString("NOMBRE"),
                rs.getString("NIT"),
                rs.getString("REGIMEN_HABITUAL"),
                rs.getString("ESTADO"),
                ts != null ? ts.toInstant() : null);
    };

    static final ProcedureSpec GET_EPS_BY_ID = ProcedureSpec.procedure(PACKAGE, "get_eps_by_id")
            .in("p_id", Types.NUMERIC)
            .cursor("o_row", ROW_MAPPER)
            .build();

    static final ProcedureSpec GET_EPS_BY_PUBLIC_ID = ProcedureSpec.procedure(PACKAGE, "get_eps_by_public_id")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_row", ROW_MAPPER)
            .build();

    static final ProcedureSpec GET_EPS_BY_CODE = ProcedureSpec.procedure(PACKAGE, "get_eps_by_code")
            .in("p_code", Types.VARCHAR)
            .cursor("o_row", ROW_MAPPER)
            .build();

    static final ProcedureSpec LIST_ACTIVE_EPS = ProcedureSpec.procedure(PACKAGE, "list_active_eps")
            .cursor("o_rows", ROW_MAPPER)
            .build();

    private final PackageGateway gateway;

    public EntidadEpsRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public Optional<EntidadEps> buscarPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return gateway.single(GET_EPS_BY_ID, new MapSqlParameterSource("p_id", id), "o_row");
    }

    public Optional<EntidadEps> buscarPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        return gateway.single(GET_EPS_BY_PUBLIC_ID, new MapSqlParameterSource("p_public_id", publicId), "o_row");
    }

    public Optional<EntidadEps> buscarPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        return gateway.single(GET_EPS_BY_CODE, new MapSqlParameterSource("p_code", codigo), "o_row");
    }

    public List<EntidadEps> listarActivas() {
        return gateway.list(LIST_ACTIVE_EPS, EmptySqlParameterSource.INSTANCE, "o_rows");
    }
}
