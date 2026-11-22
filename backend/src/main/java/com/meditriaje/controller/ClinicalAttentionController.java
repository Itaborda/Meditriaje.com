package com.meditriaje.controller;

import com.meditriaje.dto.clinical.ClinicalAttentionResponse;
import com.meditriaje.dto.clinical.CloseAttentionRequest;
import com.meditriaje.dto.clinical.CreateAmendmentRequest;
import com.meditriaje.dto.clinical.AmendmentResponse;
import com.meditriaje.dto.clinical.StartAttentionRequest;
import com.meditriaje.service.ClinicalAttentionService;
import com.meditriaje.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Collection;
import java.util.Objects;

/**
 * Controlador REST para el ciclo de vida de la atención médica (HU-07, HU-09, ADR-007, ADR-008).
 */
@RestController
@RequestMapping("/api/v1/attentions")
public class ClinicalAttentionController {

    private final ClinicalAttentionService clinicalAttentionService;

    public ClinicalAttentionController(ClinicalAttentionService clinicalAttentionService) {
        this.clinicalAttentionService = Objects.requireNonNull(clinicalAttentionService, "ClinicalAttentionService no puede ser nulo");
    }

    /**
     * Inicia una atención médica vinculada a una cita.
     * Restringido exclusivamente al profesional asignado a la cita (ADR-007).
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_PROFESIONAL')")
    public ResponseEntity<ClinicalAttentionResponse> startAttention(
            @Valid @RequestBody StartAttentionRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);

        ClinicalAttentionResponse response = clinicalAttentionService.startAttention(request, usuarioPublicId, sourceIp);
        URI location = URI.create("/api/v1/attentions/" + response.publicId());

        return ResponseEntity.created(location).body(response);
    }

    /**
     * Cierra y consolida una atención médica de forma inmutable (ADR-008, HU-07).
     * Pasa la cita a ATENDIDA y audita el evento.
     */
    @PostMapping("/{publicId}/close")
    @PreAuthorize("hasAuthority('ROLE_PROFESIONAL')")
    public ResponseEntity<ClinicalAttentionResponse> closeAttention(
            @PathVariable("publicId") String publicId,
            @Valid @RequestBody CloseAttentionRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);

        ClinicalAttentionResponse response = clinicalAttentionService.closeAttention(publicId, request, usuarioPublicId, sourceIp);
        return ResponseEntity.ok(response);
    }

    /**
     * Consulta el detalle de una atención médica.
     * Requiere relación asistencial o ser el paciente dueño (ADR-007).
     */
    @GetMapping("/{publicId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ClinicalAttentionResponse> getAttention(
            @PathVariable("publicId") String publicId,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        String sourceIp = IpUtil.extractIp(httpRequest);

        ClinicalAttentionResponse response = clinicalAttentionService.getByPublicId(publicId, usuarioPublicId, authorities, sourceIp);
        return ResponseEntity.ok(response);
    }

    /**
     * Registra una enmienda o aclaración médica inmutable sobre una atención cerrada (ADR-008, HU-07).
     * Restringido exclusivamente al rol profesional con relación asistencial o autor.
     */
    @PostMapping("/{publicId}/amendments")
    @PreAuthorize("hasAuthority('ROLE_PROFESIONAL')")
    public ResponseEntity<AmendmentResponse> createAmendment(
            @PathVariable("publicId") String publicId,
            @Valid @RequestBody CreateAmendmentRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);

        AmendmentResponse response = clinicalAttentionService.createAmendment(publicId, request, usuarioPublicId, sourceIp);
        URI location = URI.create("/api/v1/attentions/" + publicId);

        return ResponseEntity.created(location).body(response);
    }
}
