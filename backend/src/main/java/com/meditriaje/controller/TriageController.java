package com.meditriaje.controller;

import com.meditriaje.dto.triage.SymptomCatalogResponse;
import com.meditriaje.dto.triage.CreateTriageRequest;
import com.meditriaje.dto.triage.TriageResponse;
import com.meditriaje.service.TriageService;
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
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * Controlador REST para la evaluación, consulta y catálogos de triaje clínico (ADR-002, ADR-003, ADR-009, HU-02).
 */
@RestController
@RequestMapping("/api/v1/triage")
public class TriageController {

    private final TriageService triageService;

    public TriageController(TriageService triageService) {
        this.triageService = Objects.requireNonNull(triageService, "TriajeService no puede ser nulo");
    }

    /**
     * Endpoint para evaluar y registrar un nuevo triaje clínico.
     * Solo accesible por pacientes autenticados (ROLE_PACIENTE).
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_PACIENTE')")
    public ResponseEntity<TriageResponse> createTriage(
            @Valid @RequestBody CreateTriageRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);

        TriageResponse response = triageService.evaluateAndSaveTriage(request, usuarioPublicId, sourceIp);
        URI location = URI.create("/api/v1/triage/" + response.publicId());

        return ResponseEntity.created(location).body(response);
    }

    /**
     * Endpoint para consultar el resultado y síntomas de un triaje por su identificador público UUID.
     * Accesible por usuarios autenticados con validación estricta de aislamiento.
     */
    @GetMapping("/{publicId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TriageResponse> getTriage(
            @PathVariable("publicId") String publicId,
            Authentication authentication
    ) {
        String usuarioPublicId = authentication.getName();
        TriageResponse response = triageService.getByPublicId(publicId, usuarioPublicId, authentication.getAuthorities());
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint para consultar el catálogo de síntomas activos del sistema.
     */
    @GetMapping("/symptoms")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SymptomCatalogResponse>> getSymptoms() {
        List<SymptomCatalogResponse> catalogo = triageService.getSymptomCatalog();
        return ResponseEntity.ok(catalogo);
    }
}
