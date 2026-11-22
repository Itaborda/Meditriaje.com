package com.meditriaje.service;

import com.meditriaje.dto.clinical.BreakGlassAccessResponse;
import com.meditriaje.dto.clinical.ActivateBreakGlassRequest;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.BreakGlassAccess;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.Patient;
import com.meditriaje.model.Professional;
import com.meditriaje.model.AuditResult;
import com.meditriaje.model.User;
import com.meditriaje.repository.BreakGlassRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.ProfessionalRepository;
import com.meditriaje.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio para gestión y activación de accesos clínicos de emergencia Break-Glass (ADR-007, ADR-017).
 * Exclusivo para profesionales asistenciales (ROLE_PROFESIONAL) ante situaciones clínicas de urgencia vital.
 */
@Service
public class BreakGlassService {

    public static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    public static final int VALIDITY_HOURS = 24;

    private final UserRepository userRepository;
    private final ProfessionalRepository professionalRepository;
    private final PatientRepository patientRepository;
    private final BreakGlassRepository breakGlassRepository;
    private final AuditService auditService;
    private final Clock clock;

    @Autowired
    public BreakGlassService(
            UserRepository userRepository,
            ProfessionalRepository professionalRepository,
            PatientRepository patientRepository,
            BreakGlassRepository breakGlassRepository,
            AuditService auditService
    ) {
        this(
                userRepository,
                professionalRepository,
                patientRepository,
                breakGlassRepository,
                auditService,
                Clock.system(BOGOTA_ZONE)
        );
    }

    public BreakGlassService(
            UserRepository userRepository,
            ProfessionalRepository professionalRepository,
            PatientRepository patientRepository,
            BreakGlassRepository breakGlassRepository,
            AuditService auditService,
            Clock clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "usuarioRepository no puede ser nulo");
        this.professionalRepository = Objects.requireNonNull(professionalRepository, "profesionalRepository no puede ser nulo");
        this.patientRepository = Objects.requireNonNull(patientRepository, "pacienteRepository no puede ser nulo");
        this.breakGlassRepository = Objects.requireNonNull(breakGlassRepository, "breakGlassRepository no puede ser nulo");
        this.auditService = Objects.requireNonNull(auditService, "auditoriaService no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    /**
     * Activa una autorización de acceso clínico excepcional de emergencia Break-Glass (ADR-017).
     *
     * @param request                    Petición con pacientePublicId y motivo clínico de urgencia
     * @param usuarioAutenticadoPublicId Identificador del usuario que invoca la acción (debe ser ROLE_PROFESIONAL)
     * @param ipOrigen                   Dirección IP del cliente para la bitácora inmutable
     * @return DTO con la información de la autorización creada y su vigencia
     */
    @Transactional
    public BreakGlassAccessResponse activateBreakGlass(
            ActivateBreakGlassRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        if (usuarioAutenticadoPublicId == null || usuarioAutenticadoPublicId.isBlank()) {
            throw new UnauthorizedAccessException("Usuario no autenticado.");
        }

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        Professional profesional = professionalRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como profesional asistencial."));

        Patient paciente = patientRepository.findByPublicId(request.patientPublicId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado."));

        if (request.reason() == null || request.reason().trim().length() < 20) {
            throw new InvalidDataException("El motivo de justificacion de emergencia debe contener al menos 20 caracteres.");
        }

        Instant ahora = clock.instant();
        Instant expiracion = ahora.plus(VALIDITY_HOURS, ChronoUnit.HOURS);
        String publicId = UUID.randomUUID().toString();

        BreakGlassAccess acceso = new BreakGlassAccess(
                null,
                publicId,
                profesional.id(),
                paciente.id(),
                request.reason().trim(),
                expiracion,
                ahora
        );

        breakGlassRepository.recordAccess(acceso);

        // Auditoría reforzada e inmutable (ADR-011, ADR-017)
        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.ACCESO_BREAK_GLASS,
                "PACIENTE",
                paciente.publicId(),
                AuditResult.EXITO,
                ipOrigen
        ));

        return breakGlassRepository.findByPublicId(publicId, ahora)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar el acceso break-glass recién registrado."));
    }

    /**
     * Lista los accesos Break-Glass activos (no expirados) del profesional autenticado.
     */
    public List<BreakGlassAccessResponse> listMyActiveAccesses(String usuarioAutenticadoPublicId) {
        if (usuarioAutenticadoPublicId == null || usuarioAutenticadoPublicId.isBlank()) {
            throw new UnauthorizedAccessException("Usuario no autenticado.");
        }

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        Professional profesional = professionalRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como profesional asistencial."));

        return breakGlassRepository.listActiveByProfessional(profesional.id(), clock.instant());
    }

    /**
     * Consulta el detalle de un acceso Break-Glass por su ID público.
     */
    public BreakGlassAccessResponse getByPublicId(String publicId, String usuarioAutenticadoPublicId) {
        if (usuarioAutenticadoPublicId == null || usuarioAutenticadoPublicId.isBlank()) {
            throw new UnauthorizedAccessException("Usuario no autenticado.");
        }

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        Professional profesional = professionalRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como profesional asistencial."));

        Instant ahora = clock.instant();
        BreakGlassAccessResponse response = breakGlassRepository.findByPublicId(publicId, ahora)
                .orElseThrow(() -> new ResourceNotFoundException("Registro de acceso Break-Glass no encontrado."));

        if (!response.professionalPublicId().equals(profesional.publicId())) {
            throw new UnauthorizedAccessException("No tiene autorizacion para consultar el acceso break-glass de otro profesional.");
        }

        return response;
    }
}
