package com.meditriaje.dto.operational;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record TrackingQrResponse(
        String publicId,
        @JsonProperty("episodioPublicId")
        String episodePublicId,
        @JsonProperty("codigoIdentidadProvisional")
        String provisionalIdentityCode,
        @JsonProperty("tokenQr")
        String qrToken,
        @JsonProperty("urlSeguimiento")
        String trackingUrl,
        @JsonProperty("ubicacionActual")
        String currentLocation,
        @JsonProperty("estadoEpisodio")
        String episodeStatus,
        @JsonProperty("nivelTriaje")
        String triageLevel,
        @JsonProperty("estado")
        String status,
        @JsonProperty("creadoAt")
        Instant createdAt,
        @JsonProperty("expiraAt")
        Instant expiresAt
) {
}
