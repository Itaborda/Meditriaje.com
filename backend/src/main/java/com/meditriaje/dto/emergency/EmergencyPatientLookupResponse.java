package com.meditriaje.dto.emergency;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * Resultado de búsqueda de paciente por número de documento para admisión y triaje presencial.
 */
public record EmergencyPatientLookupResponse(
        String publicId,
        @JsonProperty("tipoDocumento")
        String documentType,
        @JsonProperty("numeroDocumento")
        String documentNumber,
        @JsonProperty("nombres")
        String firstNames,
        @JsonProperty("apellidos")
        String lastNames,
        @JsonProperty("nombreCompleto")
        String fullName,
        @JsonProperty("fechaNacimiento")
        LocalDate birthDate,
        @JsonProperty("telefono")
        String phone,
        @JsonProperty("tieneCuentaMediTriaje")
        boolean hasAccount
) {
}
