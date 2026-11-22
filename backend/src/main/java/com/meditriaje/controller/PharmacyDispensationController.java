package com.meditriaje.controller;

import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.dto.pharmacy.DispensationResponse;
import com.meditriaje.dto.pharmacy.PrescriptionDispensationResponse;
import com.meditriaje.dto.pharmacy.RegisterDispensationRequest;
import com.meditriaje.service.DispensationService;
import com.meditriaje.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * Controlador REST para el servicio de farmacia y dispensación de recetas médicas (F2.4, ADR-016).
 * Acceso estrictamente segregado: exclusivo para usuarios con {@code ROLE_FARMACEUTICO}.
 */
@RestController
@RequestMapping("/api/v1/pharmacy")
@PreAuthorize("hasAuthority('ROLE_FARMACEUTICO')")
public class PharmacyDispensationController {

    private final DispensationService dispensationService;

    public PharmacyDispensationController(DispensationService dispensationService) {
        this.dispensationService = Objects.requireNonNull(dispensationService, "DispensationService no puede ser nulo");
    }

    /**
     * Registra un evento de dispensación farmacéutica de medicamentos (F2.4, ADR-016).
     */
    @PostMapping("/dispensations")
    public ResponseEntity<DispensationResponse> registerDispensation(
            @Valid @RequestBody RegisterDispensationRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);

        DispensationResponse response = dispensationService.registerDispensation(
                request, usuarioPublicId, authentication.getAuthorities(), sourceIp
        );
        URI location = URI.create("/api/v1/pharmacy/dispensations/" + response.publicId());

        return ResponseEntity.created(location).body(response);
    }

    /**
     * Consulta una dispensación previamente registrada por su UUID público.
     */
    @GetMapping("/dispensations/{publicId}")
    public ResponseEntity<DispensationResponse> getDispensation(@PathVariable("publicId") String publicId) {
        DispensationResponse response = dispensationService.getDispensationByPublicId(publicId);
        return ResponseEntity.ok(response);
    }

    /**
     * Búsqueda de recetas médicas para ventanilla de farmacia por documento del paciente o código de reclamación.
     */
    @GetMapping("/prescriptions")
    public ResponseEntity<PaginatedResponse<PrescriptionDispensationResponse>> searchPrescriptions(
            @RequestParam(name = "query", required = false, defaultValue = "") String query,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Authentication authentication
    ) {
        PaginatedResponse<PrescriptionDispensationResponse> response = dispensationService.searchPrescriptionsForPharmacy(
                query, page, size, authentication.getAuthorities()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Consulta detallada de una receta médica con saldos disponibles y entregas previas para dispensación.
     */
    @GetMapping("/prescriptions/{publicId}")
    public ResponseEntity<PrescriptionDispensationResponse> getPrescription(
            @PathVariable("publicId") String publicId,
            Authentication authentication
    ) {
        PrescriptionDispensationResponse response = dispensationService.getPrescriptionForPharmacy(
                publicId, authentication.getAuthorities()
        );
        return ResponseEntity.ok(response);
    }
}
