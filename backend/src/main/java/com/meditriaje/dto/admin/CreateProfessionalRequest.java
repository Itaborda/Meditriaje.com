package com.meditriaje.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Solicitud administrativa para dar de alta a un profesional asistencial (HU-10, ADR-002, ADR-003, Ley 1164/2007).
 */
public record CreateProfessionalRequest(
        @JsonProperty("tipoDocumento")
        String documentType,

        @JsonProperty("numeroDocumento")
        String documentNumber,

        @JsonProperty("registroMedico")
        @NotBlank(message = "El registro medico es obligatorio.")
        @Size(max = 30, message = "El registro medico no puede superar los 30 caracteres.")
        String medicalLicense,

        @JsonProperty("nombres")
        @NotBlank(message = "Los nombres son obligatorios.")
        @Size(max = 60, message = "Los nombres no pueden superar los 60 caracteres.")
        String firstNames,

        @JsonProperty("apellidos")
        @NotBlank(message = "Los apellidos son obligatorios.")
        @Size(max = 60, message = "Los apellidos no pueden superar los 60 caracteres.")
        String lastNames,

        @NotBlank(message = "El correo electronico es obligatorio.")
        @Email(message = "El formato del correo electronico es invalido.")
        @Size(max = 100, message = "El correo electronico no puede superar los 100 caracteres.")
        String email,

        @JsonProperty("telefono")
        String phone,

        @JsonProperty("especialidadPublicId")
        @NotBlank(message = "La especialidad es obligatoria.")
        String specialtyPublicId,

        @JsonProperty("rol")
        String role
) {
    public CreateProfessionalRequest {
        if (documentType == null || documentType.isBlank()) {
            documentType = "CC";
        }
        if (role == null || role.isBlank()) {
            role = "ROLE_PROFESIONAL";
        }
    }

    public CreateProfessionalRequest(
            String documentType,
            String documentNumber,
            String medicalLicense,
            String firstNames,
            String lastNames,
            String email,
            String phone,
            String specialtyPublicId
    ) {
        this(
                documentType,
                documentNumber,
                medicalLicense,
                firstNames,
                lastNames,
                email,
                phone,
                specialtyPublicId,
                "ROLE_PROFESIONAL"
        );
    }

    public CreateProfessionalRequest(
            String medicalLicense,
            String firstNames,
            String lastNames,
            String email,
            String specialtyPublicId
    ) {
        this(
                "CC",
                "10" + (Math.abs(medicalLicense != null ? medicalLicense.hashCode() % 90000000 + 10000000 : 12345678)),
                medicalLicense,
                firstNames,
                lastNames,
                email,
                null,
                specialtyPublicId,
                "ROLE_PROFESIONAL"
        );
    }
}
