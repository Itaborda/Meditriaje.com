package com.meditriaje.controller.admin;

import com.meditriaje.dto.admin.UpdateSpecialtyRequest;
import com.meditriaje.dto.admin.CreateSpecialtyRequest;
import com.meditriaje.dto.admin.SpecialtyResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.service.AdminCatalogService;
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
 * Controlador administrativo para la gestión de especialidades médicas (HU-10, ADR-002).
 * Requiere rol ROLE_ADMINISTRADOR para todas las operaciones.
 */
@RestController
@RequestMapping("/api/v1/admin/specialties")
@PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
public class AdminSpecialtyController {

    private final AdminCatalogService catalogService;

    public AdminSpecialtyController(AdminCatalogService catalogService) {
        this.catalogService = Objects.requireNonNull(catalogService, "AdminCatalogService no puede ser nulo");
    }

    @PostMapping
    public ResponseEntity<SpecialtyResponse> create(
            @Valid @RequestBody CreateSpecialtyRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String sourceIp = IpUtil.extractIp(httpRequest);
        SpecialtyResponse response = catalogService.createSpecialty(request, adminPublicId, sourceIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<SpecialtyResponse>> list(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "estado", required = false) String status
    ) {
        PaginatedResponse<SpecialtyResponse> response = catalogService.listSpecialties(page, size, status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<SpecialtyResponse> getByPublicId(@PathVariable("publicId") String publicId) {
        SpecialtyResponse response = catalogService.getSpecialty(publicId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{publicId}")
    public ResponseEntity<SpecialtyResponse> update(
            @PathVariable("publicId") String publicId,
            @Valid @RequestBody UpdateSpecialtyRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String sourceIp = IpUtil.extractIp(httpRequest);
        SpecialtyResponse response = catalogService.updateSpecialty(publicId, request, adminPublicId, sourceIp);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{publicId}/deactivate")
    public ResponseEntity<SpecialtyResponse> deactivate(
            @PathVariable("publicId") String publicId,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String sourceIp = IpUtil.extractIp(httpRequest);
        SpecialtyResponse response = catalogService.deactivateSpecialty(publicId, adminPublicId, sourceIp);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{publicId}/activate")
    public ResponseEntity<SpecialtyResponse> activate(
            @PathVariable("publicId") String publicId,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String sourceIp = IpUtil.extractIp(httpRequest);
        SpecialtyResponse response = catalogService.activateSpecialty(publicId, adminPublicId, sourceIp);
        return ResponseEntity.ok(response);
    }
}
