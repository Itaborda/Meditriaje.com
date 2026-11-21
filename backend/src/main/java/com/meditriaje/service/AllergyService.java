package com.meditriaje.service;

import com.meditriaje.dto.allergy.AllergyResponse;
import com.meditriaje.dto.allergy.InactivateAllergyRequest;
import com.meditriaje.dto.allergy.RegisterAllergyRequest;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.Allergy;
import com.meditriaje.model.ClinicalAttention;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.Patient;
import com.meditriaje.model.Professional;
import com.meditriaje.model.AuditResult;
import com.meditriaje.model.User;
import com.meditriaje.repository.AllergyRepository;
import com.meditriaje.repository.ClinicalAttentionRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.ProfessionalRepository;
import com.meditriaje.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio de negocio para la gestión e inmutabilidad de alergias e hipersensibilidades (D3, T4, ADR-007, ADR-008).
 * Garantiza autorización estricta por capas: relación asistencial ordinaria para profesionales (break-glass solo lectura),
 * gestión de alergias autorreportadas para pacientes, y denegación absoluta para administradores.
 */
@Service
public class AllergyService {

    private final AllergyRepository allergyRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final ProfessionalRepository professionalRepository;
    private final ClinicalAttentionRepository attentionRepository;
    private final ClinicalAccessService clinicalAccessService;
    private final AuditService auditService;
    private final Clock clock;

    @Autowired
    public AllergyService(
            AllergyRepository allergyRepository,
            PatientRepository patientRepository,
            UserRepository userRepository,
            ProfessionalRepository professionalRepository,
            ClinicalAttentionRepository attentionRepository,
            ClinicalAccessService clinicalAccessService,
            AuditService auditService
    ) {
        this(
                allergyRepository,
                patientRepository,
                userRepository,
                professionalRepository,
                attentionRepository,
                clinicalAccessService,
                auditService,
                Clock.systemUTC()
        );
    }

    public AllergyService(
            AllergyRepository allergyRepository,
            PatientRepository patientRepository,
            UserRepository userRepository,
            ProfessionalRepository professionalRepository,
            ClinicalAttentionRepository attentionRepository,
            ClinicalAccessService clinicalAccessService,
            AuditService auditService,
            Clock clock
    ) {
        this.allergyRepository = Objects.requireNonNull(allergyRepository, "alergiaRepository no puede ser nulo");
        this.patientRepository = Objects.requireNonNull(patientRepository, "pacienteRepository no puede ser nulo");
        this.userRepository = Objects.requireNonNull(userRepository, "usuarioRepository no puede ser nulo");
        this.professionalRepository = Objects.requireNonNull(professionalRepository, "profesionalRepository no puede ser nulo");
        this.attentionRepository = Objects.requireNonNull(attentionRepository, "atencionRepository no puede ser nulo");
        this.clinicalAccessService = Objects.requireNonNull(clinicalAccessService, "accesoClinicoService no puede ser nulo");
        this.auditService = Objects.requireNonNull(auditService, "auditoriaService no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    // =========================================================================
    // SECCIÓN PROFESIONAL ASISTENCIAL
    // =========================================================================

    /**
     * Consulta las alergias de un paciente por parte de un profesional asistencial (ADR-007, T4).
     * Requiere relación asistencial activa u autorización Break-Glass válida.
     */
    @Transactional(readOnly = true)
    public List<AllergyResponse> listPatientAllergiesForProfessional(
            String pacientePublicId,
            boolean incluirInactivas,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities,
            String ipOrigen
    ) {
        User usuario = getUserByPublicId(usuarioAutenticadoPublicId);
        clinicalAccessService.validateClinicalHistoryAccess(usuarioAutenticadoPublicId, pacientePublicId, authorities);

        Patient paciente = patientRepository.findByPublicId(pacientePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado."));

        List<AllergyResponse> lista = allergyRepository.listByPatientPublicId(paciente.publicId(), incluirInactivas);

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.ALERGIA_CONSULTADA,
                "PACIENTE",
                paciente.publicId(),
                AuditResult.EXITO,
                ipOrigen
        ));

        return lista;
    }

    /**
     * Registra una alergia clínica durante o fuera de una atención por parte de un profesional asistencial (D3, T4).
     * Requiere relación asistencial ordinaria vigente. El acceso Break-Glass es estrictamente de solo lectura.
     */
    @Transactional
    public AllergyResponse registerAllergyByProfessional(
            String pacientePublicId,
            RegisterAllergyRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        User usuario = getUserByPublicId(usuarioAutenticadoPublicId);
        Professional profesional = professionalRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como profesional asistencial."));

        Patient paciente = patientRepository.findByPublicId(pacientePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado."));

        // Autorización de escritura clínica (Break-Glass es SOLO LECTURA)
        clinicalAccessService.validateClinicalWriteAccess(profesional.id(), paciente.id());

        Long atencionId = null;
        if (request.attentionPublicId() != null && !request.attentionPublicId().isBlank()) {
            ClinicalAttention atencion = attentionRepository.findEntityByPublicId(request.attentionPublicId())
                    .orElseThrow(() -> new ResourceNotFoundException("Atencion medica no encontrada."));
            if (!atencion.patientId().equals(paciente.id())) {
                throw new InvalidDataException("La atencion medica no corresponde al paciente indicado.");
            }
            atencionId = atencion.id();
        }

        // Unicidad de sustancia activa
        if (allergyRepository.existsActiveBySubstance(paciente.id(), request.substance())) {
            throw new InvalidDataException("El paciente ya cuenta con una alergia activa registrada para la sustancia: " + request.substance().trim());
        }

        String publicId = UUID.randomUUID().toString();
        Instant ahora = Instant.now(clock);

        Allergy nueva = new Allergy(
                null,
                publicId,
                paciente.id(),
                request.substance().trim(),
                request.reaction(),
                request.severity(),
                "ACTIVA",
                "PROFESIONAL",
                usuario.id(),
                atencionId,
                null,
                null,
                null,
                ahora,
                ahora
        );

        allergyRepository.create(nueva);

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.ALERGIA_REGISTRADA,
                "ALERGIA",
                publicId,
                AuditResult.EXITO,
                ipOrigen
        ));

        return allergyRepository.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar la alergia recién registrada."));
    }

    /**
     * Inactiva una alergia por parte de un profesional asistencial con justificación clínica obligatoria (D3, T4).
     * Requiere relación asistencial ordinaria. Break-Glass es solo lectura.
     */
    @Transactional
    public AllergyResponse inactivateAllergyByProfessional(
            String alergiaPublicId,
            InactivateAllergyRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        User usuario = getUserByPublicId(usuarioAutenticadoPublicId);
        Professional profesional = professionalRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como profesional asistencial."));

        Allergy alergia = allergyRepository.findEntityByPublicId(alergiaPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Alergia no encontrada."));

        // Autorización de escritura clínica
        clinicalAccessService.validateClinicalWriteAccess(profesional.id(), alergia.patientId());

        if (!"ACTIVA".equalsIgnoreCase(alergia.status())) {
            throw new InvalidDataException("La alergia ya se encuentra inactiva.");
        }

        allergyRepository.inactivate(alergia.id(), usuario.id(), request.reason(), Instant.now(clock));

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.ALERGIA_INACTIVADA,
                "ALERGIA",
                alergiaPublicId,
                AuditResult.EXITO,
                ipOrigen
        ));

        return allergyRepository.findByPublicId(alergiaPublicId)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar la alergia recién inactivada."));
    }

    // =========================================================================
    // SECCIÓN PACIENTE (AUTORREPORTADAS)
    // =========================================================================

    /**
     * Consulta las alergias del propio paciente autenticado (D3, T4).
     */
    @Transactional(readOnly = true)
    public List<AllergyResponse> listMyAllergies(String usuarioAutenticadoPublicId, String ipOrigen) {
        User usuario = getUserByPublicId(usuarioAutenticadoPublicId);
        Patient paciente = patientRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como paciente."));

        List<AllergyResponse> lista = allergyRepository.listByPatientPublicId(paciente.publicId(), true);

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.ALERGIA_CONSULTADA,
                "PACIENTE",
                paciente.publicId(),
                AuditResult.EXITO,
                ipOrigen
        ));

        return lista;
    }

    /**
     * Registra una alergia autorreportada por el propio paciente (D3, T4).
     */
    @Transactional
    public AllergyResponse registerMyAllergy(
            RegisterAllergyRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        User usuario = getUserByPublicId(usuarioAutenticadoPublicId);
        Patient paciente = patientRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como paciente."));

        if (allergyRepository.existsActiveBySubstance(paciente.id(), request.substance())) {
            throw new InvalidDataException("Ya cuenta con una alergia activa registrada para la sustancia: " + request.substance().trim());
        }

        String publicId = UUID.randomUUID().toString();
        Instant ahora = Instant.now(clock);

        Allergy nueva = new Allergy(
                null,
                publicId,
                paciente.id(),
                request.substance().trim(),
                request.reaction(),
                request.severity(),
                "ACTIVA",
                "PACIENTE",
                usuario.id(),
                null,
                null,
                null,
                null,
                ahora,
                ahora
        );

        allergyRepository.create(nueva);

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.ALERGIA_REGISTRADA,
                "ALERGIA",
                publicId,
                AuditResult.EXITO,
                ipOrigen
        ));

        return allergyRepository.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar la alergia autorreportada recién registrada."));
    }

    /**
     * Inactiva una alergia autorreportada por el propio paciente (D3, T4).
     * El paciente SOLO puede inactivar alergias registradas con origen 'PACIENTE'.
     */
    @Transactional
    public AllergyResponse inactivateMyAllergy(
            String alergiaPublicId,
            InactivateAllergyRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        User usuario = getUserByPublicId(usuarioAutenticadoPublicId);
        Patient paciente = patientRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como paciente."));

        Allergy alergia = allergyRepository.findEntityByPublicId(alergiaPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Alergia no encontrada."));

        if (!alergia.patientId().equals(paciente.id())) {
            throw new UnauthorizedAccessException("No tiene autorizacion para inactivar una alergia de otro paciente.");
        }

        if (!"PACIENTE".equalsIgnoreCase(alergia.origin())) {
            throw new UnauthorizedAccessException("El paciente solo puede inactivar alergias autorreportadas por si mismo. Las alergias registradas por profesionales deben ser modificadas por el personal de salud.");
        }

        if (!"ACTIVA".equalsIgnoreCase(alergia.status())) {
            throw new InvalidDataException("La alergia ya se encuentra inactiva.");
        }

        allergyRepository.inactivate(alergia.id(), usuario.id(), request.reason(), Instant.now(clock));

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.ALERGIA_INACTIVADA,
                "ALERGIA",
                alergiaPublicId,
                AuditResult.EXITO,
                ipOrigen
        ));

        return allergyRepository.findByPublicId(alergiaPublicId)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar la alergia recién inactivada."));
    }

    // =========================================================================
    // UTILIDADES PRIVADAS
    // =========================================================================

    private User getUserByPublicId(String usuarioPublicId) {
        if (usuarioPublicId == null || usuarioPublicId.isBlank()) {
            throw new UnauthorizedAccessException("Usuario no autenticado.");
        }
        return userRepository.findByPublicId(usuarioPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado."));
    }
}
