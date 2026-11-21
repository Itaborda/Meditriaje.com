package com.meditriaje.controller;

import com.meditriaje.dto.clinical.Cie10DiagnosisResponse;
import com.meditriaje.repository.Cie10DiagnosisRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * Controlador REST para el catálogo maestro de diagnósticos CIE-10 (ADR-013, HU-07).
 */
@RestController
@RequestMapping("/api/v1/catalogs/icd10")
public class Cie10CatalogController {

    private final Cie10DiagnosisRepository cie10DiagnosisRepository;

    public Cie10CatalogController(Cie10DiagnosisRepository cie10DiagnosisRepository) {
        this.cie10DiagnosisRepository = Objects.requireNonNull(cie10DiagnosisRepository, "DiagnosticoCie10Repository no puede ser nulo");
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Cie10DiagnosisResponse>> listDiagnoses(
            @RequestParam(name = "q", required = false) String query
    ) {
        List<Cie10DiagnosisResponse> resultados = cie10DiagnosisRepository.listActive(query);
        return ResponseEntity.ok(resultados);
    }
}
