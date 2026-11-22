package com.meditriaje.model;

import java.time.Instant;

/**
 * Traslado intrahospitalario inmutable del paciente (Fase H, ADR-024).
 * Registra origen, destino, motivo y profesional responsable.
 */
public record PatientMovement(
        Long id,
        String publicId,
        Long episodeId,
        Long originAreaId,
        Long destinationAreaId,
        Long originBedId,
        Long destinationBedId,
        Instant movedAt,
        String transferReason,
        Long registeredByUserId,
        Instant createdAt
) {
}
