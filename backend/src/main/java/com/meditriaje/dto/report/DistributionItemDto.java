package com.meditriaje.dto.report;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Elemento de distribución estadística para reportes operativos (F2.6, RF-30).
 */
public record DistributionItemDto(
        String publicId,
        @JsonProperty("etiqueta")
        String label,
        @JsonProperty("cantidad")
        long quantity,
        @JsonProperty("porcentaje")
        double percentage
) {
    public DistributionItemDto(String label, long quantity) {
        this(null, label, quantity, 0.0);
    }

    public DistributionItemDto(String label, long quantity, double percentage) {
        this(null, label, quantity, percentage);
    }
}

