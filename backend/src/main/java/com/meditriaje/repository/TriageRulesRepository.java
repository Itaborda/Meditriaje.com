package com.meditriaje.repository;

import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import com.meditriaje.triage.PriorityLevel;
import com.meditriaje.triage.TriageRule;
import com.meditriaje.triage.TriageSymptom;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/** Carga (solo lectura) del catálogo de síntomas y reglas activas de triaje (ADR-009) a través de {@code PKG_TRIAGE}. */
@Repository
public class TriageRulesRepository {

    static final String PACKAGE = "PKG_TRIAGE";

    static final RowMapper<TriageSymptom> SYMPTOM_MAPPER =
            (rs, n) -> new TriageSymptom(rs.getString("CODIGO"), rs.getInt("ES_ALARMA") == 1);

    static final RowMapper<TriageRule> RULE_MAPPER = (rs, n) -> new TriageRule(
            rs.getString("CODIGO"),
            rs.getBigDecimal("DURACION_MIN_HORAS"),
            rs.getBigDecimal("DURACION_MAX_HORAS"),
            rs.getInt("INTENSIDAD_MIN"),
            rs.getInt("INTENSIDAD_MAX"),
            PriorityLevel.valueOf(rs.getString("NIVEL_PRIORIDAD")));

    static final ProcedureSpec LIST_ACTIVE_SYMPTOMS = ProcedureSpec.procedure(PACKAGE, "list_active_symptoms")
            .cursor("o_rows", SYMPTOM_MAPPER)
            .build();

    static final ProcedureSpec LIST_ACTIVE_RULES = ProcedureSpec.procedure(PACKAGE, "list_active_rules")
            .in("p_rules_version", Types.VARCHAR)
            .cursor("o_rows", RULE_MAPPER)
            .build();

    private final PackageGateway gateway;

    public TriageRulesRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "PackageGateway no puede ser nulo");
    }

    public List<TriageSymptom> loadActiveSymptoms() {
        return gateway.list(LIST_ACTIVE_SYMPTOMS, EmptySqlParameterSource.INSTANCE, "o_rows");
    }

    public List<TriageRule> loadActiveRules(String version) {
        return gateway.list(
                LIST_ACTIVE_RULES,
                new MapSqlParameterSource().addValue("p_rules_version", version, Types.VARCHAR),
                "o_rows");
    }
}
