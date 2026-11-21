package com.meditriaje.triage;

import com.meditriaje.exception.InvalidDataException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Entrada del motor: lista no vacía, máximo {@value #MAX_SINTOMAS} síntomas, sin duplicados. */
public record TriageInput(List<ReportedSymptom> symptoms) {

    public static final int MAX_SYMPTOMS = 20;

    public TriageInput {
        if (symptoms == null || symptoms.isEmpty()) {
            throw new InvalidDataException("Debe reportar al menos un síntoma");
        }
        if (symptoms.size() > MAX_SYMPTOMS) {
            throw new InvalidDataException("No se admiten más de " + MAX_SYMPTOMS + " síntomas");
        }
        Set<String> vistos = new HashSet<>();
        for (ReportedSymptom s : symptoms) {
            if (s == null) {
                throw new InvalidDataException("Síntoma reportado inválido");
            }
            if (!vistos.add(s.symptomCode())) {
                throw new InvalidDataException("Síntoma duplicado: " + s.symptomCode());
            }
        }
        symptoms = List.copyOf(symptoms);
    }
}
