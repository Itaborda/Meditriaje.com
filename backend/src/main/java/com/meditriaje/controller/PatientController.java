package com.meditriaje.controller;

import com.meditriaje.dto.PatientProfileResponse;
import com.meditriaje.dto.appointment.AppointmentResponse;
import com.meditriaje.dto.clinical.ClinicalAttentionResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.dto.followup.ReportProgressRequest;
import com.meditriaje.dto.followup.FollowUpResponse;
import com.meditriaje.dto.prescription.PrescriptionResponse;
import com.meditriaje.dto.emergency.QrAccessResponse;
import com.meditriaje.dto.emergency.GenerateQrRequest;
import com.meditriaje.dto.emergency.GenerateQrResponse;
import com.meditriaje.dto.pharmacy.PrescriptionDispensationResponse;
import com.meditriaje.service.AppointmentService;
import com.meditriaje.service.ClinicalAttentionService;
import com.meditriaje.service.DispensationService;
import com.meditriaje.service.EmergencyQrService;
import com.meditriaje.service.FollowUpService;
import com.meditriaje.service.PatientService;
import com.meditriaje.service.PrescriptionService;
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

import java.util.List;
import java.util.Objects;

/**
 * Endpoints asistenciales y de consulta para el paciente (HU-08, HU-09).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private final PatientService patientService;
    private final ClinicalAttentionService clinicalAttentionService;
    private final PrescriptionService prescriptionService;
    private final AppointmentService appointmentService;
    private final FollowUpService followUpService;
    private final EmergencyQrService emergencyQrService;
    private final DispensationService dispensationService;

    public PatientController(
            PatientService patientService,
            ClinicalAttentionService clinicalAttentionService,
            PrescriptionService prescriptionService,
            AppointmentService appointmentService,
            FollowUpService followUpService,
            EmergencyQrService emergencyQrService,
            DispensationService dispensationService
    ) {
        this.patientService = Objects.requireNonNull(patientService, "PacienteService no puede ser nulo");
        this.clinicalAttentionService = Objects.requireNonNull(clinicalAttentionService, "ClinicalAttentionService no puede ser nulo");
        this.prescriptionService = Objects.requireNonNull(prescriptionService, "PrescriptionService no puede ser nulo");
        this.appointmentService = Objects.requireNonNull(appointmentService, "AppointmentService no puede ser nulo");
        this.followUpService = Objects.requireNonNull(followUpService, "FollowUpService no puede ser nulo");
        this.emergencyQrService = Objects.requireNonNull(emergencyQrService, "EmergencyQrService no puede ser nulo");
        this.dispensationService = Objects.requireNonNull(dispensationService, "DispensationService no puede ser nulo");
    }

    /**
     * Consulta el perfil demográfico y de contacto del paciente actualmente autenticado.
     * Requiere obligatoriamente el rol ROLE_PACIENTE (ADR-002, HU-09).
     */
    @GetMapping("/me")
    @PreAuthorize("hasAuthority('ROLE_PACIENTE')")
    public ResponseEntity<PatientProfileResponse> getMyProfile(Authentication authentication) {
        String usuarioPublicId = authentication.getName();
        PatientProfileResponse perfil = patientService.getMyProfile(usuarioPublicId);
        return ResponseEntity.ok(perfil);
    }

    /**
     * Consulta paginada del historial de atenciones médicas y enmiendas del paciente autenticado (HU-09).
     * Solo lectura, sin exponer IDs numéricos de base de datos.
     */
    @GetMapping("/me/history")
    @PreAuthorize("hasAuthority('ROLE_PACIENTE')")
    public ResponseEntity<PaginatedResponse<ClinicalAttentionResponse>> getMyClinicalHistory(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);
        PaginatedResponse<ClinicalAttentionResponse> historia = clinicalAttentionService.getMyClinicalHistory(
                usuarioPublicId, page, size, sourceIp
        );
        return ResponseEntity.ok(historia);
    }

    /**
     * Consulta paginada de las recetas médicas emitidas para el paciente autenticado (HU-08, HU-09).
     * Solo lectura, sin exponer IDs numéricos autonuméricos de base de datos.
     */
    @GetMapping("/me/prescriptions")
    @PreAuthorize("hasAuthority('ROLE_PACIENTE')")
    public ResponseEntity<PaginatedResponse<PrescriptionResponse>> getMyPrescriptions(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);
        PaginatedResponse<PrescriptionResponse> recetas = prescriptionService.getMyPrescriptions(
                usuarioPublicId, page, size, sourceIp
        );
        return ResponseEntity.ok(recetas);
    }

    /**
     * Consulta el estado de dispensación farmacéutica y código de reclamación de una receta médica propia (F2.4, ADR-016).
     */
    @GetMapping("/me/prescriptions/{publicId}/dispensation")
    @PreAuthorize("hasAuthority('ROLE_PACIENTE')")
    public ResponseEntity<PrescriptionDispensationResponse> getPrescriptionDispensation(
            @PathVariable("publicId") String publicId,
            Authentication authentication
    ) {
        String usuarioPublicId = authentication.getName();
        PrescriptionDispensationResponse response = dispensationService.getPatientDispensation(
                publicId, usuarioPublicId
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Consulta paginada de las citas médicas agendadas para el paciente autenticado (HU-04, HU-09).
     * Solo lectura, sin exponer IDs numéricos autonuméricos de base de datos.
     */
    @GetMapping("/me/appointments")
    @PreAuthorize("hasAuthority('ROLE_PACIENTE')")
    public ResponseEntity<PaginatedResponse<AppointmentResponse>> getMyAppointments(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Authentication authentication
    ) {
        String usuarioPublicId = authentication.getName();
        PaginatedResponse<AppointmentResponse> citas = appointmentService.getMyAppointments(
                usuarioPublicId, page, size
        );
        return ResponseEntity.ok(citas);
    }

    /**
     * Consulta paginada de las tareas de seguimiento post-atención asignadas al paciente (ADR-015, HU-09).
     */
    @GetMapping("/me/follow-ups")
    @PreAuthorize("hasAuthority('ROLE_PACIENTE')")
    public ResponseEntity<PaginatedResponse<FollowUpResponse>> getMyFollowUps(
            @RequestParam(name = "estado", required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Authentication authentication
    ) {
        String usuarioPublicId = authentication.getName();
        PaginatedResponse<FollowUpResponse> response = followUpService.listMyFollowUps(
                usuarioPublicId, status, page, size
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Registra el reporte de evolución del paciente sobre una tarea de seguimiento (ADR-015, §5.16).
     * El reporte del paciente nunca se convierte automáticamente en un diagnóstico médico.
     */
    @PostMapping("/me/follow-ups/{publicId}/report")
    @PreAuthorize("hasAuthority('ROLE_PACIENTE')")
    public ResponseEntity<FollowUpResponse> reportProgress(
            @PathVariable("publicId") String publicId,
            @Valid @RequestBody ReportProgressRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);
        FollowUpResponse response = followUpService.reportProgress(
                publicId, request, usuarioPublicId, sourceIp
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Genera un nuevo código QR y token temporal de acceso al resumen clínico de emergencia (ADR-010, §5.17, §5.18).
     */
    @PostMapping("/me/emergency-qr")
    @PreAuthorize("hasAuthority('ROLE_PACIENTE')")
    public ResponseEntity<GenerateQrResponse> generateQrAccess(
            @Valid @RequestBody(required = false) GenerateQrRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);
        GenerateQrResponse response = emergencyQrService.generateQrAccess(request, usuarioPublicId, sourceIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lista los accesos temporales QR generados por el paciente con su estado actual (ACTIVO, EXPIRADO, AGOTADO, REVOCADO).
     */
    @GetMapping("/me/emergency-qr")
    @PreAuthorize("hasAuthority('ROLE_PACIENTE')")
    public ResponseEntity<List<QrAccessResponse>> listMyQrAccesses(Authentication authentication) {
        String usuarioPublicId = authentication.getName();
        List<QrAccessResponse> response = emergencyQrService.listMyQrAccesses(usuarioPublicId);
        return ResponseEntity.ok(response);
    }

    /**
     * Revoca inmediatamente un acceso temporal QR a solicitud del paciente titular.
     */
    @PatchMapping("/me/emergency-qr/{publicId}/revoke")
    @PreAuthorize("hasAuthority('ROLE_PACIENTE')")
    public ResponseEntity<Void> revokeQrAccess(
            @PathVariable("publicId") String publicId,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String sourceIp = IpUtil.extractIp(httpRequest);
        emergencyQrService.revokeQrAccess(publicId, usuarioPublicId, sourceIp);
        return ResponseEntity.noContent().build();
    }
}
