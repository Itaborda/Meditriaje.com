package com.meditriaje.model;

/**
 * Fila en staging previo al commit de un lote de importación masiva EPS (Fase A, ADR-025).
 */
public record EpsImportDetail(
        Long id,
        Long batchId,
        int rowNumber,
        String documentType,
        String documentNumber,
        String firstNames,
        String lastNames,
        String regime,
        String affiliateType,
        String rowStatus,
        String errorReason
) {
}
