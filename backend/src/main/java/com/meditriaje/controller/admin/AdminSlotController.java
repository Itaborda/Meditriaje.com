package com.meditriaje.controller.admin;

import com.meditriaje.dto.admin.GenerateSlotsRequest;
import com.meditriaje.dto.admin.GenerateSlotsResponse;
import com.meditriaje.dto.admin.SlotResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.service.SlotGeneratorService;
import com.meditriaje.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Objects;

/**
 * Controlador administrativo para la gestión y generación en lote de slots de disponibilidad (HU-10, ADR-002, ADR-005, ADR-006).
 * Requiere estrictamente el rol ROLE_ADMINISTRADOR para todas las operaciones.
 */
@RestController
@RequestMapping("/api/v1/admin/slots")
@PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
public class AdminSlotController {

    private final SlotGeneratorService slotGeneratorService;

    public AdminSlotController(SlotGeneratorService slotGeneratorService) {
        this.slotGeneratorService = Objects.requireNonNull(slotGeneratorService, "SlotGeneratorService no puede ser nulo");
    }

    @PostMapping("/generate")
    public ResponseEntity<GenerateSlotsResponse> generateSlots(
            @Valid @RequestBody GenerateSlotsRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String sourceIp = IpUtil.extractIp(httpRequest);
        GenerateSlotsResponse response = slotGeneratorService.generateSlots(request, adminPublicId, sourceIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<SlotResponse>> list(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "profesionalPublicId", required = false) String professionalPublicId,
            @RequestParam(value = "especialidadPublicId", required = false) String specialtyPublicId,
            @RequestParam(value = "fechaDesde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
            @RequestParam(value = "fechaHasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo,
            @RequestParam(value = "estado", required = false) String status
    ) {
        PaginatedResponse<SlotResponse> response = slotGeneratorService.list(
                page, size, professionalPublicId, specialtyPublicId, dateFrom, dateTo, status
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<SlotResponse> getByPublicId(@PathVariable("publicId") String publicId) {
        SlotResponse response = slotGeneratorService.getByPublicId(publicId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{publicId}/block")
    public ResponseEntity<SlotResponse> block(
            @PathVariable("publicId") String publicId,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String sourceIp = IpUtil.extractIp(httpRequest);
        SlotResponse response = slotGeneratorService.blockSlot(publicId, adminPublicId, sourceIp);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{publicId}/unblock")
    public ResponseEntity<SlotResponse> unblock(
            @PathVariable("publicId") String publicId,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String sourceIp = IpUtil.extractIp(httpRequest);
        SlotResponse response = slotGeneratorService.unblockSlot(publicId, adminPublicId, sourceIp);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> delete(
            @PathVariable("publicId") String publicId,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String sourceIp = IpUtil.extractIp(httpRequest);
        slotGeneratorService.deleteSlot(publicId, adminPublicId, sourceIp);
        return ResponseEntity.noContent().build();
    }
}
