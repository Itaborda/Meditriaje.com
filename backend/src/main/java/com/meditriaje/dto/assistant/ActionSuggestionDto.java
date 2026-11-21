package com.meditriaje.dto.assistant;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Sugerencia de acción interactiva o navegación en la SPA (F2.6, RF-27).
 */
public record ActionSuggestionDto(
        @JsonProperty("etiqueta")
        String label,
        @JsonProperty("rutaSpa")
        String spaRoute,
        @JsonProperty("icono")
        String icon
) {}
