package com.meditriaje.dto.hospital;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ProcedureResponse(
        String publicId,
        @JsonProperty("episodioPublicId")
        String episodePublicId,
        @JsonProperty("tipoProcedimiento")
        String procedureType,
        @JsonProperty("descripcion")
        String description,
        @JsonProperty("profesionalNombre")
        String professionalName,
        @JsonProperty("salaCodigo")
        String roomCode,
        @JsonProperty("estado")
        String status,
        @JsonProperty("inicioAt")
        Instant startedAt,
        @JsonProperty("finAt")
        Instant endedAt,
        @JsonProperty("observaciones")
        String notes,
        @JsonProperty("creadoAt")
        Instant createdAt
) {
}
