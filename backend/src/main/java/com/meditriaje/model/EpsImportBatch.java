package com.meditriaje.model;

import java.time.Instant;

/**
 * Cabecera de auditoría y staging para la importación masiva de afiliados EPS (Fase A, ADR-025).
 */
public record EpsImportBatch(
        Long id,
        String publicId,
        String fileName,
        String hashSha256,
        Long epsId,
        int totalRows,
        int validRows,
        int failedRows,
        String status,
        Long uploadedByUserId,
        Instant createdAt,
        Instant processedAt
) {
}
