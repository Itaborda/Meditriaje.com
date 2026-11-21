package com.meditriaje.controller.admin;

import com.meditriaje.dto.audit.AuditRecordResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.service.AuditService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Controlador para la supervisión y visor de la bitácora de auditoría de seguridad (F2.7, RF-26, RNF-11, ADR-019).
 * Restringido exclusivamente a ROLE_ADMINISTRADOR.
 * Proyecta metadatos técnicos y de seguridad con cero datos clínicos (ADR-007).
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
public class AdminAuditController {

    private final AuditService auditService;

    public AdminAuditController(AuditService auditService) {
        this.auditService = Objects.requireNonNull(auditService, "auditoriaService no puede ser nulo");
    }

    /**
     * Consulta paginada y filtrada de eventos de la bitácora de auditoría.
     */
    @GetMapping
    public ResponseEntity<PaginatedResponse<AuditRecordResponse>> queryAuditLog(
            @RequestParam(value = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "accion", required = false) String action,
            @RequestParam(value = "resultado", required = false) String result,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        PaginatedResponse<AuditRecordResponse> response =
                auditService.queryAuditLog(fromDate, toDate, action, result, page, size);
        return ResponseEntity.ok(response);
    }
}
