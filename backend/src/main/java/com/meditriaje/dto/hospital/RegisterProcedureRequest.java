package com.meditriaje.dto.hospital;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterProcedureRequest(
        @JsonProperty("tipoProcedimiento")
        @NotBlank(message = "tipoProcedimiento es obligatorio")
        @Size(max = 100, message = "tipoProcedimiento no debe superar 100 caracteres")
        String procedureType,

        @JsonProperty("descripcion")
        @NotBlank(message = "descripcion es obligatoria")
        @Size(max = 1000, message = "descripcion no debe superar 1000 caracteres")
        String description,

        @JsonProperty("profesionalPublicId")
        @NotBlank(message = "profesionalPublicId es obligatorio")
        String professionalPublicId,

        @JsonProperty("salaPublicId")
        String roomPublicId,

        @JsonProperty("observaciones")
        @Size(max = 1000, message = "observaciones no debe superar 1000 caracteres")
        String notes
) {
}
