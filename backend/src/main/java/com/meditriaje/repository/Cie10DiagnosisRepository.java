package com.meditriaje.repository;

import com.meditriaje.dto.clinical.Cie10DiagnosisResponse;
import com.meditriaje.model.Cie10Diagnosis;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Catálogo de patologías {@code DIAGNOSTICO_CIE10} a través de {@code PKG_CATALOGS}. No contiene SQL: declara
 * las rutinas del paquete y delega la llamada en {@link PackageGateway}.
 */
@Repository
public class Cie10DiagnosisRepository {

    static final String PACKAGE = "PKG_CATALOGS";

    static final RowMapper<Cie10Diagnosis> DIAGNOSIS_MAPPER = (rs, rowNum) -> new Cie10Diagnosis(
            rs.getLong("ID"), rs.getString("CODIGO"), rs.getString("DESCRIPCION"), rs.getString("ESTADO"));

    static final RowMapper<Cie10DiagnosisResponse> ACTIVE_MAPPER =
            (rs, rowNum) -> new Cie10DiagnosisResponse(rs.getString("CODIGO"), rs.getString("DESCRIPCION"));

    static final ProcedureSpec GET_DIAGNOSIS_BY_CODE = ProcedureSpec.procedure(PACKAGE, "get_diagnosis_by_code")
            .in("p_code", Types.VARCHAR)
            .cursor("o_row", DIAGNOSIS_MAPPER)
            .build();

    static final ProcedureSpec GET_DIAGNOSIS_BY_ID = ProcedureSpec.procedure(PACKAGE, "get_diagnosis_by_id")
            .in("p_id", Types.NUMERIC)
            .cursor("o_row", DIAGNOSIS_MAPPER)
            .build();

    static final ProcedureSpec LIST_ACTIVE_DIAGNOSES = ProcedureSpec.procedure(PACKAGE, "list_active_diagnoses")
            .in("p_pattern", Types.VARCHAR)
            .cursor("o_rows", ACTIVE_MAPPER)
            .build();

    private final PackageGateway gateway;

    public Cie10DiagnosisRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public Optional<Cie10Diagnosis> findByCode(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        return gateway.single(GET_DIAGNOSIS_BY_CODE,
                new MapSqlParameterSource("p_code", codigo.trim().toUpperCase(Locale.ROOT)), "o_row");
    }

    public Optional<Cie10Diagnosis> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return gateway.single(GET_DIAGNOSIS_BY_ID, new MapSqlParameterSource("p_id", id), "o_row");
    }

    public List<Cie10DiagnosisResponse> listActive(String query) {
        String pattern = (query == null || query.isBlank())
                ? null
                : "%" + query.trim().toUpperCase(Locale.ROOT) + "%";
        return gateway.list(LIST_ACTIVE_DIAGNOSES, new MapSqlParameterSource("p_pattern", pattern), "o_rows");
    }
}
