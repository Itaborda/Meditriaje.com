package com.meditriaje.dto.hospital;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record MovementResponse(
        String publicId,
        @JsonProperty("episodioPublicId")
        String episodePublicId,
        @JsonProperty("areaOrigenNombre")
        String originAreaName,
        @JsonProperty("areaDestinoNombre")
        String destinationAreaName,
        @JsonProperty("camaOrigenCodigo")
        String originBedCode,
        @JsonProperty("camaDestinoCodigo")
        String destinationBedCode,
        @JsonProperty("fechaMovimiento")
        Instant movedAt,
        @JsonProperty("motivoTraslado")
        String transferReason,
        @JsonProperty("registradoPorNombre")
        String registeredByName
) {
}
