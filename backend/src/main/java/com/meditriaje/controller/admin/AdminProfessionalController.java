package com.meditriaje.controller.admin;

import com.meditriaje.dto.admin.UpdateProfessionalRequest;
import com.meditriaje.dto.admin.CreateProfessionalRequest;
import com.meditriaje.dto.admin.CreateProfessionalResponse;
import com.meditriaje.dto.admin.ProfessionalResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.service.AdminProfessionalService;
import com.meditriaje.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Controlador administrativo para la gestión de profesionales asistenciales (HU-10, ADR-002, ADR-003).
 * Requiere el rol ROLE_ADMINISTRADOR para todas las operaciones.
 */
@RestController
@RequestMapping("/api/v1/admin/professionals")
@PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
public class AdminProfessionalController {

    private final AdminProfessionalService professionalService;

    public AdminProfessionalController(AdminProfessionalService professionalService) {
        this.professionalService = Objects.requireNonNull(professionalService, "AdminProfessionalService no puede ser nulo");
    }

    @PostMapping
    public ResponseEntity<CreateProfessionalResponse> create(
            @Valid @RequestBody CreateProfessionalRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String sourceIp = IpUtil.extractIp(httpRequest);
        CreateProfessionalResponse response = professionalService.registerProfessional(request, adminPublicId, sourceIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<ProfessionalResponse>> list(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "especialidadPublicId", required = false) String specialtyPublicId,
            @RequestParam(value = "estado", required = false) String status
    ) {
        PaginatedResponse<ProfessionalResponse> response = professionalService.list(page, size, specialtyPublicId, status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<ProfessionalResponse> getByPublicId(@PathVariable("publicId") String publicId) {
        ProfessionalResponse response = professionalService.getByPublicId(publicId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{publicId}")
    public ResponseEntity<ProfessionalResponse> update(
            @PathVariable("publicId") String publicId,
            @Valid @RequestBody UpdateProfessionalRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String sourceIp = IpUtil.extractIp(httpRequest);
        ProfessionalResponse response = professionalService.updateProfessional(publicId, request, adminPublicId, sourceIp);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{publicId}/deactivate")
    public ResponseEntity<ProfessionalResponse> deactivate(
            @PathVariable("publicId") String publicId,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String sourceIp = IpUtil.extractIp(httpRequest);
        ProfessionalResponse response = professionalService.changeStatus(publicId, "INACTIVO", adminPublicId, sourceIp);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{publicId}/activate")
    public ResponseEntity<ProfessionalResponse> activate(
            @PathVariable("publicId") String publicId,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String sourceIp = IpUtil.extractIp(httpRequest);
        ProfessionalResponse response = professionalService.changeStatus(publicId, "ACTIVO", adminPublicId, sourceIp);
        return ResponseEntity.ok(response);
    }
}
