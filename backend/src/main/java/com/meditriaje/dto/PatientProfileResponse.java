package com.meditriaje.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * DTO con información de perfil del paciente autenticado (HU-09, ADR-003).
 * Solo expone publicId y datos demográficos; nunca IDs numéricos internos de base de datos.
 */
public record PatientProfileResponse(
        String publicId,
        @JsonProperty("tipoDocumento")
        String documentType,
        @JsonProperty("numeroDocumento")
        String documentNumber,
        @JsonProperty("nombres")
        String firstNames,
        @JsonProperty("apellidos")
        String lastNames,
        @JsonProperty("fechaNacimiento")
        LocalDate birthDate,
        @JsonProperty("telefono")
        String phone,
        String email
) {}
