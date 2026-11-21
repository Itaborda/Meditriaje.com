package com.meditriaje.model;

import java.util.Objects;

/**
 * Modelo de dominio inmutable para el detalle de prescripción {@code RECETA_DETALLE} (M7.1, HU-08, ADR-008).
 * Contiene snapshot inmutable del medicamento en el instante de prescripción.
 */
public record PrescriptionDetail(
        Long id,
        Long prescriptionId,
        Long medicationId,
        String nameSnapshot,
        String activeIngredientSnapshot,
        String presentationSnapshot,
        String concentrationSnapshot,
        String dose,
        String frequency,
        int durationDays,
        int quantity,
        String instructions
) {
    public PrescriptionDetail {
        Objects.requireNonNull(nameSnapshot, "snapshotNombre no puede ser nulo");
        Objects.requireNonNull(activeIngredientSnapshot, "snapshotPrincipioActivo no puede ser nulo");
        Objects.requireNonNull(presentationSnapshot, "snapshotPresentacion no puede ser nula");
        Objects.requireNonNull(concentrationSnapshot, "snapshotConcentracion no puede ser nula");
        Objects.requireNonNull(dose, "dosis no puede ser nula");
        Objects.requireNonNull(frequency, "frecuencia no puede ser nula");
        if (durationDays <= 0) {
            throw new IllegalArgumentException("La duracion en dias debe ser mayor a 0.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a 0.");
        }
    }
}
