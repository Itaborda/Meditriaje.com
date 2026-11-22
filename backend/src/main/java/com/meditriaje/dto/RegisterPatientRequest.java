package com.meditriaje.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Petición de registro de paciente (HU-01).
 * Incluye documento, datos personales, credenciales (contraseña ≥10) y consentimiento obligatorio.
 */
public record RegisterPatientRequest(
        @JsonProperty("tipoDocumento")
        @NotBlank(message = "El tipo de documento es obligatorio")
        @Pattern(regexp = "^(CC|TI|RC|CE|PA)$", message = "El tipo de documento debe ser CC, TI, RC, CE o PA")
        String documentType,

        @JsonProperty("numeroDocumento")
        @NotBlank(message = "El numero de documento es obligatorio")
        @Size(max = 20, message = "El numero de documento no debe exceder 20 caracteres")
        String documentNumber,

        @JsonProperty("nombres")
        @NotBlank(message = "Los nombres son obligatorios")
        @Size(max = 60, message = "Los nombres no deben exceder 60 caracteres")
        String firstNames,

        @JsonProperty("apellidos")
        @NotBlank(message = "Los apellidos son obligatorios")
        @Size(max = 60, message = "Los apellidos no deben exceder 60 caracteres")
        String lastNames,

        @JsonProperty("fechaNacimiento")
        @NotNull(message = "La fecha de nacimiento es obligatoria")
        @Past(message = "La fecha de nacimiento debe corresponder al pasado")
        LocalDate birthDate,

        @JsonProperty("telefono")
        @Size(max = 20, message = "El telefono no debe exceder 20 caracteres")
        String phone,

        @NotBlank(message = "El correo electronico es obligatorio")
        @Email(message = "El correo electronico debe ser valido")
        @Size(max = 100, message = "El correo no debe exceder 100 caracteres")
        String email,

        @NotBlank(message = "La contrasena es obligatoria")
        @Size(min = 10, max = 100, message = "La contrasena debe tener al menos 10 caracteres")
        String password,

        @JsonProperty("consentimientoTextoVersion")
        @NotBlank(message = "La version del texto del consentimiento es obligatoria")
        @Size(max = 20, message = "La version del consentimiento no debe exceder 20 caracteres")
        String consentTextVersion,

        @JsonProperty("aceptaConsentimiento")
        @NotNull(message = "El consentimiento informado es obligatorio")
        @AssertTrue(message = "Debe aceptar el consentimiento informado de tratamiento de datos personales")
        Boolean acceptsConsent
) {}
