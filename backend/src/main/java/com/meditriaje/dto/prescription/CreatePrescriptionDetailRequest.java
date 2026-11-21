package com.meditriaje.dto.prescription;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO para la prescripción de un fármaco dentro de una receta médica.
 */
public record CreatePrescriptionDetailRequest(
        @JsonProperty("medicamentoPublicId")
        @NotBlank(message = "El identificador del medicamento es obligatorio.")
        String medicationPublicId,

        @JsonProperty("dosis")
        @NotBlank(message = "La dosis es obligatoria.")
        @Size(max = 100, message = "La dosis no puede exceder 100 caracteres.")
        String dose,

        @JsonProperty("frecuencia")
        @NotBlank(message = "La frecuencia es obligatoria.")
        @Size(max = 100, message = "La frecuencia no puede exceder 100 caracteres.")
        String frequency,

        @JsonProperty("duracionDias")
        @NotNull(message = "La duracion en dias es obligatoria.")
        @Min(value = 1, message = "La duracion minima es de 1 dia.")
        Integer durationDays,

        @JsonProperty("cantidad")
        @NotNull(message = "La cantidad es obligatoria.")
        @Min(value = 1, message = "La cantidad minima es de 1 unidad.")
        Integer quantity,

        @JsonProperty("indicaciones")
        @Size(max = 300, message = "Las indicaciones no pueden exceder 300 caracteres.")
        String instructions
) {}
