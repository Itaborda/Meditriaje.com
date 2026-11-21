package com.meditriaje.service;

import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.Patient;
import com.meditriaje.model.Professional;
import com.meditriaje.model.User;
import com.meditriaje.repository.ClinicalAttentionRepository;
import com.meditriaje.repository.BreakGlassRepository;
import com.meditriaje.repository.AppointmentRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.ProfessionalRepository;
import com.meditriaje.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio centralizado de autorización clínica y verificación de relación asistencial
 * (ADR-007, ADR-008, HU-07, HU-09).
 *
 * <p>Reglas estrictas de acceso:</p>
 * <ul>
 *   <li><b>Paciente:</b> solo puede consultar su propia información clínica. Si intenta ver la de otro paciente &rarr; 403 Forbidden.</li>
 *   <li><b>Administrador:</b> NUNCA tiene acceso a contenido clínico (atenciones, historia, recetas, signos vitales) &rarr; 403 Forbidden.</li>
 *   <li><b>Profesional asistencial:</b> solo puede consultar la información de un paciente si existe una relación asistencial activa:
 *     <ul>
 *       <li>Cita activa futura (PROGRAMADA o CONFIRMADA) con el profesional, O</li>
 *       <li>Atención previa propia realizada dentro de la ventana temporal configurada (por defecto 12 meses).</li>
 *     </ul>
 *     Si no se cumple ninguna condición &rarr; 403 Forbidden.
 *   </li>
 * </ul>
 */
@Service
public class ClinicalAccessService {

    public static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    public static final String ROLE_ADMIN = "ROLE_ADMINISTRADOR";
    public static final String ROLE_PATIENT = "ROLE_PACIENTE";
    public static final String ROLE_PROFESSIONAL = "ROLE_PROFESIONAL";

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final ProfessionalRepository professionalRepository;
    private final AppointmentRepository appointmentRepository;
    private final ClinicalAttentionRepository attentionRepository;
    private final BreakGlassRepository breakGlassRepository;
    private final Clock clock;
    private final int windowMonths;

    @Autowired
    public ClinicalAccessService(
            UserRepository userRepository,
            PatientRepository patientRepository,
            ProfessionalRepository professionalRepository,
            AppointmentRepository appointmentRepository,
            ClinicalAttentionRepository attentionRepository,
            BreakGlassRepository breakGlassRepository,
            @Value("${meditriaje.clinical.access-window-months:12}") int windowMonths
    ) {
        this(
                userRepository,
                patientRepository,
                professionalRepository,
                appointmentRepository,
                attentionRepository,
                breakGlassRepository,
                Clock.system(BOGOTA_ZONE),
                windowMonths
        );
    }

    public ClinicalAccessService(
            UserRepository userRepository,
            PatientRepository patientRepository,
            ProfessionalRepository professionalRepository,
            AppointmentRepository appointmentRepository,
            ClinicalAttentionRepository attentionRepository,
            Clock clock,
            int windowMonths
    ) {
        this(
                userRepository,
                patientRepository,
                professionalRepository,
                appointmentRepository,
                attentionRepository,
                null,
                clock,
                windowMonths
        );
    }

    public ClinicalAccessService(
            UserRepository userRepository,
            PatientRepository patientRepository,
            ProfessionalRepository professionalRepository,
            AppointmentRepository appointmentRepository,
            ClinicalAttentionRepository attentionRepository,
            BreakGlassRepository breakGlassRepository,
            Clock clock,
            int windowMonths
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "usuarioRepository no puede ser nulo");
        this.patientRepository = Objects.requireNonNull(patientRepository, "pacienteRepository no puede ser nulo");
        this.professionalRepository = Objects.requireNonNull(professionalRepository, "profesionalRepository no puede ser nulo");
        this.appointmentRepository = Objects.requireNonNull(appointmentRepository, "citaRepository no puede ser nulo");
        this.attentionRepository = Objects.requireNonNull(attentionRepository, "atencionRepository no puede ser nulo");
        this.breakGlassRepository = breakGlassRepository;
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
        this.windowMonths = windowMonths > 0 ? windowMonths : 12;
    }

    /**
     * Valida que el usuario autenticado tenga autorización clínica para acceder al historial o datos
     * del paciente identificado por su {@code pacientePublicId} (ADR-007).
     *
     * @param usuarioAutenticadoPublicId Identificador público del usuario autenticado
     * @param pacientePublicId           Identificador público del paciente objetivo
     * @param authorities                Colección de roles/autoridades del usuario autenticado
     * @throws AccesoNoAutorizadoException Si no está autorizado (403)
     * @throws RecursoNoEncontradoException Si el paciente objetivo o usuario no existe (404)
     */
    public void validateClinicalHistoryAccess(
            String usuarioAutenticadoPublicId,
            String pacientePublicId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        if (usuarioAutenticadoPublicId == null || usuarioAutenticadoPublicId.isBlank() || authorities == null || authorities.isEmpty()) {
            throw new UnauthorizedAccessException("Usuario no autenticado o sin roles asignados.");
        }
        if (pacientePublicId == null || pacientePublicId.isBlank()) {
            throw new UnauthorizedAccessException("Identificador de paciente no proporcionado.");
        }

        Set<String> roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        // 1. Regla ADR-007: El administrador NUNCA accede a contenido clínico
        if (roles.contains(ROLE_ADMIN)) {
            throw new UnauthorizedAccessException("El personal administrativo no tiene autorizacion para acceder a contenido clinico.");
        }

        // 2. Regla paciente: solo ve sus propios datos clínicos
        if (roles.contains(ROLE_PATIENT)) {
            User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado."));
            Patient paciente = patientRepository.findByUserId(usuario.id())
                    .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como paciente."));

            if (!paciente.publicId().equals(pacientePublicId)) {
                throw new UnauthorizedAccessException("No tiene autorizacion para acceder a la informacion clinica de otro paciente.");
            }
            return;
        }

        // 3. Regla profesional: requiere relación asistencial activa (ADR-007)
        if (roles.contains(ROLE_PROFESSIONAL)) {
            User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario profesional no encontrado."));
            Professional profesional = professionalRepository.findByUserId(usuario.id())
                    .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como profesional asistencial."));

            Patient pacienteObjetivo = patientRepository.findByPublicId(pacientePublicId)
                    .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado."));

            if (!hasCareRelationship(profesional.id(), pacienteObjetivo.id())) {
                throw new UnauthorizedAccessException("No existe una relacion asistencial activa con el paciente.");
            }
            return;
        }

        throw new UnauthorizedAccessException("Rol no autorizado para acceder a contenido clinico.");
    }

    /**
     * Valida que el usuario autenticado tenga autorización clínica para acceder al historial o datos
     * del paciente identificado por su {@code pacienteId} numérico interno.
     */
    public void validateClinicalHistoryAccess(
            String usuarioAutenticadoPublicId,
            Long pacienteId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        if (pacienteId == null) {
            throw new UnauthorizedAccessException("Identificador de paciente no proporcionado.");
        }
        Patient paciente = patientRepository.findById(pacienteId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado."));

        validateClinicalHistoryAccess(usuarioAutenticadoPublicId, paciente.publicId(), authorities);
    }

    /**
     * Determina si existe una relación asistencial activa entre el profesional y el paciente (ADR-007):
     * 1. Cita activa futura (PROGRAMADA o CONFIRMADA) con fecha de inicio posterior a ahora, O
     * 2. Atención médica previa propia realizada dentro de la ventana temporal configurada.
     *
     * @param profesionalId Identificador interno del profesional
     * @param pacienteId    Identificador interno del paciente
     * @return true si existe relación asistencial activa
     */
    public boolean hasCareRelationship(Long profesionalId, Long pacienteId) {
        Objects.requireNonNull(profesionalId, "profesionalId no puede ser nulo");
        Objects.requireNonNull(pacienteId, "pacienteId no puede ser nulo");

        Instant ahora = clock.instant();

        // 1. Cita activa futura
        boolean tieneCitaFutura = appointmentRepository.existsActiveFutureAppointment(profesionalId, pacienteId, ahora);
        if (tieneCitaFutura) {
            return true;
        }

        // 2. Atención previa propia en los últimos N meses (default 12 meses)
        Instant fechaLimite = ZonedDateTime.ofInstant(ahora, BOGOTA_ZONE)
                .minusMonths(windowMonths)
                .toInstant();

        if (attentionRepository.existsPreviousAttentionInWindow(profesionalId, pacienteId, fechaLimite)) {
            return true;
        }

        // 3. Acceso clínico de emergencia Break-Glass activo y no expirado (ADR-017)
        if (breakGlassRepository != null && breakGlassRepository.existsActiveAccess(profesionalId, pacienteId, ahora)) {
            return true;
        }

        return false;
    }

    /**
     * Sobrecarga de conveniencia para verificar relación asistencial a partir de identificadores públicos UUID.
     */
    public boolean hasCareRelationship(String profesionalPublicId, String pacientePublicId) {
        Professional profesional = professionalRepository.findByPublicId(profesionalPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Profesional no encontrado."));
        Patient paciente = patientRepository.findByPublicId(pacientePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado."));

        return hasCareRelationship(profesional.id(), paciente.id());
    }

    /**
     * Valida que exista relación asistencial activa o lanza AccesoNoAutorizadoException (ADR-007).
     */
    public void validateCareRelationship(Long profesionalId, Long pacienteId) {
        if (!hasCareRelationship(profesionalId, pacienteId)) {
            throw new UnauthorizedAccessException("El profesional no cuenta con una relacion asistencial activa con el paciente.");
        }
    }

    /**
     * Determina si existe una relación asistencial ordinaria (cita futura o atención previa en ventana),
     * excluyendo el acceso excepcional Break-Glass (ADR-007, ADR-017, T4).
     */
    public boolean hasOrdinaryCareRelationship(Long profesionalId, Long pacienteId) {
        Objects.requireNonNull(profesionalId, "profesionalId no puede ser nulo");
        Objects.requireNonNull(pacienteId, "pacienteId no puede ser nulo");

        Instant ahora = clock.instant();

        // 1. Cita activa futura
        boolean tieneCitaFutura = appointmentRepository.existsActiveFutureAppointment(profesionalId, pacienteId, ahora);
        if (tieneCitaFutura) {
            return true;
        }

        // 2. Atención previa propia en los últimos N meses (default 12 meses)
        Instant fechaLimite = ZonedDateTime.ofInstant(ahora, BOGOTA_ZONE)
                .minusMonths(windowMonths)
                .toInstant();

        return attentionRepository.existsPreviousAttentionInWindow(profesionalId, pacienteId, fechaLimite);
    }

    /**
     * Valida que el profesional cuente con autorización de escritura clínica sobre el paciente (ADR-007, ADR-017, T4).
     * El acceso Break-Glass es estrictamente de solo lectura; cualquier registro o modificación exige relación asistencial ordinaria.
     */
    public void validateClinicalWriteAccess(Long profesionalId, Long pacienteId) {
        if (!hasOrdinaryCareRelationship(profesionalId, pacienteId)) {
            if (breakGlassRepository != null && breakGlassRepository.existsActiveAccess(profesionalId, pacienteId, clock.instant())) {
                throw new UnauthorizedAccessException("El acceso Break-Glass es de solo lectura; no permite registrar ni modificar informacion clinica.");
            }
            throw new UnauthorizedAccessException("El profesional no cuenta con una relacion asistencial activa con el paciente.");
        }
    }

    public int getWindowMonths() {
        return windowMonths;
    }
}
