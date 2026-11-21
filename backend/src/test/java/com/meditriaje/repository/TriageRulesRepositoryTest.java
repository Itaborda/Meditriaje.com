package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.triage.PriorityLevel;
import com.meditriaje.triage.TriageRule;
import com.meditriaje.triage.TriageSymptom;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** Carga de síntomas y reglas activas desde PKG_TRIAGE, sin SQL en el repositorio. */
class TriageRulesRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final TriageRulesRepository repository = new TriageRulesRepository(gateway);

    @Test
    void loadActiveSymptoms_delegatesToPackageAndMapsAlarm() throws Exception {
        TriageSymptom sintoma = new TriageSymptom("CONVULSIONES", true);
        when(gateway.<TriageSymptom>list(
                        eq(TriageRulesRepository.LIST_ACTIVE_SYMPTOMS), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of(sintoma));

        assertThat(repository.loadActiveSymptoms()).containsExactly(sintoma);
        assertThat(TriageRulesRepository.LIST_ACTIVE_SYMPTOMS.qualifiedName())
                .isEqualTo("PKG_TRIAGE.list_active_symptoms");

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("CODIGO")).thenReturn("CONVULSIONES");
        when(rs.getInt("ES_ALARMA")).thenReturn(1);
        assertThat(TriageRulesRepository.SYMPTOM_MAPPER.mapRow(rs, 0)).isEqualTo(sintoma);
    }

    @Test
    void loadActiveRules_parameterizesVersionAndMapsRule() throws Exception {
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(gateway.list(eq(TriageRulesRepository.LIST_ACTIVE_RULES), params.capture(), eq("o_rows")))
                .thenReturn(List.of());

        repository.loadActiveRules("v1-prototipo");

        assertThat(params.getValue().getValue("p_rules_version")).isEqualTo("v1-prototipo");

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("CODIGO")).thenReturn("FIEBRE");
        when(rs.getBigDecimal("DURACION_MIN_HORAS")).thenReturn(BigDecimal.ZERO);
        when(rs.getBigDecimal("DURACION_MAX_HORAS")).thenReturn(null);
        when(rs.getInt("INTENSIDAD_MIN")).thenReturn(7);
        when(rs.getInt("INTENSIDAD_MAX")).thenReturn(10);
        when(rs.getString("NIVEL_PRIORIDAD")).thenReturn("II");
        assertThat(TriageRulesRepository.RULE_MAPPER.mapRow(rs, 0))
                .isEqualTo(new TriageRule("FIEBRE", BigDecimal.ZERO, null, 7, 10, PriorityLevel.II));
    }
}
