package com.meditriaje.triage;

import com.meditriaje.repository.TriageRulesRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Construye el motor de triaje desde BD para la versión de reglas vigente
 * ({@code meditriaje.triage.rules-version}, por defecto {@code v1-prototipo}) y lo cachea en memoria:
 * el catálogo de una versión es inmutable.
 */
@Service
public class TriageEngineFactory {

    private final TriageRulesRepository repository;
    private final String rulesVersion;
    private volatile TriageEngine engine;

    public TriageEngineFactory(TriageRulesRepository repository,
                              @Value("${meditriaje.triage.rules-version:v1-prototipo}") String rulesVersion) {
        this.repository = repository;
        this.rulesVersion = rulesVersion;
    }

    public String currentVersion() {
        return rulesVersion;
    }

    public TriageEngine getEngine() {
        TriageEngine current = engine;
        if (current == null) {
            synchronized (this) {
                current = engine;
                if (current == null) {
                    var catalog = repository.loadActiveSymptoms();
                    if (catalog.isEmpty()) {
                        throw new IllegalStateException("Catálogo de síntomas vacío; no se puede construir el motor");
                    }
                    current = new RuleBasedTriageEngine(rulesVersion, catalog,
                            repository.loadActiveRules(rulesVersion));
                    engine = current;
                }
            }
        }
        return current;
    }
}
