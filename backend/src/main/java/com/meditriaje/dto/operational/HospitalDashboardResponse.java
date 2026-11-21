package com.meditriaje.dto.operational;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record HospitalDashboardResponse(
        @JsonProperty("sedePublicId")
        String sitePublicId,
        @JsonProperty("sedeNombre")
        String siteName,
        @JsonProperty("totalCamas")
        int totalBeds,
        @JsonProperty("camasDisponibles")
        int availableBeds,
        @JsonProperty("camasOcupadas")
        int occupiedBeds,
        @JsonProperty("camasLimpieza")
        int cleaningBeds,
        @JsonProperty("camasMantenimiento")
        int maintenanceBeds,
        @JsonProperty("tasaOcupacionPorcentaje")
        double occupancyRatePercentage,
        @JsonProperty("episodiosUrgenciasActivos")
        int activeEmergencyEpisodes,
        @JsonProperty("episodiosPorNivelTriaje")
        Map<String, Long> episodesByTriageLevel,
        @JsonProperty("tiemposPromedioEsperaMinutos")
        Map<String, Double> averageWaitMinutes,
        @JsonProperty("alertasActivas")
        List<OperationalAlertResponse> activeAlerts
) {
}
