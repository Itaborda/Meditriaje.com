package com.meditriaje.dto.audit;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Elemento de bitácora para el visor de auditoría de seguridad (RF-26, ADR-019).
 * Proyecta metadatos del evento sin exponer contenido clínico confidencial (ADR-007).
 */
public record AuditRecordResponse(
        Long id,
        @JsonProperty("usuarioEmail")
        String userEmail,
        @JsonProperty("accion")
        String action,
        @JsonProperty("tipoRecurso")
        String resourceType,
        @JsonProperty("recursoPublicId")
        String resourcePublicId,
        @JsonProperty("resultado")
        String result,
        @JsonProperty("ipOrigen")
        String sourceIp,
        @JsonProperty("fechaHora")
        Instant timestamp
) {}
