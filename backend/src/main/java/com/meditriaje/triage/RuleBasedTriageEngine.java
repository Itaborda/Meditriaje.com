package com.meditriaje.triage;

import com.meditriaje.exception.InvalidDataException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Motor de triaje puro y determinista (sin Spring/JDBC/HTTP). Reglas de PROTOTIPO, no validadas
 * clínicamente (ADR-009 y adenda M5.2).
 *
 * <ul>
 *   <li>Síntoma de alarma: corte de emergencia incondicional (no consulta reglas ni intensidad/duración).</li>
 *   <li>Por síntoma: gana la regla aplicable más urgente; sin regla aplicable aporta nivel III (nunca V).</li>
 *   <li>Nivel final: el más urgente de todos los síntomas. Nivel I también es emergencia.</li>
 * </ul>
 */
public final class RuleBasedTriageEngine implements TriageEngine {

    public static final String EMERGENCY_MESSAGE = "Llama al 123 o acude a urgencias de inmediato.";
    public static final String NOTICE =
            "Esta orientación es un prototipo, no sustituye la valoración de un profesional de la salud.";
    public static final PriorityLevel DEFAULT_LEVEL = PriorityLevel.III;

    private final String rulesVersion;
    private final Map<String, TriageSymptom> catalog;
    private final Map<String, List<TriageRule>> rulesBySymptom;

    public RuleBasedTriageEngine(String rulesVersion, Collection<TriageSymptom> catalog,
                                     Collection<TriageRule> rules) {
        if (rulesVersion == null || rulesVersion.isBlank()) {
            throw new IllegalArgumentException("versionReglas es obligatoria");
        }
        this.rulesVersion = rulesVersion;
        Map<String, TriageSymptom> cat = new HashMap<>();
        for (TriageSymptom s : catalog) {
            cat.put(s.code(), s);
        }
        this.catalog = Map.copyOf(cat);
        Map<String, List<TriageRule>> porSintoma = new HashMap<>();
        for (TriageRule r : rules) {
            porSintoma.computeIfAbsent(r.symptomCode(), k -> new ArrayList<>()).add(r);
        }
        porSintoma.replaceAll((k, v) -> List.copyOf(v));
        this.rulesBySymptom = Map.copyOf(porSintoma);
    }

    @Override
    public TriageResult evaluate(TriageInput input) {
        PriorityLevel finalLevel = null;
        List<String> alarms = new ArrayList<>();

        for (ReportedSymptom reported : input.symptoms()) {
            TriageSymptom sintoma = catalog.get(reported.symptomCode());
            if (sintoma == null) {
                throw new InvalidDataException("Síntoma desconocido: " + reported.symptomCode());
            }
            if (sintoma.isAlarm()) {
                alarms.add(sintoma.code());
                continue;
            }
            PriorityLevel level = levelFor(reported);
            finalLevel = finalLevel == null ? level : PriorityLevel.mostUrgent(finalLevel, level);
        }
        alarms.sort(String::compareTo);

        boolean emergency = !alarms.isEmpty() || finalLevel == PriorityLevel.I;
        if (emergency) {
            return new TriageResult(PriorityLevel.I, SuggestedRoute.URGENCIAS, true, rulesVersion,
                    EMERGENCY_MESSAGE, NOTICE, alarms);
        }
        return new TriageResult(finalLevel, finalLevel.route(), false, rulesVersion,
                messageFor(finalLevel.route()), NOTICE, List.of());
    }

    private PriorityLevel levelFor(ReportedSymptom reported) {
        PriorityLevel best = null;
        for (TriageRule r : rulesBySymptom.getOrDefault(reported.symptomCode(), List.of())) {
            if (r.applies(reported.durationHours(), reported.intensity())) {
                best = best == null ? r.level() : PriorityLevel.mostUrgent(best, r.level());
            }
        }
        return best != null ? best : DEFAULT_LEVEL;
    }

    public static String messageFor(SuggestedRoute route) {
        return switch (route) {
            case URGENCIAS -> EMERGENCY_MESSAGE;
            case ATENCION_PRIORITARIA -> "Se sugiere atención prioritaria.";
            case CITA_PRESENCIAL -> "Se sugiere una cita presencial.";
            case CITA_TELEMEDICINA -> "Se sugiere una cita de telemedicina.";
            case CONSULTA_PROGRAMADA -> "Se sugiere una consulta programada.";
        };
    }
}
