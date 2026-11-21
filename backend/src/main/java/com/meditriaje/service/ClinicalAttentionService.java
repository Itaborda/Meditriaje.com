package com.meditriaje.service;

import com.meditriaje.dto.clinical.ClinicalAttentionResponse;
import com.meditriaje.dto.clinical.CloseAttentionRequest;
import com.meditriaje.dto.clinical.CreateAmendmentRequest;
import com.meditriaje.dto.clinical.AmendmentResponse;
import com.meditriaje.dto.clinical.StartAttentionRequest;
import com.meditriaje.dto.clinical.VitalSignsDto;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.ClinicalAttention;
import com.meditriaje.model.Appointment;
import com.meditriaje.model.AppointmentStateMachine;
import com.meditriaje.model.Cie10Diagnosis;
import com.meditriaje.model.AvailabilitySlot;
import com.meditriaje.model.AppointmentStatus;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.Patient;
import com.meditriaje.model.Professional;
import com.meditriaje.model.AuditResult;
import com.meditriaje.model.VitalSign;
import com.meditriaje.model.User;
import com.meditriaje.repository.ClinicalAttentionRepository;
import com.meditriaje.repository.AppointmentRepository;
import com.meditriaje.repository.Cie10DiagnosisRepository;
import com.meditriaje.repository.AvailabilitySlotRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.ProfessionalRepository;
import com.meditriaje.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio de negocio para el inicio, atención y cierre inmutable de actos médicos
 * (ADR-007, ADR-008, ADR-011, HU-07, HU-09).
 */
@Service
public class ClinicalAttentionService {

    public static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final ProfessionalRepository professionalRepository;
    private final AppointmentRepository appointmentRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final ClinicalAttentionRepository attentionRepository;
    private final Cie10DiagnosisRepository cie10DiagnosisRepository;
    private final ClinicalAccessService clinicalAccessService;
    private final AuditService auditService;
    private final Clock clock;

    @Autowired
    public ClinicalAttentionService(
            UserRepository userRepository,
            PatientRepository patientRepository,
            ProfessionalRepository professionalRepository,
            AppointmentRepository appointmentRepository,
            AvailabilitySlotRepository availabilitySlotRepository,
            ClinicalAttentionRepository attentionRepository,
            Cie10DiagnosisRepository cie10DiagnosisRepository,
            ClinicalAccessService clinicalAccessService,
            AuditService auditService
    ) {
        this(
                userRepository,
                patientRepository,
                professionalRepository,
                appointmentRepository,
                availabilitySlotRepository,
                attentionRepository,
                cie10DiagnosisRepository,
                clinicalAccessService,
                auditService,
                Clock.system(BOGOTA_ZONE)
        );
    }

    public ClinicalAttentionService(
            UserRepository userRepository,
            PatientRepository patientRepository,
            ProfessionalRepository professionalRepository,
            AppointmentRepository appointmentRepository,
            AvailabilitySlotRepository availabilitySlotRepository,
            ClinicalAttentionRepository attentionRepository,
            Cie10DiagnosisRepository cie10DiagnosisRepository,
            ClinicalAccessService clinicalAccessService,
            AuditService auditService,
            Clock clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "usuarioRepository no puede ser nulo");
        this.patientRepository = Objects.requireNonNull(patientRepository, "pacienteRepository no puede ser nulo");
        this.professionalRepository = Objects.requireNonNull(professionalRepository, "profesionalRepository no puede ser nulo");
        this.appointmentRepository = Objects.requireNonNull(appointmentRepository, "citaRepository no puede ser nulo");
        this.availabilitySlotRepository = Objects.requireNonNull(availabilitySlotRepository, "disponibilidadSlotRepository no puede ser nulo");
        this.attentionRepository = Objects.requireNonNull(attentionRepository, "atencionRepository no puede ser nulo");
        this.cie10DiagnosisRepository = Objects.requireNonNull(cie10DiagnosisRepository, "diagnosticoCie10Repository no puede ser nulo");
        this.clinicalAccessService = Objects.requireNonNull(clinicalAccessService, "accesoClinicoService no puede ser nulo");
        this.auditService = Objects.requireNonNull(auditService, "auditoriaService no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    /**
     * Inicia una atención médica vinculada a una cita (HU-07).
     * Solo el profesional asignado a la cita puede iniciar la atención.
     */
    @Transactional
    public ClinicalAttentionResponse startAttention(
            StartAttentionRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado."));
        Professional profesional = professionalRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como profesional asistencial."));

        Appointment cita = appointmentRepository.findEntityByPublicId(request.appointmentPublicId())
                .orElseThrow(() -> new ResourceNotFoundException("Cita medica no encontrada."));

        AvailabilitySlot slot = availabilitySlotRepository.findById(cita.slotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot de disponibilidad no encontrado."));

        // Validar que el médico autenticado es el asignado a la cita
        if (!slot.professionalId().equals(profesional.id())) {
            throw new UnauthorizedAccessException("El profesional no esta asignado a esta cita medica.");
        }

        // Validar estado de la cita
        if (!"PROGRAMADA".equalsIgnoreCase(cita.status()) && !"CONFIRMADA".equalsIgnoreCase(cita.status())) {
            throw new InvalidDataException("La cita no se encuentra en un estado valido para ser atendida (estado actual: " + cita.status() + ").");
        }

        // Validar relación 1:1 estricta
        if (attentionRepository.existsByAppointmentId(cita.id())) {
            throw new InvalidDataException("Ya existe una atencion clinica registrada para esta cita medica.");
        }

        // En el flujo D2, la cita permanece en su estado actual (PROGRAMADA) mientras la atención esté en curso.
        String atencionPublicId = UUID.randomUUID().toString();
        ClinicalAttention atencion = new ClinicalAttention(
                null,
                atencionPublicId,
                cita.id(),
                cita.patientId(),
                profesional.id(),
                "ABIERTA"
        );

        attentionRepository.create(atencion);

        // Auditoría inmutable sin datos clínicos (ADR-011)
        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.CREACION_ATENCION,
                "ATENCION",
                atencionPublicId,
                AuditResult.EXITO,
                ipOrigen
        ));

        return attentionRepository.findDetailByPublicId(atencionPublicId)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar la atencion recien creada."));
    }

    /**
     * Cierra y consolida una atención médica de forma inmutable (ADR-008, HU-07).
     * Pasa la cita a ATENDIDA y audita el evento.
     */
    @Transactional
    public ClinicalAttentionResponse closeAttention(
            String atencionPublicId,
            CloseAttentionRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado."));
        Professional profesional = professionalRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como profesional asistencial."));

        ClinicalAttention atencion = attentionRepository.findEntityByPublicId(atencionPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Atencion clinica no encontrada."));

        // Validar que el profesional es el asignado a la atención
        if (!atencion.professionalId().equals(profesional.id())) {
            throw new UnauthorizedAccessException("No tiene autorizacion para cerrar la atencion de otro profesional.");
        }

        // Validar que la atención siga abierta (inmutabilidad ADR-008)
        if (!atencion.isOpen()) {
            throw new InvalidDataException("La atencion clinica ya se encuentra CERRADA y es inmutable.");
        }

        // Validar diagnóstico CIE-10
        Cie10Diagnosis diag = cie10DiagnosisRepository.findByCode(request.cie10DiagnosisCode())
                .orElseThrow(() -> new InvalidDataException("Codigo de diagnostico CIE-10 no encontrado: " + request.cie10DiagnosisCode()));
        if (!diag.isActive()) {
            throw new InvalidDataException("El diagnostico CIE-10 seleccionado se encuentra inactivo.");
        }

        // Validar y registrar signos vitales si fueron provistos
        VitalSignsDto signos = request.vitalSigns();
        if (signos != null && signos.hasValues()) {
            if (signos.systolicPressure() != null && signos.diastolicPressure() != null
                    && signos.systolicPressure() <= signos.diastolicPressure()) {
                throw new InvalidDataException("La presion arterial sistolica debe ser estrictamente mayor a la presion diastolica.");
            }
            VitalSign sv = new VitalSign(
                    atencion.id(),
                    signos.systolicPressure(),
                    signos.diastolicPressure(),
                    signos.heartRate(),
                    signos.respiratoryRate(),
                    signos.temperature(),
                    signos.oxygenSaturation(),
                    signos.weightKg(),
                    signos.heightCm()
            );
            attentionRepository.saveVitalSigns(sv);
        }

        // Cerrar atención en base de datos (inmutable)
        Instant fechaCierre = clock.instant();
        attentionRepository.closeAttention(
                atencion.id(),
                diag.id(),
                request.consultationReason(),
                request.progressNote(),
                request.instructions(),
                fechaCierre
        );

        // Actualizar estado de la cita a ATENDIDA
        Appointment cita = appointmentRepository.findEntityById(atencion.appointmentId())
                .orElseThrow(() -> new IllegalStateException("Cita vinculada a la atencion no encontrada."));

        AppointmentStatus estadoOrigen = AppointmentStatus.valueOf(cita.status());
        AppointmentStateMachine.validateTransition(estadoOrigen, AppointmentStatus.ATENDIDA);
        appointmentRepository.updateStatus(cita.id(), AppointmentStatus.ATENDIDA.name(), null);

        // Auditoría inmutable sin datos clínicos ni diagnósticos en logs/auditoría (ADR-011)
        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.CIERRE_ATENCION,
                "ATENCION",
                atencionPublicId,
                AuditResult.EXITO,
                ipOrigen
        ));

        return attentionRepository.findDetailByPublicId(atencionPublicId)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar la atencion recien cerrada."));
    }

    /**
     * Consulta una atención médica por su ID público, validando autorización vía AccesoClinicoService (HU-07, HU-09).
     */
    public ClinicalAttentionResponse getByPublicId(
            String atencionPublicId,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities,
            String ipOrigen
    ) {
        ClinicalAttention atencion = attentionRepository.findEntityByPublicId(atencionPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Atencion clinica no encontrada."));

        // Autorización centralizada de relación asistencial / pertenencia de paciente
        clinicalAccessService.validateClinicalHistoryAccess(usuarioAutenticadoPublicId, atencion.patientId(), authorities);

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        // Auditar consulta de historia clínica
        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.CONSULTA_HISTORIA,
                "ATENCION",
                atencionPublicId,
                AuditResult.EXITO,
                ipOrigen
        ));

        return attentionRepository.findDetailByPublicId(atencionPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Atencion clinica no encontrada."));
    }

    /**
     * Registra una enmienda o aclaración médica inmutable sobre una atención cerrada (ADR-008, HU-07).
     * Garantiza que la atención esté CERRADA y que el profesional cuente con relación asistencial o sea el autor.
     */
    @Transactional
    public AmendmentResponse createAmendment(
            String atencionPublicId,
            CreateAmendmentRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado."));
        Professional profesional = professionalRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como profesional asistencial."));

        ClinicalAttention atencion = attentionRepository.findEntityByPublicId(atencionPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Atencion clinica no encontrada."));

        // Inmutabilidad estricta (ADR-008): solo se admiten enmiendas sobre atenciones CERRADAS
        if (!atencion.isClosed()) {
            throw new InvalidDataException("Solo es posible registrar enmiendas sobre atenciones clinicas CERRADAS.");
        }

        // Autorización asistencial (ADR-007): el profesional debe ser el autor o tener relación asistencial activa
        if (!atencion.professionalId().equals(profesional.id())) {
            clinicalAccessService.validateCareRelationship(profesional.id(), atencion.patientId());
        }

        Instant ahora = clock.instant();
        attentionRepository.createAmendment(
                atencion.id(),
                profesional.id(),
                request.reason(),
                request.content(),
                ahora
        );

        // Auditoría inmutable sin contenido clínico en bitácora (ADR-011)
        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.ENMIENDA_ATENCION,
                "ATENCION",
                atencionPublicId,
                AuditResult.EXITO,
                ipOrigen
        ));

        String nombreCompleto = (profesional.firstNames() + " " + profesional.lastNames()).trim();
        return new AmendmentResponse(
                profesional.publicId(),
                nombreCompleto,
                request.reason(),
                request.content(),
                ahora
        );
    }

    /**
     * Consulta paginada del historial clínico del paciente actualmente autenticado (HU-09).
     * Incluye atenciones, diagnósticos, signos vitales y enmiendas sin exponer IDs numéricos.
     */
    public PaginatedResponse<ClinicalAttentionResponse> getMyClinicalHistory(
            String usuarioAutenticadoPublicId,
            int page,
            int size,
            String ipOrigen
    ) {
        if (usuarioAutenticadoPublicId == null || usuarioAutenticadoPublicId.isBlank()) {
            throw new UnauthorizedAccessException("Usuario no autenticado.");
        }

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado."));
        Patient paciente = patientRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como paciente."));

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));

        List<ClinicalAttentionResponse> contenido = attentionRepository.listPatientHistory(paciente.id(), safePage, safeSize);
        long total = attentionRepository.countPatientHistory(paciente.id());

        // Auditar consulta de historia clínica del paciente (ADR-011)
        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.CONSULTA_HISTORIA,
                "HISTORIA_CLINICA",
                paciente.publicId(),
                AuditResult.EXITO,
                ipOrigen
        ));

        return PaginatedResponse.of(contenido, safePage, safeSize, total);
    }

    /**
     * Consulta paginada de la historia clínica de un paciente para profesionales asistenciales (HU-07, HU-09, ADR-007, ADR-017).
     * Valida autorización asistencial (cita futura, atención previa en 12 meses o acceso Break-Glass activo).
     */
    public PaginatedResponse<ClinicalAttentionResponse> getPatientClinicalHistory(
            String pacientePublicId,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities,
            int page,
            int size,
            String ipOrigen
    ) {
        if (usuarioAutenticadoPublicId == null || usuarioAutenticadoPublicId.isBlank()) {
            throw new UnauthorizedAccessException("Usuario no autenticado.");
        }
        if (pacientePublicId == null || pacientePublicId.isBlank()) {
            throw new InvalidDataException("Identificador de paciente no proporcionado.");
        }

        // Valida que el profesional tenga relación asistencial activa o break-glass
        clinicalAccessService.validateClinicalHistoryAccess(usuarioAutenticadoPublicId, pacientePublicId, authorities);

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        Patient paciente = patientRepository.findByPublicId(pacientePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado."));

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));

        List<ClinicalAttentionResponse> contenido = attentionRepository.listPatientHistory(paciente.id(), safePage, safeSize);
        long total = attentionRepository.countPatientHistory(paciente.id());

        // Auditar consulta de historia clínica médica (ADR-011)
        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.CONSULTA_HISTORIA,
                "HISTORIA_CLINICA",
                paciente.publicId(),
                AuditResult.EXITO,
                ipOrigen
        ));

        return PaginatedResponse.of(contenido, safePage, safeSize, total);
    }
}
