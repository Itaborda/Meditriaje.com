package com.meditriaje.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * DTO para la representación pública de un slot de disponibilidad médica (HU-10, ADR-003, ADR-005, ADR-006).
 */
public record SlotResponse(
        String publicId,
        @JsonProperty("profesionalPublicId")
        String professionalPublicId,
        @JsonProperty("profesionalNombre")
        String professionalName,
        @JsonProperty("sedePublicId")
        String sitePublicId,
        @JsonProperty("sedeNombre")
        String siteName,
        @JsonProperty("especialidadPublicId")
        String specialtyPublicId,
        @JsonProperty("especialidadNombre")
        String specialtyName,
        @JsonProperty("fechaHoraInicio")
        Instant startDateTime,
        @JsonProperty("fechaHoraFin")
        Instant endDateTime,
        @JsonProperty("modalidad")
        String modality,
        @JsonProperty("estado")
        String status
) {
}
