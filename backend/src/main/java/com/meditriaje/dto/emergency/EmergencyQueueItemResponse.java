package com.meditriaje.dto.emergency;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Elemento de la cola priorizada de urgencias por sede y nivel de triaje (Fase U, ADR-027, U05).
 */
public record EmergencyQueueItemResponse(
        @JsonProperty("episodioPublicId")
        String episodePublicId,
        @JsonProperty("pacientePublicId")
        String patientPublicId,
        @JsonProperty("identificadorVisible")
        String visibleIdentifier,
        @JsonProperty("codigoProvisional")
        String provisionalCode,
        @JsonProperty("sedePublicId")
        String sitePublicId,
        @JsonProperty("sedeNombre")
        String siteName,
        @JsonProperty("tipoEpisodio")
        String episodeType,
        @JsonProperty("estadoEpisodio")
        String episodeStatus,
        @JsonProperty("nivelTriaje")
        String triageLevel,
        @JsonProperty("motivoLlegada")
        String arrivalReason,
        @JsonProperty("minutosEspera")
        Long waitMinutes,
        @JsonProperty("ingresoAt")
        Instant admittedAt,
        @JsonProperty("profesionalTratanteNombre")
        String attendingProfessionalName
) {
}
