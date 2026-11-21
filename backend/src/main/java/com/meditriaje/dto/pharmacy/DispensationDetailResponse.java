package com.meditriaje.dto.pharmacy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Respuesta para un ítem dispensado en una entrega farmacéutica (F2.4, ADR-016).
 */
public record DispensationDetailResponse(
        @JsonProperty("medicamentoPublicId")
        String medicationPublicId,
        @JsonProperty("medicamentoCodigo")
        String medicationCode,
        @JsonProperty("nombreComercial")
        String brandName,
        @JsonProperty("cantidadEntregada")
        int deliveredQuantity,
        @JsonProperty("lote")
        String batch,
        @JsonProperty("fechaVencimientoLote")
        LocalDate batchExpirationDate,
        Instant createdAt
) {}
