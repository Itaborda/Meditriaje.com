package com.meditriaje.dto.pharmacy;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Solicitud de registro de evento de dispensación en farmacia (F2.4, ADR-016).
 */
public record RegisterDispensationRequest(
        @JsonProperty("recetaPublicId")
        @NotBlank(message = "El identificador de la receta es obligatorio.")
        String prescriptionPublicId,

        @JsonProperty("observaciones")
        @Size(max = 500, message = "Las observaciones no pueden exceder 500 caracteres.")
        String notes,

        @JsonProperty("detalles")
        @NotEmpty(message = "Debe incluir al menos un detalle de entrega.")
        List<@Valid DeliveryDetailRequest> details
) {}
