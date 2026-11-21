package com.meditriaje.dto.emergency.summary;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * Datos básicos del paciente visibles en el resumen de emergencia (ADR-010, §5.17).
 */
public record EmergencyPatientDto(
        @JsonProperty("nombreCompleto")
        String fullName,
        @JsonProperty("tipoDocumento")
        String documentType,
        @JsonProperty("numeroDocumento")
        String documentNumber,
        @JsonProperty("fechaNacimiento")
        LocalDate birthDate,
        @JsonProperty("edad")
        int age,
        @JsonProperty("telefono")
        String phone,
        String email
) {
}
