package com.meditriaje.dto.hospital;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TransferPatientRequest(
        @JsonProperty("camaDestinoPublicId")
        @NotBlank(message = "camaDestinoPublicId es obligatorio")
        String destinationBedPublicId,

        @JsonProperty("motivoTraslado")
        @NotBlank(message = "motivoTraslado es obligatorio")
        @Size(max = 500, message = "motivoTraslado no debe superar 500 caracteres")
        String transferReason
) {
}