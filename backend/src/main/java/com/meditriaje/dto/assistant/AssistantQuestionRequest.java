package com.meditriaje.dto.assistant;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de interacción y orientación al asistente virtual del sistema (F2.6, RF-27).
 */
public record AssistantQuestionRequest(
        @JsonProperty("mensaje")
        @NotBlank(message = "El mensaje no puede estar vacío.")
        @Size(max = 500, message = "El mensaje no puede exceder 500 caracteres.")
        String message,

        @JsonProperty("contexto")
        @Size(max = 100, message = "El contexto no puede exceder 100 caracteres.")
        String context
) {
    public AssistantQuestionRequest(String message) {
        this(message, null);
    }
}

