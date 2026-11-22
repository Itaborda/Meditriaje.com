package com.meditriaje.service;

import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.dto.followup.CreateFollowUpRequest;
import com.meditriaje.dto.followup.ReportProgressRequest;
import com.meditriaje.dto.followup.FollowUpResponse;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.ClinicalAttention;
import com.meditriaje.model.FollowUpStatus;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.Patient;
import com.meditriaje.model.Professional;
import com.meditriaje.model.AuditResult;
import com.meditriaje.model.PostAttentionFollowUp;
import com.meditriaje.model.FollowUpType;
import com.meditriaje.model.User;
import com.meditriaje.repository.ClinicalAttentionRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.ProfessionalRepository;
import com.meditriaje.repository.FollowUpRepository;
import com.meditriaje.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio de dominio para el seguimiento post-atención médica (ADR-015, HU-07, HU-09).
 * Gestiona prescripción de tareas de control, reporte de evolución del paciente y aislamiento de roles.
 */
@Service
public class FollowUpService {

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final FollowUpRepository followUpRepository;
    private final ClinicalAttentionRepository attentionRepository;
    private final PatientRepository patientRepository;
    private final ProfessionalRepository professionalRepository;
    private final UserRepository userRepository;
    private final ClinicalAccessService clinicalAccessService;
    private final AuditService auditService;
    private final AppointmentNotificationService notificationService;

    public FollowUpService(
            FollowUpRepository followUpRepository,
            ClinicalAttentionRepository attentionRepository,
            PatientRepository patientRepository,
            ProfessionalRepository professionalRepository,
            UserRepository userRepository,
            ClinicalAccessService clinicalAccessService,
            AuditService auditService,
            AppointmentNotificationService notificationService
    ) {
        this.followUpRepository = Objects.requireNonNull(followUpRepository, "seguimientoRepository no puede ser nulo");
        this.attentionRepository = Objects.requireNonNull(attentionRepository, "atencionRepository no puede ser nulo");
        this.patientRepository = Objects.requireNonNull(patientRepository, "pacienteRepository no puede ser nulo");
        this.professionalRepository = Objects.requireNonNull(professionalRepository, "profesionalRepository no puede ser nulo");
        this.userRepository = Objects.requireNonNull(userRepository, "usuarioRepository no puede ser nulo");
        this.clinicalAccessService = Objects.requireNonNull(clinicalAccessService, "accesoClinicoService no puede ser nulo");
        this.auditService = Objects.requireNonNull(auditService, "auditoriaService no puede ser nulo");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService no puede ser nulo");
    }

    /**
     * Prescribe una nueva tarea o plan de seguimiento post-atención (ADR-015).
     * Exclusivo para profesionales asistenciales (autor o con relación asistencial activa).
     */
    @Transactional
    public FollowUpResponse prescribeFollowUp(
            String atencionPublicId,
            CreateFollowUpRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        User usuario = getUser(usuarioAutenticadoPublicId);
        Professional profesional = professionalRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como profesional asistencial."));

        if (!FollowUpType.isValid(request.type())) {
            throw new InvalidDataException("Tipo de seguimiento invalido: " + request.type()
                    + ". Tipos permitidos: CONTROL_MEDICO, EVOLUCION_SINTOMAS, EXAMEN_PENDIENTE, ADHERENCIA_TRATAMIENTO.");
        }

        if (request.suggestedControlDate() != null) {
            LocalDate hoy = LocalDate.now(BOGOTA_ZONE);
            if (request.suggestedControlDate().isBefore(hoy)) {
                throw new InvalidDataException("La fecha sugerida de control no puede ser anterior a la fecha actual.");
            }
        }

        ClinicalAttention atencion = attentionRepository.findEntityByPublicId(atencionPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Atencion medica no encontrada."));

        if (!"CERRADA".equalsIgnoreCase(atencion.status())) {
            throw new InvalidDataException("Solo se pueden prescribir tareas de seguimiento sobre atenciones medicas cerradas.");
        }

        boolean esAutor = atencion.professionalId().equals(profesional.id());
        if (!esAutor) {
            clinicalAccessService.validateCareRelationship(profesional.id(), atencion.patientId());
        }

        String publicId = UUID.randomUUID().toString();
        PostAttentionFollowUp nuevo = new PostAttentionFollowUp(
                null,
                publicId,
                atencion.id(),
                atencion.patientId(),
                profesional.id(),
                request.type().toUpperCase().trim(),
                request.instructions().trim(),
                request.suggestedControlDate(),
                "PENDIENTE",
                null,
                null,
                Instant.now(),
                Instant.now()
        );

        followUpRepository.save(nuevo);

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.CREACION_SEGUIMIENTO,
                "SEGUIMIENTO_POST_ATENCION",
                publicId,
                AuditResult.EXITO,
                ipOrigen
        ));

        FollowUpResponse response = followUpRepository.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar el seguimiento recién creado."));

        dispatchAsyncNotification(atencion, response);

        return response;
    }

    /**
     * Registra el reporte de evolución aportado por el paciente sobre una tarea de seguimiento (ADR-015, §5.16).
     * El reporte del paciente nunca se convierte automáticamente en un diagnóstico médico.
     */
    @Transactional
    public FollowUpResponse reportProgress(
            String seguimientoPublicId,
            ReportProgressRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        User usuario = getUser(usuarioAutenticadoPublicId);
        Patient paciente = patientRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como paciente."));

        PostAttentionFollowUp seguimiento = followUpRepository.findEntityByPublicId(seguimientoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Seguimiento post-atencion no encontrado."));

        if (!seguimiento.patientId().equals(paciente.id())) {
            throw new UnauthorizedAccessException("No tiene autorizacion para reportar evolucion en un seguimiento ajeno.");
        }

        if (!"PENDIENTE".equalsIgnoreCase(seguimiento.status())) {
            throw new InvalidDataException("El seguimiento ya se encuentra " + seguimiento.status().toLowerCase() + ".");
        }

        followUpRepository.recordPatientReport(
                seguimiento.id(),
                request.report().trim(),
                Instant.now()
        );

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.REPORTE_EVOLUCION_SEGUIMIENTO,
                "SEGUIMIENTO_POST_ATENCION",
                seguimiento.publicId(),
                AuditResult.EXITO,
                ipOrigen
        ));

        return followUpRepository.findByPublicId(seguimiento.publicId())
                .orElseThrow(() -> new IllegalStateException("Error al recuperar el seguimiento actualizado."));
    }

    /**
     * Consulta el detalle de un seguimiento puntual con validación de autorización (ADR-007, ADR-015).
     */
    public FollowUpResponse getByPublicId(
            String seguimientoPublicId,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        validateNotAdministrator(authorities);

        User usuario = getUser(usuarioAutenticadoPublicId);
        PostAttentionFollowUp seguimiento = followUpRepository.findEntityByPublicId(seguimientoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Seguimiento post-atencion no encontrado."));

        if (hasRole(authorities, "ROLE_PACIENTE")) {
            Patient paciente = patientRepository.findByUserId(usuario.id())
                    .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como paciente."));
            if (!seguimiento.patientId().equals(paciente.id())) {
                throw new UnauthorizedAccessException("No tiene autorizacion para consultar el seguimiento de otro paciente.");
            }
        } else if (hasRole(authorities, "ROLE_PROFESIONAL")) {
            Professional profesional = professionalRepository.findByUserId(usuario.id())
                    .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como profesional."));
            boolean esAutor = seguimiento.professionalId().equals(profesional.id());
            if (!esAutor) {
                clinicalAccessService.validateCareRelationship(profesional.id(), seguimiento.patientId());
            }
        } else {
            throw new UnauthorizedAccessException("Rol no autorizado para acceder al seguimiento.");
        }

        return followUpRepository.findByPublicId(seguimientoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Seguimiento post-atencion no encontrado."));
    }

    /**
     * Lista los seguimientos asociados a una atención médica específica (ADR-007, ADR-015).
     */
    public List<FollowUpResponse> listByAttention(
            String atencionPublicId,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        validateNotAdministrator(authorities);

        User usuario = getUser(usuarioAutenticadoPublicId);
        ClinicalAttention atencion = attentionRepository.findEntityByPublicId(atencionPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Atencion medica no encontrada."));

        if (hasRole(authorities, "ROLE_PACIENTE")) {
            Patient paciente = patientRepository.findByUserId(usuario.id())
                    .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como paciente."));
            if (!atencion.patientId().equals(paciente.id())) {
                throw new UnauthorizedAccessException("No tiene autorizacion para consultar los seguimientos de otro paciente.");
            }
        } else if (hasRole(authorities, "ROLE_PROFESIONAL")) {
            Professional profesional = professionalRepository.findByUserId(usuario.id())
                    .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como profesional."));
            boolean esAutor = atencion.professionalId().equals(profesional.id());
            if (!esAutor) {
                clinicalAccessService.validateCareRelationship(profesional.id(), atencion.patientId());
            }
        } else {
            throw new UnauthorizedAccessException("Rol no autorizado.");
        }

        return followUpRepository.listByAttentionId(atencion.id());
    }

    /**
     * Consulta paginada de las tareas de seguimiento pertenecientes al paciente autenticado.
     */
    public PaginatedResponse<FollowUpResponse> listMyFollowUps(
            String usuarioAutenticadoPublicId,
            String estadoFiltro,
            int page,
            int size
    ) {
        User usuario = getUser(usuarioAutenticadoPublicId);
        Patient paciente = patientRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como paciente."));

        int safePage = Math.max(page, 0);
        int safeSize = (size < 1 || size > 100) ? 10 : size;

        String filtro = null;
        if (estadoFiltro != null && !estadoFiltro.isBlank()) {
            if (!FollowUpStatus.isValid(estadoFiltro)) {
                throw new InvalidDataException("Filtro de estado invalido: " + estadoFiltro
                        + ". Estados permitidos: PENDIENTE, COMPLETADO, CANCELADO.");
            }
            filtro = estadoFiltro.toUpperCase().trim();
        }

        List<FollowUpResponse> items = followUpRepository.listByPatientId(paciente.id(), filtro, safePage, safeSize);
        int totalElements = followUpRepository.countByPatientId(paciente.id(), filtro);
        int totalPages = (int) Math.ceil((double) totalElements / safeSize);
        boolean hasNext = safePage < totalPages - 1;
        boolean hasPrevious = safePage > 0;

        return new PaginatedResponse<>(items, safePage, safeSize, totalElements, totalPages, hasNext, hasPrevious);
    }

    /**
     * Cancela una tarea de seguimiento pendiente.
     * Solo permitido para profesionales asistenciales (autor o con relación activa).
     */
    @Transactional
    public FollowUpResponse cancelFollowUp(
            String seguimientoPublicId,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        validateNotAdministrator(authorities);

        if (!hasRole(authorities, "ROLE_PROFESIONAL")) {
            throw new UnauthorizedAccessException("Solo profesionales asistenciales pueden cancelar seguimientos.");
        }

        User usuario = getUser(usuarioAutenticadoPublicId);
        Professional profesional = professionalRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como profesional."));

        PostAttentionFollowUp seguimiento = followUpRepository.findEntityByPublicId(seguimientoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Seguimiento post-atencion no encontrado."));

        boolean esAutor = seguimiento.professionalId().equals(profesional.id());
        if (!esAutor) {
            clinicalAccessService.validateCareRelationship(profesional.id(), seguimiento.patientId());
        }

        if (!"PENDIENTE".equalsIgnoreCase(seguimiento.status())) {
            throw new InvalidDataException("Solo se pueden cancelar seguimientos en estado PENDIENTE. Estado actual: " + seguimiento.status());
        }

        followUpRepository.updateStatus(seguimiento.id(), "CANCELADO");

        return followUpRepository.findByPublicId(seguimiento.publicId())
                .orElseThrow(() -> new IllegalStateException("Error al recuperar el seguimiento cancelado."));
    }

    private void dispatchAsyncNotification(ClinicalAttention atencion, FollowUpResponse response) {
        try {
            String email = notificationService.resolvePatientEmail(atencion.patientId());
            if (email != null && !email.isBlank()) {
                String fechaAtencionStr = atencion.closedAt() != null
                        ? DATE_FORMATTER.format(atencion.closedAt().atZone(BOGOTA_ZONE))
                        : (atencion.createdAt() != null ? DATE_FORMATTER.format(atencion.createdAt().atZone(BOGOTA_ZONE)) : "");

                String fechaControlStr = response.suggestedControlDate() != null
                        ? DATE_FORMATTER.format(response.suggestedControlDate())
                        : "Según evolución médica";

                notificationService.sendFollowUpAttentionSummary(
                        email,
                        response.patientName(),
                        response.professionalName(),
                        response.professionalSpecialty(),
                        fechaAtencionStr,
                        response.type(),
                        response.instructions(),
                        fechaControlStr
                );
            }
        } catch (Exception ex) {
            // Tolerancia a fallos: no interrumpe la transacción clínica
        }
    }

    private User getUser(String usuarioPublicId) {
        return userRepository.findByPublicId(usuarioPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));
    }

    private void validateNotAdministrator(Collection<? extends GrantedAuthority> authorities) {
        if (hasRole(authorities, "ROLE_ADMINISTRADOR")) {
            throw new UnauthorizedAccessException("El personal administrativo no tiene acceso a contenido clinico.");
        }
    }

    private boolean hasRole(Collection<? extends GrantedAuthority> authorities, String rol) {
        return authorities != null && authorities.stream().anyMatch(a -> a.getAuthority().equals(rol));
    }
}
