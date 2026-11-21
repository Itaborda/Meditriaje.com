package com.meditriaje.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Respuesta DTO para el profesional asistencial (HU-10, Ley 1164/2007).
 */
public record ProfessionalResponse(
        String publicId,
        @JsonProperty("usuarioPublicId")
        String userPublicId,
        @JsonProperty("tipoDocumento")
        String documentType,
        @JsonProperty("numeroDocumento")
        String documentNumber,
        @JsonProperty("registroMedico")
        String medicalLicense,
        @JsonProperty("nombres")
        String firstNames,
        @JsonProperty("apellidos")
        String lastNames,
        String email,
        @JsonProperty("telefono")
        String phone,
        @JsonProperty("especialidadPublicId")
        String specialtyPublicId,
        @JsonProperty("especialidadNombre")
        String specialtyName,
        @JsonProperty("estado")
        String status,
        @JsonProperty("debeCambiarPassword")
        boolean mustChangePassword,
        Instant createdAt
) {
    public ProfessionalResponse(
            String publicId,
            String userPublicId,
            String medicalLicense,
            String firstNames,
            String lastNames,
            String email,
            String specialtyPublicId,
            String specialtyName,
            String status,
            boolean mustChangePassword,
            Instant createdAt
    ) {
        this(
                publicId,
                userPublicId,
                "CC",
                null,
                medicalLicense,
                firstNames,
                lastNames,
                email,
                null,
                specialtyPublicId,
                specialtyName,
                status,
                mustChangePassword,
                createdAt
        );
    }
}
