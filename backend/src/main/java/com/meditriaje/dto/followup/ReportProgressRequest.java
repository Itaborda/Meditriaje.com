package com.meditriaje.dto.followup;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Solicitud del paciente para registrar su evolución en una tarea de seguimiento (ADR-015, §5.16).
 */
public record ReportProgressRequest(
        @JsonProperty("reporte")
        @NotBlank(message = "El reporte de evolucion es obligatorio.")
        @Size(max = 2000, message = "El reporte no puede exceder 2000 caracteres.")
        String report
) {
}
