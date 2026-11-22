package com.meditriaje.dto.pharmacy;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Solicitud de entrega para un ítem individual de una receta médica (F2.4, ADR-016).
 */
public record DeliveryDetailRequest(
        @JsonProperty("medicamentoPublicId")
        @NotBlank(message = "El identificador del medicamento es obligatorio.")
        String medicationPublicId,

        @JsonProperty("cantidadEntregada")
        @NotNull(message = "La cantidad a entregar es obligatoria.")
        @Min(value = 1, message = "La cantidad a entregar debe ser al menos 1.")
        Integer deliveredQuantity,

        @JsonProperty("lote")
        @Size(max = 50, message = "El lote no puede exceder 50 caracteres.")
        String batch,

        @JsonProperty("fechaVencimientoLote")
        LocalDate batchExpirationDate
) {}
