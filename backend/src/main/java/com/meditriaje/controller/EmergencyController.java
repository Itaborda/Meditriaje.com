package com.meditriaje.controller;

import com.meditriaje.dto.emergency.AssignTeamRequest;
import com.meditriaje.dto.emergency.CalculateTriageLevelRequest;
import com.meditriaje.dto.emergency.EmergencyEpisodeResponse;
import com.meditriaje.dto.emergency.EmergencyPatientLookupResponse;
import com.meditriaje.dto.emergency.EmergencyQueueItemResponse;
import com.meditriaje.dto.emergency.ReconcileIdentityRequest;
import com.meditriaje.dto.emergency.RegisterEmergencyAdmissionRequest;
import com.meditriaje.dto.emergency.RegisterTriageAssessmentRequest;
import com.meditriaje.dto.emergency.TriageAssessmentResponse;
import com.meditriaje.dto.emergency.TriageCalculationResponse;
import com.meditriaje.service.EmergencyService;
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
import java.util.Map;
import java.util.Objects;

/**
 * Controlador REST para el circuito de admisiones de urgencias, identidades provisionales,
 * triaje clínico presencial y asignación asistencial (Fase U, ADR-022, ADR-023, ADR-026, ADR-027).
 */
@RestController
@RequestMapping("/api/v1/emergency")
@PreAuthorize("isAuthenticated()")
public class EmergencyController {

    private final EmergencyService emergencyService;

    public EmergencyController(EmergencyService emergencyService) {
        this.emergencyService = Objects.requireNonNull(emergencyService, "EmergencyService no puede ser nulo");
    }

    /**
     * Busca pacientes por número de documento para admisión o reconciliación presencial.
     */
    @GetMapping("/patients/search")
    @PreAuthorize("hasAnyAuthority('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL', 'ROLE_ADMINISTRADOR')")
    public ResponseEntity<List<EmergencyPatientLookupResponse>> searchPatients(
            @RequestParam("numeroDocumento") String documentNumber,
            @RequestParam(value = "tipoDocumento", required = false) String documentType
    ) {
        return ResponseEntity.ok(emergencyService.searchPatientsByDocument(documentNumber, documentType));
    }

    /**
     * Endpoint para registrar el ingreso presencial de urgencias (U02).
     * Retorna 201 Created con cabecera Location.
     */
    @PostMapping("/admissions")
    @PreAuthorize("hasAnyAuthority('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL', 'ROLE_ADMINISTRADOR')")
    public ResponseEntity<EmergencyEpisodeResponse> registerAdmission(
            @Valid @RequestBody RegisterEmergencyAdmissionRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);

        EmergencyEpisodeResponse response = emergencyService.registerEmergencyAdmission(request, usuarioPublicId, sourceIp);
        URI location = URI.create("/api/v1/emergency/admissions/" + response.episodePublicId());

        return ResponseEntity.created(location).body(response);
    }

    /**
     * Endpoint para consultar el detalle de un episodio de urgencias (U02).
     */
    @GetMapping({"/admissions/{episodePublicId}", "/episodes/{episodePublicId}"})
    public ResponseEntity<EmergencyEpisodeResponse> getEpisodeDetail(
            @PathVariable("episodePublicId") String episodePublicId,
            Authentication authentication
    ) {
        EmergencyEpisodeResponse response = emergencyService.getEpisodeDetail(
                episodePublicId,
                authentication.getName(),
                authentication.getAuthorities()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint para reconciliar una identidad provisional con un paciente civil verificado (U03).
     */
    @PostMapping({"/episodes/{episodePublicId}/reconcile", "/episodes/{episodePublicId}/reconcile-identity"})
    @PreAuthorize("hasAnyAuthority('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL', 'ROLE_ADMINISTRADOR')")
    public ResponseEntity<EmergencyEpisodeResponse> reconcileIdentity(
            @PathVariable("episodePublicId") String episodePublicId,
            @Valid @RequestBody ReconcileIdentityRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);

        EmergencyEpisodeResponse response = emergencyService.reconcileIdentity(episodePublicId, request, usuarioPublicId, sourceIp);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint de solo cálculo para previsualizar el nivel de prioridad a partir de los síntomas ingresados.
     */
    @PostMapping("/triage-assessments/calculate")
    @PreAuthorize("hasAnyAuthority('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<TriageCalculationResponse> calculateTriageLevel(
            @Valid @RequestBody CalculateTriageLevelRequest request
    ) {
        return ResponseEntity.ok(emergencyService.calculateTriageLevel(request));
    }

    /**
     * Endpoint para registrar una valoración clínica presencial de triaje o reevaluación (U04).
     */
    @PostMapping("/episodes/{episodePublicId}/triage-assessments")
    @PreAuthorize("hasAnyAuthority('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<TriageAssessmentResponse> recordTriageAssessment(
            @PathVariable("episodePublicId") String episodePublicId,
            @Valid @RequestBody RegisterTriageAssessmentRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);

        TriageAssessmentResponse response = emergencyService.recordTriageAssessment(
                episodePublicId,
                request,
                usuarioPublicId,
                authentication.getAuthorities(),
                sourceIp
        );
        URI location = URI.create("/api/v1/emergency/episodes/" + episodePublicId + "/triage-assessments/" + response.publicId());

        return ResponseEntity.created(location).body(response);
    }

    /**
     * Endpoint para consultar el historial de valoraciones de triaje de un episodio (U04).
     */
    @GetMapping("/episodes/{episodePublicId}/triage-assessments")
    public ResponseEntity<List<TriageAssessmentResponse>> listTriageHistory(
            @PathVariable("episodePublicId") String episodePublicId,
            Authentication authentication
    ) {
        List<TriageAssessmentResponse> response = emergencyService.listTriageHistory(
                episodePublicId,
                authentication.getName(),
                authentication.getAuthorities()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint para asignar un profesional de la salud al equipo asistencial del episodio (U06).
     */
    @PostMapping("/episodes/{episodePublicId}/care-team")
    @PreAuthorize("hasAnyAuthority('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL', 'ROLE_ADMINISTRADOR')")
    public ResponseEntity<Map<String, String>> assignCareTeam(
            @PathVariable("episodePublicId") String episodePublicId,
            @Valid @RequestBody AssignTeamRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);

        emergencyService.assignCareTeam(episodePublicId, request, usuarioPublicId, sourceIp);
        return ResponseEntity.ok(Map.of("mensaje", "Profesional asignado exitosamente al equipo asistencial."));
    }

    /**
     * Endpoint para consultar la cola priorizada de urgencias del hospital (U05).
     */
    @GetMapping("/queue")
    @PreAuthorize("hasAnyAuthority('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL', 'ROLE_ADMINISTRADOR')")
    public ResponseEntity<List<EmergencyQueueItemResponse>> listEmergencyQueue(
            @RequestParam(value = "status", required = false) String status
    ) {
        List<EmergencyQueueItemResponse> cola = emergencyService.listEmergencyQueue(status);
        return ResponseEntity.ok(cola);
    }

    /**
     * Endpoint para cerrar o egresar un episodio de urgencias (U06).
     */
    @PostMapping("/episodes/{episodePublicId}/close")
    @PreAuthorize("hasAnyAuthority('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<Map<String, String>> closeEpisode(
            @PathVariable("episodePublicId") String episodePublicId,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);

        emergencyService.closeEpisode(episodePublicId, usuarioPublicId, sourceIp);
        return ResponseEntity.ok(Map.of("mensaje", "Episodio de urgencias cerrado y egresado exitosamente."));
    }
}
