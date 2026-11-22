package com.meditriaje.dto.hospital;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Respuesta con el detalle de un área hospitalaria y sus habitaciones/salas configuradas.
 */
public record HospitalAreaResponse(
        String publicId,
        @JsonProperty("codigo")
        String code,
        @JsonProperty("nombre")
        String name,
        @JsonProperty("tipo")
        String type,
        @JsonProperty("piso")
        String floor,
        @JsonProperty("estado")
        String status,
        @JsonProperty("habitaciones")
        List<HospitalRoomResponse> rooms
) {
    public HospitalAreaResponse {
        rooms = rooms == null ? List.of() : List.copyOf(rooms);
    }
}
