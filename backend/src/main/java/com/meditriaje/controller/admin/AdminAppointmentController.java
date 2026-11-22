package com.meditriaje.controller.admin;

import com.meditriaje.dto.admin.AdminAppointmentDetailResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.service.AdminAppointmentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Controlador administrativo para la supervisión, visor de calendario y exportación
 * de citas médicas (ADR-003, ADR-006, ADR-007, RF-26).
 * Acceso restringido exclusivamente a ROLE_ADMINISTRADOR.
 */
@RestController
@RequestMapping("/api/v1/admin/appointments")
@PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
public class AdminAppointmentController {

    private final AdminAppointmentService adminAppointmentService;

    public AdminAppointmentController(AdminAppointmentService adminAppointmentService) {
        this.adminAppointmentService = Objects.requireNonNull(adminAppointmentService, "adminAppointmentService no puede ser nulo");
    }

    /**
     * Consulta paginada y filtrada de citas para el visor y calendario administrativo.
     */
    @GetMapping
    public ResponseEntity<PaginatedResponse<AdminAppointmentDetailResponse>> listAppointments(
            @RequestParam(value = "fechaDesde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(value = "fechaHasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(value = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "profesionalPublicId", required = false) String professionalPublicId,
            @RequestParam(value = "especialidadPublicId", required = false) String specialtyPublicId,
            @RequestParam(value = "estado", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size
    ) {
        LocalDate fDesde = dateFrom != null ? dateFrom : fromDate;
        LocalDate fHasta = dateTo != null ? dateTo : toDate;

        PaginatedResponse<AdminAppointmentDetailResponse> response = adminAppointmentService.listAppointments(
                fDesde,
                fHasta,
                professionalPublicId,
                specialtyPublicId,
                status,
                page,
                size
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Exportación de citas a archivo CSV compatible con Microsoft Excel en formato nativo con BOM UTF-8.
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportAppointments(
            @RequestParam(value = "fechaDesde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(value = "fechaHasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(value = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "profesionalPublicId", required = false) String professionalPublicId,
            @RequestParam(value = "especialidadPublicId", required = false) String specialtyPublicId,
            @RequestParam(value = "estado", required = false) String status
    ) {
        LocalDate fDesde = dateFrom != null ? dateFrom : fromDate;
        LocalDate fHasta = dateTo != null ? dateTo : toDate;

        byte[] contenido = adminAppointmentService.exportAppointmentsCsv(
                fDesde,
                fHasta,
                professionalPublicId,
                specialtyPublicId,
                status
        );

        String fechaSufijo = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String filename = "citas_meditriaje_" + fechaSufijo + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(contenido);
    }
}
