package com.meditriaje.model;

import java.time.Instant;

/**
 * Área hospitalaria funcional dentro de una sede (Fase H, ADR-024).
 * Ejemplo: Pabellón de Urgencias, UCI Adultos, Quirófanos, Hospitalización Piso 3.
 */
public record HospitalArea(
        Long id,
        String publicId,
        Long siteId,
        String code,
        String name,
        String type,
        String floor,
        String status,
        Instant createdAt
) {
}
