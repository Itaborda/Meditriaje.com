package com.meditriaje.dto.affiliation;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record RescheduleAppointmentRequest(
        @JsonProperty("nuevoSlotPublicId")
        String newSlotPublicId,
        @JsonProperty("nuevaFecha")
        LocalDate newDate,
        @JsonProperty("nuevaHora")
        LocalTime newTime,
        @JsonProperty("motivo")
        @Size(max = 500, message = "motivo no debe superar 500 caracteres")
        String reason
) {
    public RescheduleAppointmentRequest(LocalDate newDate, LocalTime newTime, String reason) {
        this(null, newDate, newTime, reason);
    }
}
