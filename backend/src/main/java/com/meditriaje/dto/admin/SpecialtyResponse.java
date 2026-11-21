package com.meditriaje.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SpecialtyResponse(
        String publicId,
        @JsonProperty("nombre")
        String name,
        @JsonProperty("duracionSlotMin")
        int slotDurationMinutes,
        @JsonProperty("estado")
        String status
) {}
