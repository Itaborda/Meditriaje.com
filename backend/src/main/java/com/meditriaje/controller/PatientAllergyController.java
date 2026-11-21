package com.meditriaje.controller;

import com.meditriaje.dto.allergy.AllergyResponse;
import com.meditriaje.dto.allergy.InactivateAllergyRequest;
import com.meditriaje.dto.allergy.RegisterAllergyRequest;
import com.meditriaje.service.AllergyService;
import com.meditriaje.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * Endpoints para que el paciente gestione sus alergias autorreportadas (D3, T4).
 * Exclusivo para ROLE_PACIENTE.
 */
@RestController
@RequestMapping("/api/v1/patients/me/allergies")
@PreAuthorize("hasAuthority('ROLE_PACIENTE')")
public class PatientAllergyController {

    private final AllergyService allergyService;

    public PatientAllergyController(AllergyService allergyService) {
        this.allergyService = Objects.requireNonNull(allergyService, "AllergyService no puede ser nulo");
    }

    /**
     * Consulta las alergias del propio paciente autenticado.
     */
    @GetMapping
    public ResponseEntity<List<AllergyResponse>> listMyAllergies(
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);

        List<AllergyResponse> alergias = allergyService.listMyAllergies(usuarioPublicId, sourceIp);
        return ResponseEntity.ok(alergias);
    }

    /**
     * Registra una nueva alergia autorreportada por el paciente.
     */
    @PostMapping
    public ResponseEntity<AllergyResponse> registerMyAllergy(
            @Valid @RequestBody RegisterAllergyRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);

        AllergyResponse response = allergyService.registerMyAllergy(request, usuarioPublicId, sourceIp);
        URI location = URI.create("/api/v1/patients/me/allergies/" + response.publicId());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Inactiva una alergia autorreportada con motivo obligatorio.
     * Solo permite inactivar alergias registradas por el propio paciente.
     */
    @PatchMapping("/{publicId}/deactivate")
    public ResponseEntity<AllergyResponse> inactivateMyAllergy(
            @PathVariable("publicId") String publicId,
            @Valid @RequestBody InactivateAllergyRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);

        AllergyResponse response = allergyService.inactivateMyAllergy(publicId, request, usuarioPublicId, sourceIp);
        return ResponseEntity.ok(response);
    }
}
