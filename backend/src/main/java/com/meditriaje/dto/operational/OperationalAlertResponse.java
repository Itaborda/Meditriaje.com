package com.meditriaje.dto.operational;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record OperationalAlertResponse(
        String publicId,
        @JsonProperty("sedePublicId")
        String sitePublicId,
        @JsonProperty("sedeNombre")
        String siteName,
        @JsonProperty("tipoAlerta")
        String alertType,
        @JsonProperty("nivelSeveridad")
        String severityLevel,
        @JsonProperty("mensaje")
        String message,
        @JsonProperty("estado")
        String status,
        @JsonProperty("reconocidoPorEmail")
        String acknowledgedByEmail,
        @JsonProperty("reconocidoAt")
        Instant acknowledgedAt,
        @JsonProperty("motivoReconocimiento")
        String acknowledgmentReason,
        @JsonProperty("creadoAt")
        Instant createdAt
) {
}
