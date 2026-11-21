package com.meditriaje.model;

import java.time.Instant;

/**
 * Procedimiento quirúrgico o invasivo realizado durante el episodio hospitalario (Fase H, ADR-024).
 * Estados: PROGRAMADO, EN_CURSO, RECUPERACION, FINALIZADO, CANCELADO.
 */
public record HospitalProcedure(
        Long id,
        String publicId,
        Long episodeId,
        String procedureType,
        String description,
        Long professionalId,
        Long procedureRoomId,
        String status,
        Instant startedAt,
        Instant endedAt,
        String notes,
        Instant createdAt
) {
}
