package com.meditriaje.dto.clinical;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Solicitud obligatoria para cerrar y consolidar una atención médica inmutable (ADR-008, HU-07).
 */
public record CloseAttentionRequest(
        @JsonProperty("diagnosticoCie10Codigo")
        @NotBlank(message = "El codigo de diagnostico CIE-10 es obligatorio al cerrar la atencion.")
        @Size(max = 10, message = "El codigo CIE-10 no puede superar 10 caracteres.")
        String cie10DiagnosisCode,

        @JsonProperty("motivoConsulta")
        @NotBlank(message = "El motivo de consulta es obligatorio al cerrar la atencion.")
        @Size(max = 500, message = "El motivo de consulta no puede superar 500 caracteres.")
        String consultationReason,

        @JsonProperty("evolucion")
        @NotBlank(message = "La evolucion clinica es obligatoria al cerrar la atencion.")
        @Size(max = 4000, message = "La evolucion clinica no puede superar 4000 caracteres.")
        String progressNote,

        @JsonProperty("indicaciones")
        @NotBlank(message = "Las indicaciones medicas son obligatorias al cerrar la atencion.")
        @Size(max = 1000, message = "Las indicaciones no pueden superar 1000 caracteres.")
        String instructions,

        @JsonProperty("signosVitales")
        @Valid
        VitalSignsDto vitalSigns
) {
}
