package com.meditriaje.dto.pharmacy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * Respuesta para un evento de dispensación en farmacia (F2.4, ADR-016).
 */
public record DispensationResponse(
        String publicId,
        @JsonProperty("recetaPublicId")
        String prescriptionPublicId,
        @JsonProperty("sedePublicId")
        String sitePublicId,
        @JsonProperty("sedeNombre")
        String siteName,
        @JsonProperty("dispensadorPublicId")
        String dispenserPublicId,
        @JsonProperty("dispensadorNombre")
        String dispenserName,
        @JsonProperty("observaciones")
        String notes,
        Instant createdAt,
        @JsonProperty("detalles")
        List<DispensationDetailResponse> details
) {}
