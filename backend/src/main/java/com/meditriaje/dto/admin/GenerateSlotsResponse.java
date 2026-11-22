package com.meditriaje.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Respuesta a la generación en lote de turnos de disponibilidad asistencial (HU-10, ADR-006).
 */
public record GenerateSlotsResponse(
        @JsonProperty("slotsGenerados")
        int generatedSlots,
        List<SlotResponse> slots,
        @JsonProperty("mensaje")
        String message
) {
}
