package com.meditriaje.repository;

import com.meditriaje.model.Sede;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.sql.Types;
import java.util.Objects;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Acceso a la única sede del hospital a través de {@code PKG_COMMON}. No contiene SQL: declara las rutinas del
 * paquete y delega la llamada en {@link PackageGateway} (modelo para el resto de repositorios).
 */
@Repository
public class SiteRepository {

    static final String PACKAGE = "PKG_COMMON";
    static final String SITE_CURSOR = "o_site";

    static final RowMapper<Sede> SITE_MAPPER = (rs, rowNum) -> new Sede(
            rs.getLong("ID"),
            rs.getLong("INSTITUCION_ID"),
            rs.getString("PUBLIC_ID"),
            rs.getString("NOMBRE"),
            rs.getString("DIRECCION"),
            rs.getString("CIUDAD"),
            rs.getString("ESTADO"));

    static final ProcedureSpec GET_SINGLE_SITE = ProcedureSpec.procedure(PACKAGE, "get_single_site")
            .cursor(SITE_CURSOR, SITE_MAPPER)
            .build();

    static final ProcedureSpec GET_SINGLE_SITE_ID =
            ProcedureSpec.function(PACKAGE, "get_single_site_id", "result", Types.NUMERIC).build();

    private final PackageGateway gateway;

    public SiteRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    /** Devuelve la única sede del hospital; el paquete lanza -20100 (404) si no hay ninguna. */
    public Sede getSingleSite() {
        return gateway.<Sede>single(GET_SINGLE_SITE, EmptySqlParameterSource.INSTANCE, SITE_CURSOR)
                .orElseThrow(() -> new IllegalStateException("PKG_COMMON.get_single_site returned no rows"));
    }

    /** Devuelve el ID interno de la única sede del hospital. */
    public Long getSingleSiteId() {
        Number id = gateway.callFunction(GET_SINGLE_SITE_ID);
        return id.longValue();
    }
}
