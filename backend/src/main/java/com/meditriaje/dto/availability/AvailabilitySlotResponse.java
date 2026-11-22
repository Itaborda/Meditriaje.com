package com.meditriaje.dto.availability;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;

/**
 * DTO para la consulta pública y asistencial de disponibilidad de citas (HU-03, ADR-003, ADR-005, ADR-006).
 */
public record AvailabilitySlotResponse(
        @JsonProperty("slotPublicId")
        String slotPublicId,
        @JsonProperty("profesionalPublicId")
        String professionalPublicId,
        @JsonProperty("profesionalNombre")
        String professionalName,
        @JsonProperty("especialidadPublicId")
        String specialtyPublicId,
        @JsonProperty("especialidadNombre")
        String specialtyName,
        @JsonProperty("sedePublicId")
        String sitePublicId,
        @JsonProperty("sedeNombre")
        String siteName,
        @JsonProperty("sedeDireccion")
        String siteAddress,
        @JsonProperty("sedeCiudad")
        String siteCity,
        @JsonProperty("fechaHoraInicio")
        Instant startDateTime,
        @JsonProperty("fechaHoraFin")
        Instant endDateTime,
        @JsonProperty("modalidad")
        String modality,
        @JsonProperty("duracionMinutos")
        int durationMinutes
) {
    public AvailabilitySlotResponse(
            String slotPublicId,
            String professionalPublicId,
            String professionalName,
            String specialtyPublicId,
            String specialtyName,
            String sitePublicId,
            String siteName,
            String siteAddress,
            String siteCity,
            Instant startDateTime,
            Instant endDateTime,
            String modality
    ) {
        this(
                slotPublicId,
                professionalPublicId,
                professionalName,
                specialtyPublicId,
                specialtyName,
                sitePublicId,
                siteName,
                siteAddress,
                siteCity,
                startDateTime,
                endDateTime,
                modality,
                (startDateTime != null && endDateTime != null)
                        ? (int) Duration.between(startDateTime, endDateTime).toMinutes()
                        : 0
        );
    }
}
