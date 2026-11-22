package com.meditriaje.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Solicitud administrativa para generar slots de disponibilidad en bloque (HU-10, ADR-005, ADR-006).
 */
public record GenerateSlotsRequest(
        @JsonProperty("profesionalPublicId")
        @NotBlank(message = "El publicId del profesional es obligatorio")
        String professionalPublicId,

        @JsonProperty("fechaInicio")
        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDate startDate,

        @JsonProperty("fechaFin")
        @NotNull(message = "La fecha de fin es obligatoria")
        LocalDate endDate,

        @JsonProperty("horaInicio")
        @NotNull(message = "La hora de inicio es obligatoria")
        LocalTime startTime,

        @JsonProperty("horaFin")
        @NotNull(message = "La hora de fin es obligatoria")
        LocalTime endTime,

        @JsonProperty("duracionMinutos")
        @Min(value = 5, message = "La duracion minima del slot es de 5 minutos")
        @Max(value = 120, message = "La duracion maxima del slot es de 120 minutos")
        Integer durationMinutes,

        @JsonProperty("modalidad")
        String modality,

        @JsonProperty("diasSemana")
        List<DayOfWeek> daysOfWeek
) {
}
