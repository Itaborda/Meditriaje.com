package com.meditriaje.dto.emergency;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Respuesta con el detalle de un episodio de urgencias (Fase U, ADR-022).
 */
public record EmergencyEpisodeResponse(
        @JsonProperty("episodioPublicId")
        String episodePublicId,
        @JsonProperty("pacientePublicId")
        String patientPublicId,
        @JsonProperty("pacienteNombreCompleto")
        String patientFullName,
        @JsonProperty("sedePublicId")
        String sitePublicId,
        @JsonProperty("sedeNombre")
        String siteName,
        @JsonProperty("tipo")
        String type,
        @JsonProperty("estado")
        String status,
        @JsonProperty("esIdentidadProvisional")
        boolean isProvisionalIdentity,
        @JsonProperty("codigoProvisional")
        String provisionalCode,
        @JsonProperty("identidadEstado")
        String identityStatus,
        @JsonProperty("medioLlegada")
        String arrivalMode,
        @JsonProperty("motivoLlegada")
        String arrivalReason,
        @JsonProperty("nivelTriajeActual")
        String currentTriageLevel,
        @JsonProperty("profesionalTratanteNombre")
        String attendingProfessionalName,
        @JsonProperty("ingresoAt")
        Instant admittedAt,
        @JsonProperty("egresoAt")
        Instant dischargedAt,
        @JsonProperty("creadoAt")
        Instant createdAt
) {
}
