package com.meditriaje.dto.affiliation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record AbsenceResponse(
        String publicId,
        @JsonProperty("profesionalPublicId")
        String professionalPublicId,
        @JsonProperty("profesionalNombre")
        String professionalName,
        @JsonProperty("fechaInicio")
        Instant startDate,
        @JsonProperty("fechaFin")
        Instant endDate,
        @JsonProperty("motivo")
        String reason,
        @JsonProperty("estado")
        String status
) {
}
