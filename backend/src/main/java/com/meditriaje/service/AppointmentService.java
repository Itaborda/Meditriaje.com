package com.meditriaje.service;

import com.meditriaje.dto.affiliation.RescheduleAppointmentRequest;
import com.meditriaje.dto.admin.SlotResponse;
import com.meditriaje.dto.appointment.CancelAppointmentRequest;
import com.meditriaje.dto.appointment.AppointmentResponse;
import com.meditriaje.dto.appointment.BookAppointmentRequest;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.AppointmentUnavailableException;
import com.meditriaje.exception.OperationConflictException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.Appointment;
import com.meditriaje.model.AppointmentStateMachine;
import com.meditriaje.model.AvailabilitySlot;
import com.meditriaje.model.AppointmentStatus;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.Patient;
import com.meditriaje.model.Professional;
import com.meditriaje.model.AuditResult;
import com.meditriaje.model.Triage;
import com.meditriaje.model.User;
import com.meditriaje.repository.ClinicalAttentionRepository;
import com.meditriaje.repository.AppointmentRepository;
import com.meditriaje.repository.AvailabilitySlotRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.ProfessionalRepository;
import com.meditriaje.repository.TriageRepository;
import com.meditriaje.repository.UserRepository;
import com.meditriaje.dto.common.PaginatedResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para el agendamiento y gestión del ciclo de vida de citas médicas (ADR-002, ADR-003, ADR-006, HU-04).
 */
@Service
public class AppointmentService {

    public static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final ProfessionalRepository professionalRepository;
    private final AppointmentRepository appointmentRepository;
    private final TriageRepository triageRepository;
    private final AuditService auditService;
    private final AppointmentNotificationService appointmentNotificationService;
    private final ClinicalAttentionRepository attentionRepository;
    private final Clock clock;

    public AppointmentService(
            UserRepository userRepository,
            PatientRepository patientRepository,
            AvailabilitySlotRepository availabilitySlotRepository,
            ProfessionalRepository professionalRepository,
            AppointmentRepository appointmentRepository,
            TriageRepository triageRepository,
            AuditService auditService
    ) {
        this(
                userRepository,
                patientRepository,
                availabilitySlotRepository,
                professionalRepository,
                appointmentRepository,
                triageRepository,
                auditService,
                null,
                null,
                Clock.systemUTC()
        );
    }

    public AppointmentService(
            UserRepository userRepository,
            PatientRepository patientRepository,
            AvailabilitySlotRepository availabilitySlotRepository,
            ProfessionalRepository professionalRepository,
            AppointmentRepository appointmentRepository,
            TriageRepository triageRepository,
            AuditService auditService,
            Clock clock
    ) {
        this(
                userRepository,
                patientRepository,
                availabilitySlotRepository,
                professionalRepository,
                appointmentRepository,
                triageRepository,
                auditService,
                null,
                null,
                clock
        );
    }

    public AppointmentService(
            UserRepository userRepository,
            PatientRepository patientRepository,
            AvailabilitySlotRepository availabilitySlotRepository,
            ProfessionalRepository professionalRepository,
            AppointmentRepository appointmentRepository,
            TriageRepository triageRepository,
            AuditService auditService,
            AppointmentNotificationService appointmentNotificationService
    ) {
        this(
                userRepository,
                patientRepository,
                availabilitySlotRepository,
                professionalRepository,
                appointmentRepository,
                triageRepository,
                auditService,
                appointmentNotificationService,
                null,
                Clock.systemUTC()
        );
    }

    public AppointmentService(
            UserRepository userRepository,
            PatientRepository patientRepository,
            AvailabilitySlotRepository availabilitySlotRepository,
            ProfessionalRepository professionalRepository,
            AppointmentRepository appointmentRepository,
            TriageRepository triageRepository,
            AuditService auditService,
            AppointmentNotificationService appointmentNotificationService,
            Clock clock
    ) {
        this(
                userRepository,
                patientRepository,
                availabilitySlotRepository,
                professionalRepository,
                appointmentRepository,
                triageRepository,
                auditService,
                appointmentNotificationService,
                null,
                clock
        );
    }

    @Autowired
    public AppointmentService(
            UserRepository userRepository,
            PatientRepository patientRepository,
            AvailabilitySlotRepository availabilitySlotRepository,
            ProfessionalRepository professionalRepository,
            AppointmentRepository appointmentRepository,
            TriageRepository triageRepository,
            AuditService auditService,
            AppointmentNotificationService appointmentNotificationService,
            ClinicalAttentionRepository attentionRepository,
            Clock clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "UsuarioRepository no puede ser nulo");
        this.patientRepository = Objects.requireNonNull(patientRepository, "PacienteRepository no puede ser nulo");
        this.availabilitySlotRepository = Objects.requireNonNull(availabilitySlotRepository, "DisponibilidadSlotRepository no puede ser nulo");
        this.professionalRepository = Objects.requireNonNull(professionalRepository, "ProfesionalRepository no puede ser nulo");
        this.appointmentRepository = Objects.requireNonNull(appointmentRepository, "CitaRepository no puede ser nulo");
        this.triageRepository = Objects.requireNonNull(triageRepository, "TriajeRepository no puede ser nulo");
        this.auditService = Objects.requireNonNull(auditService, "AuditoriaService no puede ser nulo");
        this.appointmentNotificationService = appointmentNotificationService;
        this.attentionRepository = attentionRepository;
        this.clock = Objects.requireNonNull(clock, "Clock no puede ser nulo");
    }

    /**
     * Reserva transaccionalmente una cita médica para el paciente autenticado (ADR-006, HU-04).
     *
     * @param request                       Datos del slot y triaje opcional.
     * @param usuarioAutenticadoPublicId     UUID público del usuario en sesión.
     * @param ipOrigen                      Dirección IP cliente para la bitácora de auditoría.
     * @return {@link CitaResponse} con los datos consolidados de la cita programada.
     */
    @Transactional
    public AppointmentResponse bookAppointment(BookAppointmentRequest request, String usuarioAutenticadoPublicId, String ipOrigen) {
        Objects.requireNonNull(request, "El request de reserva no puede ser nulo");
        Objects.requireNonNull(usuarioAutenticadoPublicId, "El usuario público no puede ser nulo");

        // 1. Obtener usuario autenticado
        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        // 2. Obtener paciente vinculado al usuario (autenticado != autorizado, ADR-002)
        Patient paciente = patientRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Solo pacientes registrados pueden agendar citas."));

        // 3. Buscar slot de disponibilidad por publicId
        AvailabilitySlot slot = availabilitySlotRepository.findEntityByPublicId(request.slotPublicId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot de disponibilidad no encontrado."));

        // 4. Validar que el horario no esté en el pasado
        if (slot.startDateTime().isBefore(Instant.now(clock))) {
            throw new InvalidDataException("No es posible agendar una cita en un horario pasado.");
        }

        // 5. Validar correspondencia profesional-especialidad del slot
        Professional profesional = professionalRepository.findById(slot.professionalId())
                .orElseThrow(() -> new InvalidDataException("El profesional no corresponde a la especialidad del slot."));

        if (!Objects.equals(profesional.specialtyId(), slot.specialtyId())) {
            throw new InvalidDataException("El profesional no corresponde a la especialidad del slot.");
        }

        // 6. Validar vinculación opcional con triaje clínico (ADR-009, HU-04, M5.4)
        Long triajeId = null;
        if (request.triagePublicId() != null && !request.triagePublicId().isBlank()) {
            Triage triaje = triageRepository.findEntityByPublicId(request.triagePublicId().trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Triaje no encontrado."));

            if (!Objects.equals(triaje.patientId(), paciente.id())) {
                throw new InvalidDataException("El triaje no corresponde al paciente de la cita.");
            }

            if (triaje.isEmergency()) {
                throw new InvalidDataException("No se permite agendar cita para un triaje clasificado como emergencia.");
            }

            triajeId = triaje.id();
        }

        // 7. Intentar reservar atómicamente el slot (cambio de estado LIBRE -> OCUPADO)
        int filas = availabilitySlotRepository.reserveSlot(slot.id());
        if (filas == 0) {
            throw new AppointmentUnavailableException("El slot de atencion ya ha sido reservado o no esta disponible.");
        }

        // 8. Insertar la cita en estado PROGRAMADA y manejar restricción de concurrencia
        String citaPublicId = UUID.randomUUID().toString();
        Appointment cita = new Appointment(
                null,
                citaPublicId,
                slot.id(),
                paciente.id(),
                triajeId,
                null, // CITA_ORIGEN_ID para reprogramaciones
                "PROGRAMADA",
                null,
                null,
                null
        );

        Long citaIdGenerado;
        try {
            citaIdGenerado = appointmentRepository.create(cita);
        } catch (DataIntegrityViolationException | OperationConflictException ex) {
            throw new AppointmentUnavailableException("El slot de atencion ya cuenta con una cita activa.");
        }

        // 8. Auditar evento exitoso inmutable
        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.RESERVA_CITA,
                "CITA",
                citaPublicId,
                AuditResult.EXITO,
                ipOrigen
        ));

        // 9. Retornar vista consolidada de la cita
        AppointmentResponse response = appointmentRepository.findByPublicId(citaPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada tras creacion."));

        // 10. Despachar notificación asíncrona/desacoplada de confirmación por correo (ADR-015)
        if (appointmentNotificationService != null) {
            appointmentNotificationService.sendBookingConfirmation(citaIdGenerado, paciente.id(), usuario.email(), response);
        }

        return response;
    }

    /**
     * Cancela una cita médica agendada aplicando las reglas de máquina de estados,
     * anticipación y autorización según el rol (ADR-002, ADR-003, ADR-006, HU-05).
     *
     * @param citaPublicId                 UUID público de la cita a cancelar.
     * @param request                      DTO opcional con el motivo de cancelación.
     * @param usuarioAutenticadoPublicId   UUID público del usuario en sesión.
     * @param authorities                  Colección de roles/autoridades del usuario autenticado.
     * @param ipOrigen                     Dirección IP cliente para la bitácora de auditoría.
     * @return {@link CitaResponse} con los datos consolidados de la cita cancelada.
     */
    @Transactional
    public AppointmentResponse cancelAppointment(
            String citaPublicId,
            CancelAppointmentRequest request,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities,
            String ipOrigen
    ) {
        Objects.requireNonNull(citaPublicId, "El identificador de la cita no puede ser nulo");
        Objects.requireNonNull(usuarioAutenticadoPublicId, "El usuario público no puede ser nulo");

        // 1. Obtener usuario autenticado
        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        // 2. Obtener cita por citaPublicId
        Appointment cita = appointmentRepository.findEntityByPublicId(citaPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada."));

        // 2.1 Si existe una atención ligada a la cita (abierta o cerrada), no se puede cancelar (Decisión D2)
        if (attentionRepository != null && attentionRepository.existsByAppointmentId(cita.id())) {
            throw new OperationConflictException("No es posible cancelar una cita que ya cuenta con una atencion clinica vinculada.");
        }

        // 3. Validar transición de estado según CitaStateMachine
        AppointmentStatus estadoActual;
        try {
            estadoActual = AppointmentStatus.valueOf(cita.status());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new InvalidDataException("Estado de cita no reconocido: " + cita.status());
        }
        AppointmentStateMachine.validateTransition(estadoActual, AppointmentStatus.CANCELADA);

        // 4. Obtener slot
        AvailabilitySlot slot = availabilitySlotRepository.findById(cita.slotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot de disponibilidad no encontrado."));

        // 5. Reglas de Autorización y Anticipación (ADR-006, HU-05, HU-09)
        Set<String> roles = authorities != null
                ? authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet())
                : Set.of();

        if (roles.contains("ROLE_ADMINISTRADOR")) {
            // Administrador puede cancelar cualquier cita activa sin restricción de 2 horas.
        } else if (roles.contains("ROLE_PROFESIONAL")) {
            // Validar que el slot pertenezca al profesional autenticado
            Professional profesional = professionalRepository.findByUserId(usuario.id())
                    .orElseThrow(() -> new UnauthorizedAccessException("El profesional no tiene autorizacion para cancelar citas de otro colega."));

            if (!Objects.equals(slot.professionalId(), profesional.id())) {
                throw new UnauthorizedAccessException("El profesional no tiene autorizacion para cancelar citas de otro colega.");
            }
            // No aplica la regla de las 2 horas para profesionales
        } else if (roles.contains("ROLE_PACIENTE")) {
            // Validar que la cita le pertenezca
            Patient paciente = patientRepository.findByUserId(usuario.id())
                    .orElseThrow(() -> new UnauthorizedAccessException("Solo pacientes registrados pueden cancelar citas."));

            if (!Objects.equals(cita.patientId(), paciente.id())) {
                throw new UnauthorizedAccessException("No tiene autorizacion para cancelar una cita ajena.");
            }

            // Regla de las 2 horas (ADR-006, HU-05): la cancelación por paciente debe realizarse con al menos 2 horas de anticipación
            Instant limiteCancelacion = slot.startDateTime().minus(2, ChronoUnit.HOURS);
            if (Instant.now(clock).isAfter(limiteCancelacion)) {
                throw new InvalidDataException("La cancelacion por parte del paciente solo esta permitida hasta 2 horas antes de la cita.");
            }
        } else {
            throw new UnauthorizedAccessException("No tiene autorizacion para cancelar citas.");
        }

        // 6. Actualizar la cita a CANCELADA y registrar motivo
        String motivoCancelacion = request != null ? request.reason() : null;
        appointmentRepository.updateStatus(cita.id(), AppointmentStatus.CANCELADA.name(), motivoCancelacion);

        // 7. Liberar el slot de disponibilidad: pasar el slot a 'LIBRE'
        availabilitySlotRepository.releaseSlot(cita.slotId());

        // 8. Auditar evento exitoso inmutable
        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.CANCELACION_CITA,
                "CITA",
                cita.publicId(),
                AuditResult.EXITO,
                ipOrigen
        ));

        // 9. Retornar CitaResponse actualizado
        AppointmentResponse response = appointmentRepository.findByPublicId(cita.publicId())
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada tras cancelacion."));

        // 10. Despachar notificación asíncrona/desacoplada de cancelación por correo (ADR-015)
        if (appointmentNotificationService != null) {
            String emailDestinatario = appointmentNotificationService.resolvePatientEmail(cita.patientId());
            appointmentNotificationService.sendCancellationNotification(cita.id(), cita.patientId(), emailDestinatario, response, motivoCancelacion);
        }

        return response;
    }

    /**
     * Registra la inasistencia (no-show) de un paciente a una cita médica programada (D4, T5).
     *
     * Reglas de negocio estrictas:
     * - Quién: Profesional asignado a la cita o ADMINISTRADOR.
     *          Paciente -> 403; otro profesional -> 403.
     * - Cuándo: now >= inicio del slot (zona America/Bogota).
     *           Si la cita aún no ha iniciado -> 400 DatosInvalidosException.
     * - Desde: Solo citas en estado PROGRAMADA.
     *          Si el estado no lo permite -> 409 ConflictoOperacionException.
     * - Condición: No debe existir atención médica vinculada (abierta o cerrada).
     *              Si existe atención vinculada -> 409 ConflictoOperacionException.
     * - Efecto: Estado de la cita pasa a NO_ASISTIO; el slot NO se libera (permanece OCUPADO).
     * - Auditoría: Registro inmutable con acción CITA_NO_ASISTIO.
     *
     * @param citaPublicId               UUID público de la cita a marcar como no asistida.
     * @param usuarioAutenticadoPublicId UUID público del usuario en sesión.
     * @param authorities                Colección de roles/autoridades del usuario autenticado.
     * @param ipOrigen                   Dirección IP cliente para la bitácora de auditoría.
     * @return {@link CitaResponse} con los datos consolidados de la cita actualizada.
     */
    @Transactional
    public AppointmentResponse markNoShow(
            String citaPublicId,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities,
            String ipOrigen
    ) {
        Objects.requireNonNull(citaPublicId, "El identificador de la cita no puede ser nulo");
        Objects.requireNonNull(usuarioAutenticadoPublicId, "El usuario público no puede ser nulo");

        // 1. Obtener usuario autenticado
        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        // 2. Obtener cita por citaPublicId
        Appointment cita = appointmentRepository.findEntityByPublicId(citaPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada."));

        // 3. Reglas de Autorización (D4, T5)
        Set<String> roles = authorities != null
                ? authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet())
                : Set.of();

        boolean esAdmin = roles.contains("ROLE_ADMINISTRADOR");
        boolean esProf = roles.contains("ROLE_PROFESIONAL");

        if (!esAdmin && !esProf) {
            throw new UnauthorizedAccessException("No tiene autorizacion para marcar inasistencia de citas.");
        }

        // 4. Obtener slot de disponibilidad
        AvailabilitySlot slot = availabilitySlotRepository.findById(cita.slotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot de disponibilidad no encontrado."));

        // Si es profesional y no es admin, validar que sea el profesional dueño de la cita
        if (!esAdmin) {
            Professional profesional = professionalRepository.findByUserId(usuario.id())
                    .orElseThrow(() -> new UnauthorizedAccessException("El profesional no tiene autorizacion para marcar inasistencia de citas de otro colega."));

            if (!Objects.equals(slot.professionalId(), profesional.id())) {
                throw new UnauthorizedAccessException("El profesional no tiene autorizacion para marcar inasistencia de citas de otro colega.");
            }
        }

        // 5. Validar estado de la cita: solo desde PROGRAMADA (D4, T5)
        if (!AppointmentStatus.PROGRAMADA.name().equals(cita.status())) {
            throw new OperationConflictException("Solo es posible marcar inasistencia para citas en estado PROGRAMADA (estado actual: " + cita.status() + ").");
        }

        // 6. Validar que no exista atención clínica ligada (D2, D4, T5)
        if (attentionRepository != null && attentionRepository.existsByAppointmentId(cita.id())) {
            throw new OperationConflictException("No es posible marcar inasistencia para una cita que ya cuenta con una atencion clinica vinculada.");
        }

        // 7. Validar temporalidad: now >= inicio del slot (D4, T5)
        Instant now = Instant.now(clock);
        if (now.isBefore(slot.startDateTime())) {
            throw new InvalidDataException("No es posible marcar inasistencia antes de la hora de inicio programada de la cita.");
        }

        // 8. Actualizar la cita a NO_ASISTIO (el slot NO se libera, ya que el horario ya transcurrió)
        appointmentRepository.updateStatus(cita.id(), AppointmentStatus.NO_ASISTIO.name(), null);

        // 9. Auditar evento inmutable
        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.CITA_NO_ASISTIO,
                "CITA",
                cita.publicId(),
                AuditResult.EXITO,
                ipOrigen
        ));

        // 10. Retornar CitaResponse actualizado
        return appointmentRepository.findByPublicId(cita.publicId())
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada tras actualizar inasistencia."));
    }

    /**
     * Consulta la agenda asistencial del profesional autenticado con filtros opcionales de fecha y estado (HU-06, ADR-007).
     *
     * @param usuarioAutenticadoPublicId UUID público del usuario profesional en sesión.
     * @param fecha                      Fecha específica en zona horaria America/Bogota (opcional).
     * @param estado                     Filtro por estado de cita (opcional).
     * @param page                       Número de página (0-indexed).
     * @param size                       Tamaño de página (1 a 100).
     * @return {@link PaginatedResponse} conteniendo la lista de citas {@link CitaResponse}.
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<AppointmentResponse> getMyAgenda(
            String usuarioAutenticadoPublicId,
            LocalDate fecha,
            String estado,
            int page,
            int size
    ) {
        Objects.requireNonNull(usuarioAutenticadoPublicId, "El usuario público no puede ser nulo");

        if (page < 0) {
            throw new InvalidDataException("El número de página no puede ser menor a 0.");
        }
        if (size < 1 || size > 100) {
            throw new InvalidDataException("El tamaño de página debe estar entre 1 y 100.");
        }

        String estadoFiltro = null;
        if (estado != null && !estado.isBlank()) {
            estadoFiltro = estado.trim().toUpperCase(Locale.ROOT);
            try {
                AppointmentStatus.valueOf(estadoFiltro);
            } catch (IllegalArgumentException ex) {
                throw new InvalidDataException("Estado de cita no válido: " + estado);
            }
        }

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        Professional profesional = professionalRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Solo profesionales registrados pueden consultar su agenda."));

        Instant fechaDesde = null;
        Instant fechaHasta = null;

        if (fecha != null) {
            ZonedDateTime startOfDay = fecha.atStartOfDay(BOGOTA_ZONE);
            ZonedDateTime endOfDay = fecha.atTime(23, 59, 59, 999_999_999).atZone(BOGOTA_ZONE);
            fechaDesde = startOfDay.toInstant();
            fechaHasta = endOfDay.toInstant();
        }

        List<AppointmentResponse> citas = appointmentRepository.listProfessionalAgenda(
                profesional.id(),
                fechaDesde,
                fechaHasta,
                estadoFiltro,
                page,
                size
        );
        int total = appointmentRepository.countProfessionalAgenda(
                profesional.id(),
                fechaDesde,
                fechaHasta,
                estadoFiltro
        );

        return PaginatedResponse.of(citas, page, size, (long) total);
    }

    /**
     * Consulta paginada de las citas médicas del paciente autenticado (HU-09).
     *
     * @param usuarioAutenticadoPublicId UUID público del usuario paciente en sesión.
     * @param page                       Número de página (0-indexed).
     * @param size                       Tamaño de página (1 a 100).
     * @return {@link PaginatedResponse} conteniendo la lista de citas {@link CitaResponse}.
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<AppointmentResponse> getMyAppointments(
            String usuarioAutenticadoPublicId,
            int page,
            int size
    ) {
        Objects.requireNonNull(usuarioAutenticadoPublicId, "El usuario público no puede ser nulo");

        if (page < 0) {
            throw new InvalidDataException("El número de página no puede ser menor a 0.");
        }
        if (size < 1 || size > 100) {
            throw new InvalidDataException("El tamaño de página debe estar entre 1 y 100.");
        }

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        Patient paciente = patientRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Solo pacientes registrados pueden consultar sus citas."));

        List<AppointmentResponse> citas = appointmentRepository.listByPatientId(paciente.id(), page, size);
        int total = appointmentRepository.countByPatientId(paciente.id());

        return PaginatedResponse.of(citas, page, size, (long) total);
    }

    /**
     * Reprograma una cita médica agendada a un nuevo slot de disponibilidad (F-01 [P1], ADR-006, ADR-026).
     * Ejecuta en una única transacción atómica:
     * 1. Validación de cita original y antelación.
     * 2. Reserva compare-and-set del nuevo slot.
     * 3. Inserción de la nueva cita vinculando CITA_ORIGEN_ID.
     * 4. Transición de la cita anterior a REPROGRAMADA y liberación del slot anterior.
     * 5. Registro de auditoría inmutable CITA_REPROGRAMADA.
     */
    @Transactional
    public AppointmentResponse rescheduleAppointment(
            String citaPublicId,
            RescheduleAppointmentRequest request,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities,
            String ipOrigen
    ) {
        Objects.requireNonNull(citaPublicId, "El identificador de la cita no puede ser nulo");
        Objects.requireNonNull(request, "El request de reprogramación no puede ser nulo");
        Objects.requireNonNull(usuarioAutenticadoPublicId, "El usuario público no puede ser nulo");

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        Appointment citaOriginal = appointmentRepository.findEntityByPublicId(citaPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada."));

        if (attentionRepository != null && attentionRepository.existsByAppointmentId(citaOriginal.id())) {
            throw new OperationConflictException("No es posible reprogramar una cita que ya cuenta con una atencion clinica vinculada.");
        }

        AppointmentStatus estadoActual;
        try {
            estadoActual = AppointmentStatus.valueOf(citaOriginal.status());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new InvalidDataException("Estado de cita no reconocido: " + citaOriginal.status());
        }
        AppointmentStateMachine.validateTransition(estadoActual, AppointmentStatus.REPROGRAMADA);

        AvailabilitySlot slotOriginal = availabilitySlotRepository.findById(citaOriginal.slotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot original no encontrado."));

        Set<String> roles = authorities != null
                ? authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet())
                : Set.of();

        if (roles.contains("ROLE_ADMINISTRADOR")) {
            // Administrador autorizado
        } else if (roles.contains("ROLE_PROFESIONAL")) {
            Professional profesional = professionalRepository.findByUserId(usuario.id())
                    .orElseThrow(() -> new UnauthorizedAccessException("El profesional no tiene autorizacion para reprogramar citas de otro colega."));
            if (!Objects.equals(slotOriginal.professionalId(), profesional.id())) {
                throw new UnauthorizedAccessException("El profesional no tiene autorizacion para reprogramar citas de otro colega.");
            }
        } else if (roles.contains("ROLE_PACIENTE")) {
            Patient paciente = patientRepository.findByUserId(usuario.id())
                    .orElseThrow(() -> new UnauthorizedAccessException("Solo pacientes registrados pueden reprogramar citas."));
            if (!Objects.equals(citaOriginal.patientId(), paciente.id())) {
                throw new UnauthorizedAccessException("No tiene autorizacion para reprogramar una cita ajena.");
            }
            // Regla de las 2 horas de anticipación
            Instant limite = slotOriginal.startDateTime().minus(2, ChronoUnit.HOURS);
            if (Instant.now(clock).isAfter(limite)) {
                throw new InvalidDataException("La reprogramacion por parte del paciente solo esta permitida hasta 2 horas antes de la cita.");
            }
        } else {
            throw new UnauthorizedAccessException("No tiene autorizacion para reprogramar citas.");
        }

        // Resolver el nuevo slot
        AvailabilitySlot nuevoSlot;
        if (request.newSlotPublicId() != null && !request.newSlotPublicId().isBlank()) {
            nuevoSlot = availabilitySlotRepository.findEntityByPublicId(request.newSlotPublicId().trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Nuevo slot de disponibilidad no encontrado: " + request.newSlotPublicId()));
        } else if (request.newDate() != null && request.newTime() != null) {
            Instant nuevoInicio = request.newDate().atTime(request.newTime()).atZone(BOGOTA_ZONE).toInstant();
            Instant nuevoFin = nuevoInicio.plus(30, ChronoUnit.MINUTES);
            List<SlotResponse> slots = availabilitySlotRepository.list(0, 10, null, null, null, nuevoInicio.minusSeconds(60), nuevoFin.plusSeconds(60), "LIBRE");
            SlotResponse coincidente = slots.stream().findFirst()
                    .orElseThrow(() -> new AppointmentUnavailableException("No se encontro slot disponible en el horario solicitado."));
            nuevoSlot = availabilitySlotRepository.findEntityByPublicId(coincidente.publicId())
                    .orElseThrow(() -> new ResourceNotFoundException("Slot de disponibilidad no encontrado."));
        } else {
            throw new InvalidDataException("Debe proporcionar nuevoSlotPublicId o nuevaFecha y nuevaHora.");
        }

        if (Objects.equals(nuevoSlot.id(), slotOriginal.id())) {
            throw new InvalidDataException("El nuevo slot no puede ser identico al slot actual.");
        }

        if (nuevoSlot.startDateTime().isBefore(Instant.now(clock))) {
            throw new InvalidDataException("No es posible reprogramar una cita en un horario pasado.");
        }

        // 1. Reservar nuevo slot con compare-and-set
        int reservado = availabilitySlotRepository.reserveSlot(nuevoSlot.id());
        if (reservado == 0) {
            throw new AppointmentUnavailableException("El nuevo slot de atencion ya ha sido reservado o no esta disponible.");
        }

        // 2. Insertar nueva cita enlazada
        String nuevaCitaPublicId = UUID.randomUUID().toString();
        Appointment nuevaCita = new Appointment(
                null,
                nuevaCitaPublicId,
                nuevoSlot.id(),
                citaOriginal.patientId(),
                citaOriginal.triageId(),
                citaOriginal.id(), // CITA_ORIGEN_ID
                "PROGRAMADA",
                null,
                null,
                null
        );

        Long nuevaCitaId;
        try {
            nuevaCitaId = appointmentRepository.create(nuevaCita);
        } catch (DataIntegrityViolationException | OperationConflictException ex) {
            availabilitySlotRepository.releaseSlot(nuevoSlot.id());
            throw new AppointmentUnavailableException("El nuevo slot de atencion ya cuenta con una cita activa.");
        }

        // 3. Transicionar cita original a REPROGRAMADA
        String motivo = request.reason() != null && !request.reason().isBlank()
                ? request.reason()
                : "Reprogramada hacia nueva cita: " + nuevaCitaPublicId;
        appointmentRepository.updateStatus(citaOriginal.id(), AppointmentStatus.REPROGRAMADA.name(), motivo);

        // 4. Liberar slot original
        availabilitySlotRepository.releaseSlot(citaOriginal.slotId());

        // 5. Auditar evento exitoso inmutable
        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.CITA_REPROGRAMADA,
                "CITA",
                nuevaCitaPublicId,
                AuditResult.EXITO,
                ipOrigen
        ));

        // 6. Retornar vista consolidada de la nueva cita
        AppointmentResponse response = appointmentRepository.findByPublicId(nuevaCitaPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita reprogramada no encontrada tras creacion."));

        if (appointmentNotificationService != null) {
            Patient paciente = patientRepository.findById(citaOriginal.patientId()).orElse(null);
            if (paciente != null) {
                User uPac = userRepository.findById(paciente.userId()).orElse(null);
                if (uPac != null) {
                    appointmentNotificationService.sendBookingConfirmation(nuevaCitaId, paciente.id(), uPac.email(), response);
                }
            }
        }

        return response;
    }
}

