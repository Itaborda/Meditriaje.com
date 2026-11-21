package com.meditriaje.dto.hospital;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record BedCensusResponse(
        @JsonProperty("sedePublicId")
        String sitePublicId,
        @JsonProperty("sedeNombre")
        String siteName,
        @JsonProperty("totalCamas")
        int totalBeds,
        @JsonProperty("ocupadas")
        int occupied,
        @JsonProperty("disponibles")
        int available,
        @JsonProperty("enLimpieza")
        int inCleaning,
        @JsonProperty("enMantenimiento")
        int inMaintenance,
        @JsonProperty("tasaOcupacionPorcentaje")
        double occupancyRatePercentage,
        @JsonProperty("areas")
        List<AreaCensusItem> areas
) {
    public record AreaCensusItem(
            @JsonProperty("areaPublicId")
            String areaPublicId,
            @JsonProperty("areaCodigo")
            String areaCode,
            @JsonProperty("areaNombre")
            String areaName,
            @JsonProperty("tipoArea")
            String areaType,
            @JsonProperty("totalCamas")
            int totalBeds,
            @JsonProperty("ocupadas")
            int occupied,
            @JsonProperty("disponibles")
            int available
    ) {
    }
}
