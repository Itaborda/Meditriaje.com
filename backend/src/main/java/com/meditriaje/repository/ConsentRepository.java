package com.meditriaje.repository;

import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.sql.Types;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Repositorio de la entidad {@code CONSENTIMIENTO} a través de {@code PKG_PATIENTS}. No contiene SQL.
 */
@Repository
public class ConsentRepository {

    static final ProcedureSpec CREATE_CONSENT = ProcedureSpec.procedure(PatientRepository.PACKAGE, "create_consent")
            .in("p_user_id", Types.NUMERIC)
            .in("p_text_version", Types.VARCHAR)
            .in("p_accepted", Types.NUMERIC)
            .in("p_source_ip", Types.VARCHAR)
            .out("o_id", Types.NUMERIC)
            .build();

    private final PackageGateway gateway;

    public ConsentRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public Long record(Long usuarioId, String versionTexto, boolean aceptado, String ipOrigen) {
        Objects.requireNonNull(usuarioId, "usuarioId no puede ser nulo");
        Map<String, Object> out = gateway.execute(CREATE_CONSENT, new MapSqlParameterSource()
                .addValue("p_user_id", usuarioId)
                .addValue("p_text_version", versionTexto)
                .addValue("p_accepted", aceptado ? 1 : 0)
                .addValue("p_source_ip", ipOrigen, Types.VARCHAR));
        Number key = (Number) out.get("o_id");
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para el consentimiento.");
        }
        return key.longValue();
    }
}
