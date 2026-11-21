package com.meditriaje.service;

import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.dto.prescription.CreatePrescriptionDetailRequest;
import com.meditriaje.dto.prescription.CreatePrescriptionRequest;
import com.meditriaje.dto.prescription.MedicationResponse;
import com.meditriaje.dto.prescription.PrescriptionResponse;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.ClinicalAttention;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.Medication;
import com.meditriaje.model.Patient;
import com.meditriaje.model.Professional;
import com.meditriaje.model.Prescription;
import com.meditriaje.model.PrescriptionDetail;
import com.meditriaje.model.AuditResult;
import com.meditriaje.model.User;
import com.meditriaje.repository.ClinicalAttentionRepository;
import com.meditriaje.repository.MedicationRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.ProfessionalRepository;
import com.meditriaje.repository.PrescriptionRepository;
import com.meditriaje.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para la gestión de recetas médicas y catálogo de medicamentos
 * (M7.2, HU-08, ADR-007, ADR-008, ADR-011).
 */
@Service
public class PrescriptionService {

    public static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

    private final UserRepository userRepository;
    private final ProfessionalRepository professionalRepository;
    private final PatientRepository patientRepository;
    private final ClinicalAttentionRepository attentionRepository;
    private final MedicationRepository medicationRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final ClinicalAccessService clinicalAccessService;
    private final AuditService auditService;
    private final Clock clock;

    @Autowired
    public PrescriptionService(
            UserRepository userRepository,
            ProfessionalRepository professionalRepository,
            PatientRepository patientRepository,
            ClinicalAttentionRepository attentionRepository,
            MedicationRepository medicationRepository,
            PrescriptionRepository prescriptionRepository,
            ClinicalAccessService clinicalAccessService,
            AuditService auditService
    ) {
        this(
                userRepository,
                professionalRepository,
                patientRepository,
                attentionRepository,
                medicationRepository,
                prescriptionRepository,
                clinicalAccessService,
                auditService,
                Clock.system(BOGOTA_ZONE)
        );
    }

    public PrescriptionService(
            UserRepository userRepository,
            ProfessionalRepository professionalRepository,
            PatientRepository patientRepository,
            ClinicalAttentionRepository attentionRepository,
            MedicationRepository medicationRepository,
            PrescriptionRepository prescriptionRepository,
            ClinicalAccessService clinicalAccessService,
            AuditService auditService,
            Clock clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "usuarioRepository no puede ser nulo");
        this.professionalRepository = Objects.requireNonNull(professionalRepository, "profesionalRepository no puede ser nulo");
        this.patientRepository = Objects.requireNonNull(patientRepository, "pacienteRepository no puede ser nulo");
        this.attentionRepository = Objects.requireNonNull(attentionRepository, "atencionRepository no puede ser nulo");
        this.medicationRepository = Objects.requireNonNull(medicationRepository, "medicamentoRepository no puede ser nulo");
        this.prescriptionRepository = Objects.requireNonNull(prescriptionRepository, "recetaRepository no puede ser nulo");
        this.clinicalAccessService = Objects.requireNonNull(clinicalAccessService, "accesoClinicoService no puede ser nulo");
        this.auditService = Objects.requireNonNull(auditService, "auditoriaService no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    /**
     * Emite una nueva receta médica asociada a una atención (HU-08).
     * Congela snapshots inmutables de los fármacos prescritos y audita la acción (ADR-008, ADR-011).
     *
     * @param request Datos de la prescripción y medicamentos solicitados
     * @param usuarioAutenticadoPublicId Identificador del usuario que emite la receta
     * @param ipOrigen Dirección IP del cliente
     * @return RecetaResponse con la receta y sus ítems consolidados
     */
    @Transactional
    public PrescriptionResponse issuePrescription(CreatePrescriptionRequest request, String usuarioAutenticadoPublicId, String ipOrigen) {
        Objects.requireNonNull(request, "request no puede ser nulo");

        // 1. Identificar profesional asistencial autenticado
        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como profesional asistencial."));
        Professional profesional = professionalRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como profesional asistencial."));

        // 2. Buscar atención médica
        ClinicalAttention atencion = attentionRepository.findEntityByPublicId(request.attentionPublicId())
                .orElseThrow(() -> new ResourceNotFoundException("Atencion medica no encontrada."));

        // 3. Autorización asistencial (ADR-007)
        if (!atencion.professionalId().equals(profesional.id())) {
            clinicalAccessService.validateCareRelationship(profesional.id(), atencion.patientId());
        }

        // 4. Validar medicamentos activos y preparar snapshot inmutable
        if (request.details() == null || request.details().isEmpty()) {
            throw new InvalidDataException("La receta debe contener al menos un medicamento prescrito.");
        }

        List<Medication> medicamentosValidados = new ArrayList<>();
        for (CreatePrescriptionDetailRequest detalleReq : request.details()) {
            Medication med = medicationRepository.findByPublicId(detalleReq.medicationPublicId())
                    .orElseThrow(() -> new InvalidDataException("El medicamento no existe o no se encuentra activo: " + detalleReq.medicationPublicId()));

            if (!med.isActive()) {
                throw new InvalidDataException("El medicamento no existe o no se encuentra activo: " + detalleReq.medicationPublicId());
            }
            medicamentosValidados.add(med);
        }

        // 5. Persistencia atómica de la receta
        String recetaPublicId = UUID.randomUUID().toString();
        int vigencia = request.validityDays() != null ? request.validityDays() : 30;

        Prescription receta = new Prescription(
                null,
                recetaPublicId,
                atencion.id(),
                atencion.patientId(),
                profesional.id(),
                vigencia,
                clock.instant()
        );

        Long recetaId = prescriptionRepository.createPrescription(receta);

        List<PrescriptionDetail> detalles = new ArrayList<>();
        for (int i = 0; i < request.details().size(); i++) {
            CreatePrescriptionDetailRequest detalleReq = request.details().get(i);
            Medication med = medicamentosValidados.get(i);

            detalles.add(new PrescriptionDetail(
                    null,
                    recetaId,
                    med.id(),
                    med.brandName(),
                    med.activeIngredient(),
                    med.presentation(),
                    med.concentration(),
                    detalleReq.dose(),
                    detalleReq.frequency(),
                    detalleReq.durationDays(),
                    detalleReq.quantity(),
                    detalleReq.instructions()
            ));
        }

        prescriptionRepository.saveDetails(recetaId, detalles);

        // 6. Auditoría inmutable obligatoria (ADR-011) — Cero datos clínicos ni medicamentos en logs
        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.CREACION_RECETA,
                "RECETA",
                recetaPublicId,
                AuditResult.EXITO,
                ipOrigen
        ));

        // 7. Retornar receta consultada desde la BD
        return prescriptionRepository.findByPublicId(recetaPublicId)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar la receta medica recien emitida."));
    }

    /**
     * Consulta una receta médica por su identificador público, validando estrictamente los permisos
     * y relación asistencial según el rol del usuario autenticado (ADR-007, HU-08).
     *
     * @param publicId Identificador UUID de la receta
     * @param usuarioAutenticadoPublicId Identificador del usuario solicitante
     * @param authorities Autoridades/roles del usuario solicitante
     * @return RecetaResponse con el detalle inmutable de la receta
     */
    public PrescriptionResponse getByPublicId(
            String publicId,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        if (publicId == null || publicId.isBlank()) {
            throw new ResourceNotFoundException("Receta medica no encontrada.");
        }
        if (usuarioAutenticadoPublicId == null || authorities == null || authorities.isEmpty()) {
            throw new UnauthorizedAccessException("Usuario no autenticado o sin roles asignados.");
        }

        Set<String> roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        // Regla ADR-007: El administrador NUNCA accede a recetas ni contenido clínico
        if (roles.contains(ClinicalAccessService.ROLE_ADMIN)) {
            throw new UnauthorizedAccessException("El personal administrativo no tiene acceso a recetas ni contenido clinico.");
        }

        Prescription receta = prescriptionRepository.findEntityByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Receta medica no encontrada."));

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado."));

        if (roles.contains(ClinicalAccessService.ROLE_PATIENT)) {
            Patient paciente = patientRepository.findByUserId(usuario.id())
                    .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como paciente."));

            if (!receta.patientId().equals(paciente.id())) {
                throw new UnauthorizedAccessException("No tiene autorizacion para acceder a la receta de otro paciente.");
            }
        } else if (roles.contains(ClinicalAccessService.ROLE_PROFESSIONAL)) {
            Professional profesional = professionalRepository.findByUserId(usuario.id())
                    .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como profesional asistencial."));

            boolean esAutor = receta.professionalId().equals(profesional.id());
            if (!esAutor) {
                clinicalAccessService.validateCareRelationship(profesional.id(), receta.patientId());
            }
        } else {
            throw new UnauthorizedAccessException("Rol no autorizado para consultar recetas medicas.");
        }

        return prescriptionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Receta medica no encontrada."));
    }

    /**
     * Consulta paginada del catálogo maestro de medicamentos activos (HU-08).
     *
     * @param query Término de búsqueda por nombre, principio activo o código (opcional)
     * @param page Número de página (0-indexed)
     * @param size Cantidad de elementos por página
     * @return Respuesta paginada con los medicamentos coincidentes
     */
    public PaginatedResponse<MedicationResponse> listCatalog(String query, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);

        List<MedicationResponse> content = medicationRepository.listActive(query, safePage, safeSize);
        int total = medicationRepository.countActive(query);

        return PaginatedResponse.of(content, safePage, safeSize, total);
    }

    /**
     * Consulta paginada de las recetas médicas emitidas para el paciente actualmente autenticado (HU-08, HU-09).
     * Solo lectura, sin exponer identificadores numéricos autonuméricos de base de datos.
     *
     * @param usuarioAutenticadoPublicId Identificador público UUID del usuario autenticado
     * @param page Número de página (0-indexed)
     * @param size Cantidad de elementos por página (1..100)
     * @param ipOrigen Dirección IP del cliente para fines de auditoría inmutable
     * @return PaginatedResponse con las recetas del paciente
     */
    public PaginatedResponse<PrescriptionResponse> getMyPrescriptions(
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

        List<PrescriptionResponse> content = prescriptionRepository.listByPatientId(paciente.id(), safePage, safeSize);
        int total = prescriptionRepository.countByPatientId(paciente.id());

        // Auditar consulta de recetas del paciente (ADR-011, sin datos clínicos ni fármacos)
        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.CONSULTA_HISTORIA,
                "RECETA",
                paciente.publicId(),
                AuditResult.EXITO,
                ipOrigen
        ));

        return PaginatedResponse.of(content, safePage, safeSize, total);
    }
}
