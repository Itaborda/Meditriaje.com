package com.meditriaje.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Respuesta a la creación exitosa de un profesional asistencial (HU-10, ADR-002, Ley 1164/2007).
 * Incluye la contraseña temporal generada en texto plano para que el administrador
 * se la suministre al médico de forma segura por única vez.
 */
public record CreateProfessionalResponse(
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
        @JsonProperty("passwordTemporal")
        String temporaryPassword,
        @JsonProperty("debeCambiarPassword")
        boolean mustChangePassword,
        Instant createdAt
) {
    public CreateProfessionalResponse(
            String publicId,
            String userPublicId,
            String medicalLicense,
            String firstNames,
            String lastNames,
            String email,
            String specialtyPublicId,
            String specialtyName,
            String temporaryPassword,
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
                temporaryPassword,
                mustChangePassword,
                createdAt
        );
    }
}
