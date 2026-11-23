package com.meditriaje.dto.emergency;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Petición de ingreso presencial y apertura de episodio de urgencias (Fase U, ADR-022, U02).
 * Admite:
 * 1) Paciente ya registrado (por {@code pacientePublicId} UUID o número de documento),
 * 2) Paciente civil presencial sin cuenta previa en MediTriaje (campos {@code *Paciente}), o
 * 3) Paciente provisional no identificado (NN).
 */
public record RegisterEmergencyAdmissionRequest(
        @JsonProperty("pacientePublicId")
        String patientPublicId,

        @JsonProperty("esIdentidadProvisional")
        boolean isProvisionalIdentity,

        @JsonProperty("medioLlegada")
        @NotBlank(message = "El medio de llegada es obligatorio.")
        String arrivalMode,

        @JsonProperty("motivoLlegada")
        @NotBlank(message = "El motivo de llegada es obligatorio.")
        @Size(max = 500, message = "El motivo de llegada no puede exceder 500 caracteres.")
        String arrivalReason,

        @JsonProperty("acompananteNombre")
        @Size(max = 150, message = "El nombre del acompañante no puede exceder 150 caracteres.")
        String companionName,

        @JsonProperty("acompananteContacto")
        @Size(max = 100, message = "El contacto del acompañante no puede exceder 100 caracteres.")
        String companionContact,

        @JsonProperty("observaciones")
        @Size(max = 1000, message = "Las observaciones no pueden exceder 1000 caracteres.")
        String notes,

        // Atributos de paciente no identificado (NN / Provisional)
        @JsonProperty("descripcionFisica")
        @Size(max = 500, message = "La descripción física no puede exceder 500 caracteres.")
        String physicalDescription,

        @JsonProperty("edadAparente")
        Integer apparentAge,

        @JsonProperty("generoAparente")
        String apparentGender,

        @JsonProperty("condicionLlegada")
        @Size(max = 100, message = "La condición de llegada no puede exceder 100 caracteres.")
        String arrivalCondition,

        // Atributos opcionales para paciente presencial sin cuenta previa en MediTriaje
        @JsonProperty("tipoDocumentoPaciente")
        String patientDocumentType,

        @JsonProperty("numeroDocumentoPaciente")
        @Size(max = 20, message = "El número de documento no puede exceder 20 caracteres.")
        String patientDocumentNumber,

        @JsonProperty("nombresPaciente")
        @Size(max = 60, message = "Los nombres no pueden exceder 60 caracteres.")
        String patientFirstNames,

        @JsonProperty("apellidosPaciente")
        @Size(max = 60, message = "Los apellidos no pueden exceder 60 caracteres.")
        String patientLastNames,

        @JsonProperty("fechaNacimientoPaciente")
        LocalDate patientBirthDate,

        @JsonProperty("telefonoPaciente")
        @Size(max = 20, message = "El teléfono no puede exceder 20 caracteres.")
        String patientPhone
) {
    public RegisterEmergencyAdmissionRequest(
            String patientPublicId,
            boolean isProvisionalIdentity,
            String arrivalMode,
            String arrivalReason,
            String companionName,
            String companionContact,
            String notes,
            String physicalDescription,
            Integer apparentAge,
            String apparentGender,
            String arrivalCondition
    ) {
        this(
                patientPublicId,
                isProvisionalIdentity,
                arrivalMode,
                arrivalReason,
                companionName,
                companionContact,
                notes,
                physicalDescription,
                apparentAge,
                apparentGender,
                arrivalCondition,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
