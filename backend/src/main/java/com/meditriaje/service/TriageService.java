package com.meditriaje.service;

import com.meditriaje.dto.triage.SymptomCatalogResponse;
import com.meditriaje.dto.triage.CreateTriageRequest;
import com.meditriaje.dto.triage.SymptomItemRequest;
import com.meditriaje.dto.triage.TriageResponse;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.Patient;
import com.meditriaje.model.AuditResult;
import com.meditriaje.model.Triage;
import com.meditriaje.model.TriageSymptomEntry;
import com.meditriaje.model.User;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.TriageRepository;
import com.meditriaje.repository.UserRepository;
import com.meditriaje.triage.TriageInput;
import com.meditriaje.triage.TriageEngine;
import com.meditriaje.triage.TriageResult;
import com.meditriaje.triage.ReportedSymptom;
import com.meditriaje.triage.TriageEngineFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para la evaluación, persistencia y consulta de triajes clínicos (ADR-002, ADR-003, ADR-009, ADR-011, HU-02).
 */
@Service
@Transactional(readOnly = true)
public class TriageService {

    private final TriageRepository triageRepository;
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final TriageEngineFactory triageEngineFactory;
    private final AuditService auditService;
    private final ClinicalAccessService clinicalAccessService;

    public TriageService(
            TriageRepository triageRepository,
            UserRepository userRepository,
            PatientRepository patientRepository,
            TriageEngineFactory triageEngineFactory,
            AuditService auditService
    ) {
        this(triageRepository, userRepository, patientRepository, triageEngineFactory, auditService, null);
    }

    @Autowired
    public TriageService(
            TriageRepository triageRepository,
            UserRepository userRepository,
            PatientRepository patientRepository,
            TriageEngineFactory triageEngineFactory,
            AuditService auditService,
            ClinicalAccessService clinicalAccessService
    ) {
        this.triageRepository = Objects.requireNonNull(triageRepository, "TriajeRepository no puede ser nulo");
        this.userRepository = Objects.requireNonNull(userRepository, "UsuarioRepository no puede ser nulo");
        this.patientRepository = Objects.requireNonNull(patientRepository, "PacienteRepository no puede ser nulo");
        this.triageEngineFactory = Objects.requireNonNull(triageEngineFactory, "TriajeMotorFactory no puede ser nulo");
        this.auditService = Objects.requireNonNull(auditService, "AuditoriaService no puede ser nulo");
        this.clinicalAccessService = clinicalAccessService;
    }

    /**
     * Evalúa la sintomatología con el motor determinista, persiste el triaje inmutable y genera auditoría (ADR-009, ADR-011, HU-02).
     */
    @Transactional
    public TriageResponse evaluateAndSaveTriage(CreateTriageRequest request, String usuarioAutenticadoPublicId, String ipOrigen) {
        Objects.requireNonNull(request, "El request de triaje no puede ser nulo");
        Objects.requireNonNull(usuarioAutenticadoPublicId, "El usuario público no puede ser nulo");

        // 1. Obtener usuario autenticado
        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        // 2. Obtener paciente vinculado (autenticado != autorizado, solo pacientes)
        Patient paciente = patientRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Solo pacientes registrados pueden realizar el triaje."));

        // 3. Validar ausencia de códigos duplicados
        if (request.symptoms() == null || request.symptoms().isEmpty()) {
            throw new InvalidDataException("Debe reportar al menos un síntoma.");
        }
        Set<String> vistos = new HashSet<>();
        for (SymptomItemRequest item : request.symptoms()) {
            if (item == null || item.code() == null || item.code().isBlank()) {
                throw new InvalidDataException("El código del síntoma es obligatorio.");
            }
            if (!vistos.add(item.code().trim())) {
                throw new InvalidDataException("Síntoma duplicado en la solicitud: " + item.code());
            }
        }

        // 4. Transformar a EntradaTriaje y evaluar con el motor
        List<ReportedSymptom> reportados = request.symptoms().stream()
                .map(s -> new ReportedSymptom(s.code().trim(), s.durationHours(), s.intensity()))
                .toList();

        TriageInput entrada = new TriageInput(reportados);
        TriageEngine motor = triageEngineFactory.getEngine();
        TriageResult resultado = motor.evaluate(entrada);

        // 5. Mapear códigos a IDs en BD y verificar existencia en el catálogo
        Map<String, Long> mapaSintomas = triageRepository.getSymptomCodeToIdMap();
        for (SymptomItemRequest s : request.symptoms()) {
            if (!mapaSintomas.containsKey(s.code().trim())) {
                throw new InvalidDataException("Síntoma desconocido: " + s.code());
            }
        }

        // 6. Persistir triaje inmutable
        String triajePublicId = UUID.randomUUID().toString();
        Triage triaje = new Triage(
                triajePublicId,
                paciente.id(),
                resultado.rulesVersion(),
                resultado.level().name(),
                resultado.route().name(),
                resultado.emergency(),
                request.notes()
        );
        Long triajeId = triageRepository.saveTriage(triaje);

        // 7. Persistir síntomas asociados
        List<TriageSymptomEntry> sintomasEntidad = request.symptoms().stream()
                .map(s -> new TriageSymptomEntry(
                        triajeId,
                        mapaSintomas.get(s.code().trim()),
                        s.durationHours(),
                        s.intensity()
                ))
                .toList();
        triageRepository.saveSymptoms(triajeId, sintomasEntidad);

        // 8. Registro inmutable de auditoría (sin datos clínicos ni síntomas, ADR-011)
        if (resultado.emergency()) {
            auditService.audit(new AuditEvent(
                    usuario.id(),
                    AuditableAction.TRIAJE_EMERGENCIA,
                    "TRIAJE",
                    triajePublicId,
                    AuditResult.EXITO,
                    ipOrigen
            ));
        }
        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.TRIAJE_REALIZADO,
                "TRIAJE",
                triajePublicId,
                AuditResult.EXITO,
                ipOrigen
        ));

        // 9. Retornar vista consolidada del triaje
        return triageRepository.findByPublicId(triajePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Triaje no encontrado tras guardado."));
    }

    /**
     * Consulta un triaje por su identificador público garantizando aislamiento estricto (ADR-002, ADR-009, HU-02).
     */
    public TriageResponse getByPublicId(
            String publicId,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        Objects.requireNonNull(publicId, "El publicId no puede ser nulo");
        Objects.requireNonNull(usuarioAutenticadoPublicId, "El usuario público no puede ser nulo");

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        TriageResponse triajeResponse = triageRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Triaje no encontrado."));

        Set<String> roles = authorities != null
                ? authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet())
                : Set.of();

        if (clinicalAccessService != null && triajeResponse.patientPublicId() != null) {
            clinicalAccessService.validateClinicalHistoryAccess(usuario.publicId(), triajeResponse.patientPublicId(), authorities);
        } else {
            // Regla no negociable: El Administrador NO accede a contenido clínico (ADR-007, AGENTS.md)
            if (roles.contains("ROLE_ADMINISTRADOR")) {
                throw new UnauthorizedAccessException("El personal administrativo no tiene acceso a informacion clinica.");
            }

            // Aislamiento de paciente: un paciente solo puede ver su propio triaje
            if (roles.contains("ROLE_PACIENTE")) {
                Patient paciente = patientRepository.findByUserId(usuario.id())
                        .orElseThrow(() -> new UnauthorizedAccessException("Solo pacientes registrados pueden consultar su triaje."));

                if (!Objects.equals(triajeResponse.patientPublicId(), paciente.publicId())) {
                    throw new UnauthorizedAccessException("No tiene autorizacion para acceder al triaje de otro paciente.");
                }
            } else if (!roles.contains("ROLE_PROFESIONAL")) {
                throw new UnauthorizedAccessException("No tiene autorizacion para acceder al triaje.");
            }
        }

        return triajeResponse;
    }

    /**
     * Retorna el catálogo de síntomas activos para la interfaz de triaje (ADR-009, HU-02).
     */
    public List<SymptomCatalogResponse> getSymptomCatalog() {
        return triageRepository.listActiveSymptomCatalog();
    }
}
