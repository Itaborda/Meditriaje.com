package com.meditriaje.triage;

import com.meditriaje.exception.InvalidDataException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleBasedTriageEngineTest {

    private static final BigDecimal CERO = BigDecimal.ZERO;

    private static List<TriageRule> rulesV1(String code) {
        return List.of(
                new TriageRule(code, CERO, null, 7, 10, PriorityLevel.II),
                new TriageRule(code, CERO, null, 4, 6, PriorityLevel.III),
                new TriageRule(code, CERO, null, 0, 3, PriorityLevel.IV));
    }

    private static RuleBasedTriageEngine engine() {
        List<TriageSymptom> catalogo = List.of(
                new TriageSymptom("FIEBRE", false),
                new TriageSymptom("TOS", false),
                new TriageSymptom("MAREO", false),
                new TriageSymptom("SIN_REGLA", false),
                new TriageSymptom("POR_DURACION", false),
                new TriageSymptom("SOLAPE", false),
                new TriageSymptom("CRITICO_REGLA", false),
                new TriageSymptom("CONVULSIONES", true));
        List<TriageRule> reglas = new ArrayList<>();
        reglas.addAll(rulesV1("FIEBRE"));
        reglas.addAll(rulesV1("TOS"));
        reglas.addAll(rulesV1("MAREO"));
        // Duración: [0,24) -> V ; [24,48) -> IV ; [48, sin tope) -> II
        reglas.add(new TriageRule("POR_DURACION", CERO, new BigDecimal("24"), 0, 10, PriorityLevel.V));
        reglas.add(new TriageRule("POR_DURACION", new BigDecimal("24"), new BigDecimal("48"), 0, 10, PriorityLevel.IV));
        reglas.add(new TriageRule("POR_DURACION", new BigDecimal("48"), null, 0, 10, PriorityLevel.II));
        // Solape: ambas aplican con intensidad 5; gana la más urgente (II), sin importar el orden
        reglas.add(new TriageRule("SOLAPE", CERO, null, 0, 10, PriorityLevel.IV));
        reglas.add(new TriageRule("SOLAPE", CERO, null, 5, 5, PriorityLevel.II));
        // Regla que da nivel I por sí sola
        reglas.add(new TriageRule("CRITICO_REGLA", CERO, null, 9, 10, PriorityLevel.I));
        return new RuleBasedTriageEngine("v-test", catalogo, reglas);
    }

    private static ReportedSymptom reported(String code, String hours, int intensity) {
        return new ReportedSymptom(code, new BigDecimal(hours), intensity);
    }

    private static TriageResult evaluate(ReportedSymptom... s) {
        return engine().evaluate(new TriageInput(List.of(s)));
    }

    @ParameterizedTest(name = "{0} dur={1} int={2} -> {3}")
    @CsvSource({
            // límites de intensidad
            "FIEBRE, 1, 0, IV",
            "FIEBRE, 1, 3, IV",
            "FIEBRE, 1, 4, III",
            "FIEBRE, 1, 6, III",
            "FIEBRE, 1, 7, II",
            "FIEBRE, 1, 10, II",
            // límites de duración: min inclusivo, max exclusivo, fraccionaria
            "POR_DURACION, 0, 5, V",
            "POR_DURACION, 23.99, 5, V",
            "POR_DURACION, 24, 5, IV",
            "POR_DURACION, 47.999, 5, IV",
            "POR_DURACION, 48, 5, II",
            "POR_DURACION, 10000, 5, II",
            // solape: la más urgente
            "SOLAPE, 1, 5, II",
            "SOLAPE, 1, 4, IV",
            // default conservador
            "SIN_REGLA, 1, 5, III",
            "SIN_REGLA, 0, 0, III",
            "SIN_REGLA, 1, 10, III"
    })
    void caseMatrix(String code, String hours, int intensity, PriorityLevel expected) {
        TriageResult r = evaluate(reported(code, hours, intensity));

        assertThat(r.level()).isEqualTo(expected);
        assertThat(r.route()).isEqualTo(expected.route());
        assertThat(r.emergency()).isFalse();
        assertThat(r.rulesVersion()).isEqualTo("v-test");
        assertThat(r.notice()).isEqualTo(RuleBasedTriageEngine.NOTICE);
    }

    @Test
    void levelToRouteMapping() {
        assertThat(PriorityLevel.I.route()).isEqualTo(SuggestedRoute.URGENCIAS);
        assertThat(PriorityLevel.II.route()).isEqualTo(SuggestedRoute.ATENCION_PRIORITARIA);
        assertThat(PriorityLevel.III.route()).isEqualTo(SuggestedRoute.CITA_PRESENCIAL);
        assertThat(PriorityLevel.IV.route()).isEqualTo(SuggestedRoute.CITA_TELEMEDICINA);
        assertThat(PriorityLevel.V.route()).isEqualTo(SuggestedRoute.CONSULTA_PROGRAMADA);
    }

    @Test
    void multipleSymptoms_mostUrgentWins() {
        TriageResult r = evaluate(reported("FIEBRE", "1", 2), reported("TOS", "1", 5), reported("MAREO", "1", 8));

        assertThat(r.level()).isEqualTo(PriorityLevel.II);
        assertThat(r.route()).isEqualTo(SuggestedRoute.ATENCION_PRIORITARIA);
    }

    @Test
    void symptomWithoutRuleMixedWithMild_contributesLevelIII() {
        TriageResult r = evaluate(reported("FIEBRE", "1", 1), reported("SIN_REGLA", "1", 1));

        assertThat(r.level()).isEqualTo(PriorityLevel.III);
    }

    @Test
    void ruleLevelI_isEmergency() {
        TriageResult r = evaluate(reported("CRITICO_REGLA", "1", 9));

        assertThat(r.emergency()).isTrue();
        assertThat(r.level()).isEqualTo(PriorityLevel.I);
        assertThat(r.route()).isEqualTo(SuggestedRoute.URGENCIAS);
        assertThat(r.message()).isEqualTo(RuleBasedTriageEngine.EMERGENCY_MESSAGE);
        assertThat(r.alarmSymptoms()).isEmpty();
    }

    @Test
    void defaultIsNeverV_forAnyIntensityAndDuration() {
        for (int intensity = 0; intensity <= 10; intensity++) {
            for (String hours : List.of("0", "0.5", "1", "24", "1000")) {
                TriageResult r = evaluate(reported("SIN_REGLA", hours, intensity));
                assertThat(r.level()).isNotEqualTo(PriorityLevel.V).isEqualTo(PriorityLevel.III);
            }
        }
    }

    @Test
    void deterministic_andInvariantToInputOrder() {
        List<ReportedSymptom> base = List.of(
                reported("FIEBRE", "3", 2), reported("TOS", "5", 5), reported("MAREO", "1", 1), reported("SIN_REGLA", "2", 9));
        TriageResult referencia = engine().evaluate(new TriageInput(base));

        permutations(base, new ArrayList<>(), new boolean[base.size()], perm ->
                assertThat(engine().evaluate(new TriageInput(perm))).isEqualTo(referencia));
        assertThat(engine().evaluate(new TriageInput(base))).isEqualTo(referencia);
    }

    @Test
    void invariantToRuleOrder() {
        List<TriageRule> reglas = new ArrayList<>(rulesV1("FIEBRE"));
        reglas.add(new TriageRule("FIEBRE", CERO, null, 0, 10, PriorityLevel.V));
        List<TriageRule> invertidas = new ArrayList<>(reglas);
        java.util.Collections.reverse(invertidas);
        List<TriageSymptom> cat = List.of(new TriageSymptom("FIEBRE", false));
        TriageInput entrada = new TriageInput(List.of(reported("FIEBRE", "1", 8)));

        assertThat(new RuleBasedTriageEngine("v", cat, reglas).evaluate(entrada))
                .isEqualTo(new RuleBasedTriageEngine("v", cat, invertidas).evaluate(entrada));
    }

    private static void permutations(List<ReportedSymptom> src, List<ReportedSymptom> current,
                                      boolean[] used, java.util.function.Consumer<List<ReportedSymptom>> check) {
        if (current.size() == src.size()) {
            check.accept(List.copyOf(current));
            return;
        }
        for (int i = 0; i < src.size(); i++) {
            if (!used[i]) {
                used[i] = true;
                current.add(src.get(i));
                permutations(src, current, used, check);
                current.remove(current.size() - 1);
                used[i] = false;
            }
        }
    }

    // ---------- validaciones ----------

    @Test
    void unknownSymptom_throwsInvalidData() {
        assertThatThrownBy(() -> evaluate(reported("NO_EXISTE", "1", 5)))
                .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void emptyOrNullInput_throwsInvalidData() {
        assertThatThrownBy(() -> new TriageInput(List.of())).isInstanceOf(InvalidDataException.class);
        assertThatThrownBy(() -> new TriageInput(null)).isInstanceOf(InvalidDataException.class);
    }

    @Test
    void moreThanTwentySymptoms_throwsInvalidData() {
        List<ReportedSymptom> entries = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            entries.add(reported("S" + i, "1", 1));
        }
        assertThatThrownBy(() -> new TriageInput(entries)).isInstanceOf(InvalidDataException.class);
    }

    @Test
    void duplicateSymptoms_throwsInvalidData() {
        assertThatThrownBy(() -> new TriageInput(List.of(reported("FIEBRE", "1", 1), reported("FIEBRE", "2", 3))))
                .isInstanceOf(InvalidDataException.class);
    }

    @ParameterizedTest
    @CsvSource({"-1", "11", "100"})
    void intensityOutOfRange_throwsInvalidData(int intensity) {
        assertThatThrownBy(() -> reported("FIEBRE", "1", intensity)).isInstanceOf(InvalidDataException.class);
    }

    @Test
    void negativeOrNullDuration_throwsInvalidData() {
        assertThatThrownBy(() -> reported("FIEBRE", "-0.1", 5)).isInstanceOf(InvalidDataException.class);
        assertThatThrownBy(() -> new ReportedSymptom("FIEBRE", null, 5)).isInstanceOf(InvalidDataException.class);
    }

    @Test
    void invalidRule_throwsInvalidData() {
        assertThatThrownBy(() -> new TriageRule("X", CERO, CERO, 0, 10, PriorityLevel.III))
                .isInstanceOf(InvalidDataException.class);
        assertThatThrownBy(() -> new TriageRule("X", CERO, null, 5, 4, PriorityLevel.III))
                .isInstanceOf(InvalidDataException.class);
    }
}
