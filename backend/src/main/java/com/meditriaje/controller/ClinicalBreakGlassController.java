package com.meditriaje.controller;

import com.meditriaje.dto.clinical.BreakGlassAccessResponse;
import com.meditriaje.dto.clinical.ActivateBreakGlassRequest;
import com.meditriaje.service.BreakGlassService;
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
 * Controlador REST para el protocolo de acceso clínico excepcional de emergencia Break-Glass (ADR-007, ADR-017).
 * Restringido exclusivamente al rol ROLE_PROFESIONAL.
 */
@RestController
@RequestMapping("/api/v1/clinical/break-glass")
@PreAuthorize("hasAuthority('ROLE_PROFESIONAL')")
public class ClinicalBreakGlassController {

    private final BreakGlassService breakGlassService;

    public ClinicalBreakGlassController(BreakGlassService breakGlassService) {
        this.breakGlassService = Objects.requireNonNull(breakGlassService, "BreakGlassService no puede ser nulo");
    }

    /**
     * Activa una autorización excepcional de acceso Break-Glass ante una urgencia clínica (ADR-017).
     * Exige motivo de justificación de al menos 20 caracteres y otorga 24 horas de vigencia.
     */
    @PostMapping
    public ResponseEntity<BreakGlassAccessResponse> activateBreakGlass(
            @Valid @RequestBody ActivateBreakGlassRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);

        BreakGlassAccessResponse response = breakGlassService.activateBreakGlass(request, usuarioPublicId, sourceIp);
        URI location = URI.create("/api/v1/clinical/break-glass/" + response.publicId());

        return ResponseEntity.created(location).body(response);
    }

    /**
     * Consulta el listado de autorizaciones Break-Glass actualmente activas (no expiradas) para el profesional autenticado.
     */
    @GetMapping("/active")
    public ResponseEntity<List<BreakGlassAccessResponse>> listMyActiveAccesses(Authentication authentication) {
        String usuarioPublicId = authentication.getName();
        List<BreakGlassAccessResponse> accesos = breakGlassService.listMyActiveAccesses(usuarioPublicId);
        return ResponseEntity.ok(accesos);
    }

    /**
     * Consulta el detalle de una autorización Break-Glass específica por su identificador público.
     */
    @GetMapping("/{publicId}")
    public ResponseEntity<BreakGlassAccessResponse> getByPublicId(
            @PathVariable("publicId") String publicId,
            Authentication authentication
    ) {
        String usuarioPublicId = authentication.getName();
        BreakGlassAccessResponse response = breakGlassService.getByPublicId(publicId, usuarioPublicId);
        return ResponseEntity.ok(response);
    }
}
