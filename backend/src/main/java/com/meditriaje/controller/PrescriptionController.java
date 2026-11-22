package com.meditriaje.controller;

import com.meditriaje.dto.prescription.CreatePrescriptionRequest;
import com.meditriaje.dto.prescription.PrescriptionResponse;
import com.meditriaje.service.PrescriptionService;
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
import java.util.Objects;

/**
 * Controlador REST para el ciclo de prescripciones médicas (HU-08, ADR-007, ADR-008).
 */
@RestController
@RequestMapping("/api/v1/prescriptions")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    public PrescriptionController(PrescriptionService prescriptionService) {
        this.prescriptionService = Objects.requireNonNull(prescriptionService, "PrescriptionService no puede ser nulo");
    }

    /**
     * Emite una receta médica con ítems inmutables.
     * Restringido exclusivamente al profesional asistencial (ADR-007).
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_PROFESIONAL')")
    public ResponseEntity<PrescriptionResponse> issuePrescription(
            @Valid @RequestBody CreatePrescriptionRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);

        PrescriptionResponse response = prescriptionService.issuePrescription(request, usuarioPublicId, sourceIp);
        URI location = URI.create("/api/v1/prescriptions/" + response.publicId());

        return ResponseEntity.created(location).body(response);
    }

    /**
     * Consulta el detalle inmutable de una receta médica.
     * Valida permisos estrictos según rol: solo el paciente dueño o médico con relación activa.
     * Administradores bloqueados categóricamente (ADR-007).
     */
    @GetMapping("/{publicId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PrescriptionResponse> getPrescription(
            @PathVariable("publicId") String publicId,
            Authentication authentication
    ) {
        String usuarioPublicId = authentication.getName();
        PrescriptionResponse response = prescriptionService.getByPublicId(
                publicId,
                usuarioPublicId,
                authentication.getAuthorities()
        );

        return ResponseEntity.ok(response);
    }
}
