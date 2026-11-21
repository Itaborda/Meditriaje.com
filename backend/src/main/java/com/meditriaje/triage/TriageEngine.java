package com.meditriaje.triage;

/** Motor de triaje: función pura de la entrada reportada hacia una orientación (ADR-009). */
public interface TriageEngine {

    TriageResult evaluate(TriageInput input);
}
