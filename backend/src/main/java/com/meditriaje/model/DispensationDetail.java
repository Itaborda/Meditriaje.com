package com.meditriaje.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Modelo de dominio inmutable para el detalle de entrega farmacéutica {@code DISPENSACION_DETALLE} (F2.4, ADR-016).
 * Inmutable tras su inserción.
 */
public record DispensationDetail(
        Long id,
        Long dispensationId,
        Long prescriptionDetailId,
        int deliveredQuantity,
        String batch,
        LocalDate batchExpirationDate,
        Instant createdAt
) {
    public DispensationDetail {
        Objects.requireNonNull(prescriptionDetailId, "recetaDetalleId no puede ser nulo");
        if (deliveredQuantity <= 0) {
            throw new IllegalArgumentException("La cantidad entregada debe ser mayor a 0.");
        }
    }
}
