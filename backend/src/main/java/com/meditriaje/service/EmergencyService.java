package com.meditriaje.service;

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
import com.meditriaje.dto.triage.SymptomCatalogResponse;
import com.meditriaje.dto.triage.SymptomItemRequest;
import com.meditriaje.dto.triage.SymptomItemResponse;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.OperationConflictException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.CareAssignment;
import com.meditriaje.model.CareEpisode;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.ProvisionalIdentity;
import com.meditriaje.model.EmergencyAdmission;
import com.meditriaje.model.Patient;
import com.meditriaje.model.Professional;
import com.meditriaje.model.AuditResult;
import com.meditriaje.model.Site;
import com.meditriaje.model.User;
import com.meditriaje.model.TriageAssessment;
import com.meditriaje.repository.CareAssignmentRepository;
import com.meditriaje.repository.CareEpisodeRepository;
import com.meditriaje.repository.ProvisionalIdentityRepository;
import com.meditriaje.repository.EmergencyAdmissionRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.ProfessionalRepository;
import com.meditriaje.repository.SiteRepository;
import com.meditriaje.repository.TriageAssessmentRepository;
import com.meditriaje.repository.TriageRepository;
import com.meditriaje.repository.UserRepository;
import com.meditriaje.triage.ReportedSymptom;
import com.meditriaje.triage.TriageEngineFactory;
import com.meditriaje.triage.TriageInput;
import com.meditriaje.triage.TriageResult;
import com.meditriaje.util.ColombianStandardValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Servicio integral para admisiones de urgencias, identidades provisionales, triaje presencial
 * y asignación del equipo asistencial (Fase U, ADR-022, ADR-023, ADR-026, ADR-027).
 */
@Service
@Transactional(readOnly = true)
public class EmergencyService {

    public static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CareEpisodeRepository episodeRepository;
    private final EmergencyAdmissionRepository admissionRepository;
    private final ProvisionalIdentityRepository identityRepository;
    private final TriageAssessmentRepository assessmentRepository;
    private final CareAssignmentRepository careAssignmentRepository;
    private final SiteRepository siteRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final ProfessionalRepository professionalRepository;
    private final TriageEngineFactory triageEngineFactory;
    private final TriageRepository triageRepository;
    private final AuditService auditService;
    private final Clock clock;

    public EmergencyService(
            CareEpisodeRepository episodeRepository,
            EmergencyAdmissionRepository admissionRepository,
            ProvisionalIdentityRepository identityRepository,
            TriageAssessmentRepository assessmentRepository,
            CareAssignmentRepository careAssignmentRepository,
            SiteRepository siteRepository,
            PatientRepository patientRepository,
            UserRepository userRepository,
            ProfessionalRepository professionalRepository,
            TriageEngineFactory triageEngineFactory,
            TriageRepository triageRepository,
            AuditService auditService
    ) {
        this(
                episodeRepository,
                admissionRepository,
                identityRepository,
                assessmentRepository,
                careAssignmentRepository,
                siteRepository,
                patientRepository,
                userRepository,
                professionalRepository,
                triageEngineFactory,
                triageRepository,
                auditService,
                Clock.system(BOGOTA_ZONE)
        );
    }

    @Autowired
    public EmergencyService(
            CareEpisodeRepository episodeRepository,
            EmergencyAdmissionRepository admissionRepository,
            ProvisionalIdentityRepository identityRepository,
            TriageAssessmentRepository assessmentRepository,
            CareAssignmentRepository careAssignmentRepository,
            SiteRepository siteRepository,
            PatientRepository patientRepository,
            UserRepository userRepository,
            ProfessionalRepository professionalRepository,
            TriageEngineFactory triageEngineFactory,
            TriageRepository triageRepository,
            AuditService auditService,
            Clock clock
    ) {
        this.episodeRepository = Objects.requireNonNull(episodeRepository);
        this.admissionRepository = Objects.requireNonNull(admissionRepository);
        this.identityRepository = Objects.requireNonNull(identityRepository);
        this.assessmentRepository = Objects.requireNonNull(assessmentRepository);
        this.careAssignmentRepository = Objects.requireNonNull(careAssignmentRepository);
        this.siteRepository = Objects.requireNonNull(siteRepository);
        this.patientRepository = Objects.requireNonNull(patientRepository);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.professionalRepository = Objects.requireNonNull(professionalRepository);
        this.triageEngineFactory = Objects.requireNonNull(triageEngineFactory);
        this.triageRepository = Objects.requireNonNull(triageRepository);
        this.auditService = Objects.requireNonNull(auditService);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Busca pacientes por número de documento (y tipo opcional) para admisión presencial o reconciliación NN.
     */
    public List<EmergencyPatientLookupResponse> searchPatientsByDocument(String documentNumber, String documentType) {
        if (documentNumber == null || documentNumber.isBlank()) {
            return List.of();
        }
        String normalizedType = (documentType == null || documentType.isBlank())
                ? null
                : documentType.trim().toUpperCase(Locale.ROOT);
        return patientRepository.searchByDocument(documentNumber.trim(), normalizedType).stream()
                .map(p -> new EmergencyPatientLookupResponse(
                        p.publicId(),
                        p.documentType(),
                        p.documentNumber(),
                        p.firstNames(),
                        p.lastNames(),
                        p.firstNames() + " " + p.lastNames(),
                        p.birthDate(),
                        p.phone(),
                        p.userId() != null
                ))
                .toList();
    }

    /**
     * Registra un ingreso de urgencia presencial (U02).
     * Acepta pacientes registrados (por UUID o número de documento), pacientes presenciales sin cuenta
     * previa en MediTriaje o pacientes indocumentados (NN con código provisional opaco).
     */
    @Transactional
    public EmergencyEpisodeResponse registerEmergencyAdmission(
            RegisterEmergencyAdmissionRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        User registrador = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario registrador no encontrado."));

        // Hospital único: el episodio se abre siempre en la única sede (la resuelve el servidor).
        Site sede = siteRepository.getSingleSite();

        Long pacienteId = null;
        boolean hasPatientRef = request.patientPublicId() != null && !request.patientPublicId().isBlank();
        boolean hasInlinePatient = request.patientDocumentNumber() != null && !request.patientDocumentNumber().isBlank();
        boolean esProvisional = request.isProvisionalIdentity() || (!hasPatientRef && !hasInlinePatient);

        if (!esProvisional) {
            if (hasPatientRef) {
                pacienteId = resolvePatientByRef(request.patientPublicId()).id();
            } else {
                pacienteId = resolveOrCreateWalkInPatient(request, registrador.id(), ipOrigen);
            }
        }

        String episodioPubId = UUID.randomUUID().toString();
        Instant ahora = Instant.now(clock);

        CareEpisode episodio = new CareEpisode(
                null,
                episodioPubId,
                pacienteId,
                "URGENCIA",
                sede.id(),
                "REGISTRADO",
                ahora,
                null,
                request.arrivalReason(),
                ahora,
                ahora
        );
        CareEpisode guardado = episodeRepository.save(episodio);

        // Guardar detalle de ingreso presencial
        EmergencyAdmission ingreso = new EmergencyAdmission(
                null,
                UUID.randomUUID().toString(),
                guardado.id(),
                request.arrivalMode(),
                request.companionName(),
                request.companionContact(),
                request.arrivalReason(),
                request.notes(),
                registrador.id(),
                ahora
        );
        admissionRepository.save(ingreso);

        String codigoProvisional = null;
        if (esProvisional) {
            codigoProvisional = generateProvisionalCode();
            ProvisionalIdentity identidad = new ProvisionalIdentity(
                    null,
                    UUID.randomUUID().toString(),
                    guardado.id(),
                    codigoProvisional,
                    request.physicalDescription(),
                    request.apparentAge(),
                    request.apparentGender(),
                    request.arrivalCondition(),
                    "PROVISIONAL",
                    null,
                    null,
                    null,
                    ahora
            );
            identityRepository.save(identidad);

            auditService.audit(new AuditEvent(
                    registrador.id(),
                    AuditableAction.IDENTIDAD_PROVISIONAL_REGISTRADA,
                    "EPISODIO_ATENCION",
                    guardado.publicId(),
                    AuditResult.EXITO,
                    ipOrigen
            ));
        }

        auditService.audit(new AuditEvent(
                registrador.id(),
                AuditableAction.INGRESO_URGENCIA_REGISTRADO,
                "EPISODIO_ATENCION",
                guardado.publicId(),
                AuditResult.EXITO,
                ipOrigen
        ));

        return episodeRepository.findDetailByPublicId(episodioPubId)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar episodio recién creado."));
    }

    /**
     * Consulta el detalle de un episodio de urgencias con comprobaciones de autorización (U02).
     */
    public EmergencyEpisodeResponse getEpisodeDetail(
            String episodePublicId,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        EmergencyEpisodeResponse detalle = episodeRepository.findDetailByPublicId(episodePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Episodio de urgencias no encontrado: " + episodePublicId));

        Set<String> roles = authorities != null
                ? authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet())
                : Set.of();

        if (roles.contains("ROLE_PACIENTE")) {
            User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado."));
            Patient paciente = patientRepository.findByUserId(usuario.id())
                    .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como paciente."));

            if (!Objects.equals(detalle.patientPublicId(), paciente.publicId())) {
                throw new UnauthorizedAccessException("No tiene autorización para consultar episodios de otro paciente.");
            }
        }

        return detalle;
    }

    /**
     * Reconcilia la identidad provisional de un paciente NN hacia su registro civil verificado (U03).
     */
    @Transactional
    public EmergencyEpisodeResponse reconcileIdentity(
            String episodePublicId,
            ReconcileIdentityRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        User confirmador = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario confirmador no encontrado."));

        CareEpisode episodio = episodeRepository.findByPublicId(episodePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Episodio no encontrado: " + episodePublicId));

        ProvisionalIdentity identidad = identityRepository.findByEpisodeId(episodio.id())
                .orElseThrow(() -> new InvalidDataException("El episodio no cuenta con una identidad provisional para reconciliar."));

        if (!"PROVISIONAL".equals(identidad.status())) {
            throw new OperationConflictException("La identidad provisional ya fue reconciliada o no está activa.");
        }

        Patient pacienteVerificado = resolvePatientByRef(request.patientPublicId());

        Instant ahora = Instant.now(clock);
        identityRepository.reconcileIdentity(identidad.id(), pacienteVerificado.id(), confirmador.id(), ahora);
        episodeRepository.linkPatient(episodio.id(), pacienteVerificado.id());

        auditService.audit(new AuditEvent(
                confirmador.id(),
                AuditableAction.IDENTIDAD_PROVISIONAL_RECONCILIADA,
                "IDENTIDAD_PROVISIONAL",
                identidad.publicId(),
                AuditResult.EXITO,
                ipOrigen
        ));

        return episodeRepository.findDetailByPublicId(episodePublicId)
                .orElseThrow(() -> new IllegalStateException("Error al consultar episodio reconciliado."));
    }

    /**
     * Calcula el nivel de prioridad de triaje a partir de los síntomas ingresados sin persistir la valoración.
     */
    public TriageCalculationResponse calculateTriageLevel(CalculateTriageLevelRequest request) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        TriageResult resultado = evaluateSymptoms(request.symptoms());
        return new TriageCalculationResponse(
                resultado.level().name(),
                resultado.route().name(),
                resultado.emergency(),
                resultado.rulesVersion(),
                resultado.alarmSymptoms(),
                resultado.message()
        );
    }

    /**
     * Registra una valoración clínica presencial de triaje o reevaluación (U04).
     * El nivel de prioridad se calcula automáticamente a partir de los síntomas reportados.
     */
    @Transactional
    public TriageAssessmentResponse recordTriageAssessment(
            String episodePublicId,
            RegisterTriageAssessmentRequest request,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        Set<String> roles = authorities != null
                ? authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet())
                : Set.of();

        // Solo personal de enfermería o profesionales asistenciales habilitados
        if (!roles.contains("ROLE_ENFERMERIA") && !roles.contains("ROLE_PROFESIONAL")) {
            throw new UnauthorizedAccessException("Solo personal de enfermería o profesionales asistenciales pueden realizar valoraciones de triaje.");
        }

        User evaluador = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Evaluador no encontrado."));

        CareEpisode episodio = episodeRepository.findByPublicId(episodePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Episodio no encontrado: " + episodePublicId));

        if ("EGRESADO".equals(episodio.status()) || "CANCELADO".equals(episodio.status())) {
            throw new OperationConflictException("No es posible registrar triaje en un episodio cerrado o egresado.");
        }

        // Calcular nivel de prioridad automáticamente con el motor determinista de síntomas
        TriageResult resultado = evaluateSymptoms(request.symptoms());
        String nivelCalculado = resultado.level().name();

        Map<String, Long> mapaSintomas = triageRepository.getSymptomCodeToIdMap();
        for (SymptomItemRequest item : request.symptoms()) {
            String codigo = item.code().trim();
            if (!mapaSintomas.containsKey(codigo)) {
                throw new InvalidDataException("Síntoma desconocido: " + item.code());
            }
        }

        int siguienteVersion = assessmentRepository.getNextVersion(episodio.id());
        String valoracionPubId = UUID.randomUUID().toString();
        Instant ahora = Instant.now(clock);

        TriageAssessment valoracion = new TriageAssessment(
                null,
                valoracionPubId,
                episodio.id(),
                siguienteVersion,
                nivelCalculado,
                request.consultationReason(),
                request.clinicalFindings(),
                request.bloodPressure(),
                request.heartRate(),
                request.respiratoryRate(),
                request.oxygenSaturation(),
                request.temperature(),
                request.glasgowScale(),
                evaluador.id(),
                request.isReevaluation() || siguienteVersion > 1,
                request.reevaluationReason(),
                ahora
        );
        TriageAssessment guardada = assessmentRepository.save(valoracion);

        List<TriageAssessmentRepository.AssessmentSymptomEntry> sintomasEntidad = request.symptoms().stream()
                .map(s -> new TriageAssessmentRepository.AssessmentSymptomEntry(
                        mapaSintomas.get(s.code().trim()),
                        s.durationHours(),
                        s.intensity()
                ))
                .toList();
        if (guardada != null && guardada.id() != null) {
            assessmentRepository.saveSymptoms(guardada.id(), sintomasEntidad);
        }

        // Actualizar estado del episodio según la urgencia del nivel calculado
        if ("I".equals(nivelCalculado) || "II".equals(nivelCalculado)) {
            episodeRepository.updateStatus(episodio.id(), "EN_ATENCION");
        } else if ("REGISTRADO".equals(episodio.status())) {
            episodeRepository.updateStatus(episodio.id(), "EN_TRIAJE");
        }

        auditService.audit(new AuditEvent(
                evaluador.id(),
                AuditableAction.VALORACION_TRIAJE_REGISTRADA,
                "VALORACION_TRIAJE",
                valoracionPubId,
                AuditResult.EXITO,
                ipOrigen
        ));

        List<SymptomCatalogResponse> catalogo = triageRepository.listActiveSymptomCatalog();
        Map<String, SymptomCatalogResponse> catalogoPorCodigo = catalogo == null
                ? Map.of()
                : catalogo.stream().collect(Collectors.toMap(
                        SymptomCatalogResponse::code,
                        Function.identity(),
                        (a, b) -> a));

        List<SymptomItemResponse> sintomasRespuesta = request.symptoms().stream()
                .map(s -> {
                    String codigo = s.code().trim();
                    SymptomCatalogResponse info = catalogoPorCodigo.get(codigo);
                    String nombre = info != null && info.name() != null ? info.name() : codigo;
                    boolean esAlarma = info != null ? info.isAlarm() : resultado.alarmSymptoms().contains(codigo);
                    return new SymptomItemResponse(codigo, nombre, s.durationHours(), s.intensity(), esAlarma);
                })
                .sorted(Comparator.comparing(SymptomItemResponse::name))
                .toList();

        return new TriageAssessmentResponse(
                valoracionPubId,
                episodio.publicId(),
                siguienteVersion,
                nivelCalculado,
                request.consultationReason(),
                request.clinicalFindings(),
                request.bloodPressure(),
                request.heartRate(),
                request.respiratoryRate(),
                request.oxygenSaturation(),
                request.temperature(),
                request.glasgowScale(),
                evaluador.email(),
                request.isReevaluation() || siguienteVersion > 1,
                request.reevaluationReason(),
                ahora,
                sintomasRespuesta
        );
    }

    private TriageResult evaluateSymptoms(List<SymptomItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new InvalidDataException("Debe reportar al menos un síntoma para calcular el nivel de prioridad.");
        }
        List<ReportedSymptom> reportados = items.stream()
                .map(s -> {
                    if (s == null || s.code() == null || s.code().isBlank()) {
                        throw new InvalidDataException("El código del síntoma es obligatorio.");
                    }
                    return new ReportedSymptom(s.code().trim(), s.durationHours(), s.intensity());
                })
                .toList();
        return triageEngineFactory.getEngine().evaluate(new TriageInput(reportados));
    }

    /**
     * Consulta el historial inmutable de valoraciones de triaje de un episodio (U04).
     */
    public List<TriageAssessmentResponse> listTriageHistory(
            String episodePublicId,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        CareEpisode episodio = episodeRepository.findByPublicId(episodePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Episodio no encontrado: " + episodePublicId));

        Set<String> roles = authorities != null
                ? authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet())
                : Set.of();

        if (roles.contains("ROLE_ADMINISTRADOR")) {
            throw new UnauthorizedAccessException("El personal administrativo no tiene acceso a notas clínicas de triaje.");
        }

        if (roles.contains("ROLE_PACIENTE")) {
            User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado."));
            Patient paciente = patientRepository.findByUserId(usuario.id())
                    .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como paciente."));

            if (!Objects.equals(episodio.patientId(), paciente.id())) {
                throw new UnauthorizedAccessException("No tiene autorización para consultar el triaje de otro paciente.");
            }
        }

        return assessmentRepository.listByEpisodeId(episodio.id());
    }

    /**
     * Asigna un profesional asistencial (médico tratante o interconsultor) al episodio (U06).
     */
    @Transactional
    public void assignCareTeam(
            String episodePublicId,
            AssignTeamRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        User asignador = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario asignador no encontrado."));

        CareEpisode episodio = episodeRepository.findByPublicId(episodePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Episodio no encontrado: " + episodePublicId));

        Professional profesional = professionalRepository.findByPublicId(request.professionalPublicId())
                .orElseThrow(() -> new ResourceNotFoundException("Profesional asistencial no encontrado: " + request.professionalPublicId()));

        // Inactivar asignación previa para la misma función si es médico tratante
        if ("MEDICO_TRATANTE".equals(request.roleFunction())) {
            careAssignmentRepository.inactivatePreviousAssignmentByRoleFunction(episodio.id(), "MEDICO_TRATANTE");
        }

        CareAssignment asignacion = new CareAssignment(
                null,
                UUID.randomUUID().toString(),
                episodio.id(),
                profesional.id(),
                request.roleFunction(),
                asignador.id(),
                Instant.now(clock),
                null,
                true,
                Instant.now(clock)
        );
        careAssignmentRepository.save(asignacion);

        if (!"EN_ATENCION".equals(episodio.status()) && !"OBSERVACION".equals(episodio.status())) {
            episodeRepository.updateStatus(episodio.id(), "EN_ATENCION");
        }

        auditService.audit(new AuditEvent(
                asignador.id(),
                AuditableAction.ASIGNACION_ASISTENCIAL_REGISTRADA,
                "EPISODIO_ATENCION",
                episodio.publicId(),
                AuditResult.EXITO,
                ipOrigen
        ));
    }

    /**
     * Consulta la cola priorizada de urgencias del hospital (U05).
     */
    public List<EmergencyQueueItemResponse> listEmergencyQueue(String estadoFiltro) {
        // Hospital único: sin filtro de sede la cola incluye todos los episodios del hospital.
        return episodeRepository.listEmergencyQueueBySite(null, estadoFiltro);
    }

    /**
     * Cierra y egresa un episodio de urgencias (U06).
     */
    @Transactional
    public void closeEpisode(String episodePublicId, String usuarioAutenticadoPublicId, String ipOrigen) {
        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        CareEpisode episodio = episodeRepository.findByPublicId(episodePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Episodio no encontrado: " + episodePublicId));

        if ("EGRESADO".equals(episodio.status()) || "CANCELADO".equals(episodio.status())) {
            throw new OperationConflictException("El episodio ya se encuentra cerrado.");
        }

        Instant ahora = Instant.now(clock);
        episodeRepository.closeEpisode(episodio.id(), ahora);

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.EPISODIO_CERRADO,
                "EPISODIO_ATENCION",
                episodio.publicId(),
                AuditResult.EXITO,
                ipOrigen
        ));
    }

    private Patient resolvePatientByRef(String ref) {
        String trimmed = ref.trim();
        return patientRepository.findByPublicId(trimmed)
                .or(() -> patientRepository.searchByDocument(trimmed, null).stream().findFirst())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado: " + ref));
    }

    private Long resolveOrCreateWalkInPatient(
            RegisterEmergencyAdmissionRequest request,
            Long registradorUserId,
            String ipOrigen
    ) {
        String tipoDoc = (request.patientDocumentType() == null || request.patientDocumentType().isBlank())
                ? "CC"
                : request.patientDocumentType().trim().toUpperCase(Locale.ROOT);
        String numDoc = request.patientDocumentNumber().trim();

        ColombianStandardValidator.validateDocument(tipoDoc, numDoc);

        Optional<Patient> existente = patientRepository.findByDocument(tipoDoc, numDoc);
        if (existente.isPresent()) {
            return existente.get().id();
        }

        ColombianStandardValidator.validateNamesOrSurnames("nombres", request.patientFirstNames());
        ColombianStandardValidator.validateNamesOrSurnames("apellidos", request.patientLastNames());
        if (request.patientBirthDate() == null) {
            throw new InvalidDataException("La fecha de nacimiento del paciente es obligatoria.");
        }
        ColombianStandardValidator.validateDocumentAgeCoherence(tipoDoc, request.patientBirthDate(), clock);
        ColombianStandardValidator.validateColombianPhone(request.patientPhone());

        String nuevoPublicId = UUID.randomUUID().toString();
        String telefono = (request.patientPhone() != null && !request.patientPhone().isBlank())
                ? request.patientPhone().trim()
                : null;

        Long nuevoPacienteId = patientRepository.create(
                null,
                nuevoPublicId,
                tipoDoc,
                numDoc,
                request.patientFirstNames().trim(),
                request.patientLastNames().trim(),
                request.patientBirthDate(),
                telefono
        );

        auditService.audit(new AuditEvent(
                registradorUserId,
                AuditableAction.REGISTRO_PACIENTE,
                "PACIENTE",
                nuevoPublicId,
                AuditResult.EXITO,
                ipOrigen
        ));

        return nuevoPacienteId;
    }

    private String generateProvisionalCode() {
        String fecha = LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE);
        StringBuilder sufijo = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sufijo.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return "NN-" + fecha + "-" + sufijo;
    }
}
