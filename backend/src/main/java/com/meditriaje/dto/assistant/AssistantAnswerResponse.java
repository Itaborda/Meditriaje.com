package com.meditriaje.dto.assistant;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Respuesta estructurada del asistente virtual con acciones y disclaimer médico (F2.6, RF-27, ADR-018).
 */
public record AssistantAnswerResponse(
        @JsonProperty("respuesta")
        String answer,
        @JsonProperty("esEmergencia")
        boolean isEmergency,
        @JsonProperty("categoria")
        String category,
        @JsonProperty("sugerencias")
        List<ActionSuggestionDto> suggestions,
        @JsonProperty("avisoLegal")
        String legalNotice
) {}
