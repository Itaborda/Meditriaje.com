package com.meditriaje.model;

import java.time.Instant;

/**
 * Registro médico de egreso y alta hospitalaria con epicrisis y destino (Fase H, ADR-024).
 * Destinos: ALTA_DOMICILIO, CONTRAREFERENCIA, TRASLADO_OTRA_IPS, HOSPITALIZACION_DOMICILIARIA, FALLECIMIENTO.
 */
public record HospitalDischarge(
        Long id,
        String publicId,
        Long episodeId,
        Long dischargingPhysicianId,
        Instant dischargeDate,
        String destinationType,
        String dischargeDiagnosis,
        String epicrisisSummary,
        String managementPlan,
        Instant createdAt
) {
}
