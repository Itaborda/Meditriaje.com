package com.meditriaje.dto.hospital;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta con el detalle de una habitación o sala hospitalaria.
 */
public record HospitalRoomResponse(
        String publicId,
        @JsonProperty("areaPublicId")
        String areaPublicId,
        @JsonProperty("codigo")
        String code,
        @JsonProperty("tipo")
        String type,
        @JsonProperty("estado")
        String status
) {
}
