package com.meditriaje.triage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.3: el corte de emergencia es infalible. Los códigos de alarma se extraen de V007__triaje.sql,
 * de modo que la prueba falla si el catálogo semilla cambia.
 */
class EmergencyCutoffTest {

    /** Alarmas esperadas hoy en V007 (contrapeso explícito: si cambian, hay que revisar esta prueba). */
    private static final List<String> EXPECTED_ALARMS = List.of(
            "DOLOR_TORACICO_OPRESIVO",
            "DIFICULTAD_RESP_SEVERA",
            "PERDIDA_CONCIENCIA",
            "CONVULSIONES",
            "SANGRADO_INCONTROLABLE",
            "PARALISIS_FACIAL_SUBITA");

    private static final Pattern ALARM_INSERT_PATTERN = Pattern.compile(
            "INSERT INTO SINTOMA \\(PUBLIC_ID, CODIGO, NOMBRE, CATEGORIA, ES_ALARMA\\)\\s+VALUES\\s*\\("
                    + "'[^']*',\\s*'([A-Z_]+)',\\s*'[^']*',\\s*'[^']*',\\s*1\\)");
    private static final Pattern NON_ALARM_INSERT_PATTERN = Pattern.compile(
            "INSERT INTO SINTOMA \\(PUBLIC_ID, CODIGO, NOMBRE, CATEGORIA\\)\\s+VALUES\\s*\\("
                    + "'[^']*',\\s*'([A-Z_]+)',");

    private static String sql() throws IOException {
        for (String p : List.of("../database/migrations/V007__triaje.sql", "database/migrations/V007__triaje.sql")) {
            Path path = Path.of(p);
            if (Files.exists(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("No se encontró V007__triaje.sql");
    }

    private static List<String> extractMatches(Pattern pattern, String content) {
        List<String> codigos = new ArrayList<>();
        Matcher m = pattern.matcher(content);
        while (m.find()) {
            codigos.add(m.group(1));
        }
        return codigos;
    }

    private static List<String> alarmsFromSql() throws IOException {
        return extractMatches(ALARM_INSERT_PATTERN, sql());
    }

    private static List<String> mildFromSql() throws IOException {
        return extractMatches(NON_ALARM_INSERT_PATTERN, sql());
    }

    private static RuleBasedTriageEngine engineWithRealCatalog() throws IOException {
        List<TriageSymptom> catalogo = new ArrayList<>();
        List<TriageRule> reglas = new ArrayList<>();
        for (String a : alarmsFromSql()) {
            catalogo.add(new TriageSymptom(a, true));
        }
        for (String l : mildFromSql()) {
            catalogo.add(new TriageSymptom(l, false));
            reglas.add(new TriageRule(l, BigDecimal.ZERO, null, 7, 10, PriorityLevel.II));
            reglas.add(new TriageRule(l, BigDecimal.ZERO, null, 4, 6, PriorityLevel.III));
            reglas.add(new TriageRule(l, BigDecimal.ZERO, null, 0, 3, PriorityLevel.IV));
        }
        return new RuleBasedTriageEngine("v1-prototipo", catalogo, reglas);
    }

    @Test
    void sqlHasExpectedAlarms_andTestCoversAll() throws IOException {
        List<String> delSql = alarmsFromSql();

        assertThat(delSql).hasSize(EXPECTED_ALARMS.size());
        assertThat(delSql).containsExactlyInAnyOrderElementsOf(EXPECTED_ALARMS);
        assertThat(mildFromSql()).isNotEmpty().doesNotContainAnyElementsOf(delSql);
    }

    private static void verifyEmergency(TriageResult r, String alarm) {
        assertThat(r.emergency()).isTrue();
        assertThat(r.level()).isEqualTo(PriorityLevel.I);
        assertThat(r.route()).isEqualTo(SuggestedRoute.URGENCIAS);
        assertThat(r.message()).contains("123").contains("urgencias");
        assertThat(r.notice()).isEqualTo(RuleBasedTriageEngine.NOTICE);
        assertThat(r.alarmSymptoms()).contains(alarm);
    }

    @Test
    void eachAlarm_alone_withZeroIntensityAndDuration_isEmergency() throws IOException {
        RuleBasedTriageEngine engine = engineWithRealCatalog();
        for (String alarm : alarmsFromSql()) {
            TriageResult r = engine.evaluate(
                    new TriageInput(List.of(new ReportedSymptom(alarm, BigDecimal.ZERO, 0))));
            verifyEmergency(r, alarm);
        }
    }

    @Test
    void eachAlarm_mixedWithMild_isEmergency_atAnyIntensity() throws IOException {
        RuleBasedTriageEngine engine = engineWithRealCatalog();
        List<String> mildSymptoms = mildFromSql();
        for (String alarm : alarmsFromSql()) {
            for (int intensity : new int[]{0, 5, 10}) {
                List<ReportedSymptom> lista = new ArrayList<>();
                lista.add(new ReportedSymptom(alarm, BigDecimal.ZERO, 0));
                for (String leve : mildSymptoms.subList(0, 3)) {
                    lista.add(new ReportedSymptom(leve, BigDecimal.ZERO, intensity));
                }
                verifyEmergency(engine.evaluate(new TriageInput(lista)), alarm);
                // y con la alarma al final de la lista
                java.util.Collections.reverse(lista);
                verifyEmergency(engine.evaluate(new TriageInput(lista)), alarm);
            }
        }
    }

    @Test
    void allAlarmsTogether_reportedSorted() throws IOException {
        List<ReportedSymptom> lista = new ArrayList<>();
        for (String a : alarmsFromSql()) {
            lista.add(new ReportedSymptom(a, BigDecimal.ZERO, 0));
        }
        TriageResult r = engineWithRealCatalog().evaluate(new TriageInput(lista));

        assertThat(r.emergency()).isTrue();
        assertThat(r.alarmSymptoms()).containsExactlyInAnyOrderElementsOf(alarmsFromSql()).isSorted();
    }

    @Test
    void alarmWithoutRulesOrMildCatalog_remainsEmergency() {
        RuleBasedTriageEngine engine = new RuleBasedTriageEngine("v",
                List.of(new TriageSymptom("CONVULSIONES", true)), List.of());

        TriageResult r = engine.evaluate(
                new TriageInput(List.of(new ReportedSymptom("CONVULSIONES", BigDecimal.ZERO, 0))));

        assertThat(r.emergency()).isTrue();
        assertThat(r.route()).isEqualTo(SuggestedRoute.URGENCIAS);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 4, 6, 7, 10})
    void noAlarm_levelIVPlusMild_isNotEmergency(int intensity) throws IOException {
        String leve = mildFromSql().get(0);
        TriageResult r = engineWithRealCatalog().evaluate(
                new TriageInput(List.of(new ReportedSymptom(leve, BigDecimal.ONE, intensity))));

        assertThat(r.emergency()).isFalse();
        assertThat(r.level()).isNotEqualTo(PriorityLevel.I);
        assertThat(r.message()).doesNotContain("123");
    }
}
