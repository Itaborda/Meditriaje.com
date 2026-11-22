package com.meditriaje.dto.hospital;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BedDetailResponse(
        String publicId,
        @JsonProperty("habitacionPublicId")
        String roomPublicId,
        @JsonProperty("habitacionCodigo")
        String roomCode,
        @JsonProperty("areaPublicId")
        String areaPublicId,
        @JsonProperty("areaNombre")
        String areaName,
        @JsonProperty("sedePublicId")
        String sitePublicId,
        @JsonProperty("sedeNombre")
        String siteName,
        @JsonProperty("codigo")
        String code,
        @JsonProperty("estado")
        String status,
        @JsonProperty("episodioPublicId")
        String episodePublicId,
        @JsonProperty("pacienteNombre")
        String patientName,
        @JsonProperty("ocupacionPublicId")
        String occupancyPublicId
) {
}
