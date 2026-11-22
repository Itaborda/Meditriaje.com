package com.meditriaje.controller;

import com.meditriaje.dto.followup.CreateFollowUpRequest;
import com.meditriaje.dto.followup.FollowUpResponse;
import com.meditriaje.service.FollowUpService;
import com.meditriaje.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Controlador REST para la gestión asistencial de tareas de seguimiento post-atención (ADR-015, HU-07, HU-09).
 */
@RestController
@RequestMapping("/api/v1")
public class FollowUpController {

    private final FollowUpService followUpService;

    public FollowUpController(FollowUpService followUpService) {
        this.followUpService = Objects.requireNonNull(followUpService, "FollowUpService no puede ser nulo");
    }

    /**
     * Prescribe una tarea de seguimiento post-atención médica vinculada a una atención cerrada (ADR-015).
     * Exclusivo para profesionales asistenciales (autor o con relación asistencial activa).
     */
    @PostMapping("/attentions/{atencionPublicId}/follow-ups")
    @PreAuthorize("hasAuthority('ROLE_PROFESIONAL')")
    public ResponseEntity<FollowUpResponse> prescribeFollowUp(
            @PathVariable("atencionPublicId") String attentionPublicId,
            @Valid @RequestBody CreateFollowUpRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);

        FollowUpResponse response = followUpService.prescribeFollowUp(
                attentionPublicId,
                request,
                usuarioPublicId,
                sourceIp
        );

        URI location = URI.create("/api/v1/follow-ups/" + response.publicId());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Consulta los seguimientos formulados en una atención médica específica.
     * Solo para profesional autor, profesional con relación activa, o el paciente dueño de la atención.
     */
    @GetMapping("/attentions/{atencionPublicId}/follow-ups")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<FollowUpResponse>> listByAttention(
            @PathVariable("atencionPublicId") String attentionPublicId,
            Authentication authentication
    ) {
        String usuarioPublicId = authentication.getName();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        List<FollowUpResponse> lista = followUpService.listByAttention(
                attentionPublicId,
                usuarioPublicId,
                authorities
        );

        return ResponseEntity.ok(lista);
    }

    /**
     * Consulta el detalle de un seguimiento puntual.
     * Restringido al paciente dueño o profesional asistencial autorizado. Administradores bloqueados (403).
     */
    @GetMapping("/follow-ups/{publicId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FollowUpResponse> getByPublicId(
            @PathVariable("publicId") String publicId,
            Authentication authentication
    ) {
        String usuarioPublicId = authentication.getName();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        FollowUpResponse response = followUpService.getByPublicId(
                publicId,
                usuarioPublicId,
                authorities
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Cancela una tarea de seguimiento pendiente.
     * Exclusivo para profesionales asistenciales autorizados.
     */
    @PatchMapping("/follow-ups/{publicId}/cancel")
    @PreAuthorize("hasAuthority('ROLE_PROFESIONAL')")
    public ResponseEntity<FollowUpResponse> cancelFollowUp(
            @PathVariable("publicId") String publicId,
            Authentication authentication
    ) {
        String usuarioPublicId = authentication.getName();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        FollowUpResponse response = followUpService.cancelFollowUp(
                publicId,
                usuarioPublicId,
                authorities
        );

        return ResponseEntity.ok(response);
    }
}
