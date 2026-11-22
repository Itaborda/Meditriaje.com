package com.meditriaje.controller;

import com.meditriaje.dto.allergy.AllergyResponse;
import com.meditriaje.dto.allergy.InactivateAllergyRequest;
import com.meditriaje.dto.allergy.RegisterAllergyRequest;
import com.meditriaje.service.AllergyService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * Endpoints asistenciales para gestión de alergias e hipersensibilidades por profesionales de salud (D3, T4, ADR-007).
 * Exclusivo para ROLE_PROFESIONAL con relación asistencial activa (break-glass es de solo lectura).
 */
@RestController
@RequestMapping("/api/v1/clinical")
@PreAuthorize("hasAuthority('ROLE_PROFESIONAL')")
public class ClinicalAllergyController {

    private final AllergyService allergyService;

    public ClinicalAllergyController(AllergyService allergyService) {
        this.allergyService = Objects.requireNonNull(allergyService, "AllergyService no puede ser nulo");
    }

    /**
     * Consulta las alergias de un paciente (activas por defecto; ?includeInactive=true).
     */
    @GetMapping("/patients/{patientPublicId}/allergies")
    public ResponseEntity<List<AllergyResponse>> listPatientAllergies(
            @PathVariable("patientPublicId") String patientPublicId,
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);

        List<AllergyResponse> alergias = allergyService.listPatientAllergiesForProfessional(
                patientPublicId,
                includeInactive,
                usuarioPublicId,
                authentication.getAuthorities(),
                sourceIp
        );

        return ResponseEntity.ok(alergias);
    }

    /**
     * Registra una alergia clínica diagnosticada para el paciente.
     * Break-Glass no permite registro (solo lectura).
     */
    @PostMapping("/patients/{patientPublicId}/allergies")
    public ResponseEntity<AllergyResponse> registerAllergy(
            @PathVariable("patientPublicId") String patientPublicId,
            @Valid @RequestBody RegisterAllergyRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);

        AllergyResponse response = allergyService.registerAllergyByProfessional(
                patientPublicId,
                request,
                usuarioPublicId,
                sourceIp
        );

        URI location = URI.create("/api/v1/clinical/allergies/" + response.publicId());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Inactiva una alergia con justificación clínica obligatoria.
     */
    @PatchMapping("/allergies/{publicId}/deactivate")
    public ResponseEntity<AllergyResponse> inactivateAllergy(
            @PathVariable("publicId") String publicId,
            @Valid @RequestBody InactivateAllergyRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);

        AllergyResponse response = allergyService.inactivateAllergyByProfessional(
                publicId,
                request,
                usuarioPublicId,
                sourceIp
        );

        return ResponseEntity.ok(response);
    }
}
