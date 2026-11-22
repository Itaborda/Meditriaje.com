package com.meditriaje.service;

import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.dto.pharmacy.DeliveryDetailRequest;
import com.meditriaje.dto.pharmacy.DispensationResponse;
import com.meditriaje.dto.pharmacy.PrescriptionDispensationResponse;
import com.meditriaje.dto.pharmacy.RegisterDispensationRequest;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.Dispensation;
import com.meditriaje.model.DispensationDetail;
import com.meditriaje.model.Patient;
import com.meditriaje.model.Prescription;
import com.meditriaje.model.AuditResult;
import com.meditriaje.model.Site;
import com.meditriaje.model.User;
import com.meditriaje.repository.DispensationRepository;
import com.meditriaje.repository.DispensationRepository.PrescribedItemInfo;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.PrescriptionRepository;
import com.meditriaje.repository.SiteRepository;
import com.meditriaje.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Servicio de dispensación farmacéutica de recetas médicas y control de saldos (F2.4, ADR-016).
 * Aplica reglas de vigencia, control estricto de entregas parciales y totales, trazabilidad INVIMA y auditoría.
 */
@Service
public class DispensationService {

    private final UserRepository userRepository;
    private final SiteRepository siteRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PatientRepository patientRepository;
    private final DispensationRepository dispensationRepository;
    private final AuditService auditService;
    private final Clock clock;

    public DispensationService(
            UserRepository userRepository,
            SiteRepository siteRepository,
            PrescriptionRepository prescriptionRepository,
            PatientRepository patientRepository,
            DispensationRepository dispensationRepository,
            AuditService auditService,
            Clock clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "usuarioRepository no puede ser nulo");
        this.siteRepository = Objects.requireNonNull(siteRepository, "siteRepository no puede ser nulo");
        this.prescriptionRepository = Objects.requireNonNull(prescriptionRepository, "recetaRepository no puede ser nulo");
        this.patientRepository = Objects.requireNonNull(patientRepository, "pacienteRepository no puede ser nulo");
        this.dispensationRepository = Objects.requireNonNull(dispensationRepository, "dispensacionRepository no puede ser nulo");
        this.auditService = Objects.requireNonNull(auditService, "auditoriaService no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    /**
     * Registra un evento de dispensación en farmacia con control estricto de saldos y vigencia (ADR-016).
     *
     * @param request                   Datos de la dispensación y cantidades por medicamento
     * @param usuarioAutenticadoPublicId UUID del dispensador autenticado
     * @param authorities               Roles del usuario autenticado
     * @param ipOrigen                  IP del cliente
     * @return DispensacionResponse con detalles consolidados
     */
    @Transactional
    public DispensationResponse registerDispensation(
            RegisterDispensationRequest request,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        validatePharmacistRole(authorities);

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario dispensador no encontrado."));

        if (!"ACTIVO".equalsIgnoreCase(usuario.status())) {
            throw new UnauthorizedAccessException("El usuario dispensador se encuentra inactivo o bloqueado.");
        }

        // Hospital único: la dispensación se registra siempre en la única sede (la resuelve el servidor).
        Site sede = siteRepository.getSingleSite();

        Prescription receta = prescriptionRepository.findEntityByPublicId(request.prescriptionPublicId())
                .orElseThrow(() -> new ResourceNotFoundException("Receta médica no encontrada: " + request.prescriptionPublicId()));

        // 1. Validación estricta de vigencia de la receta
        Instant fechaEmision = receta.createdAt() != null ? receta.createdAt() : Instant.now(clock);
        Instant fechaVencimiento = fechaEmision.plus(receta.validityDays(), ChronoUnit.DAYS);
        Instant ahora = Instant.now(clock);

        if (ahora.isAfter(fechaVencimiento)) {
            throw new InvalidDataException("La receta médica ha expirado (vigencia de " + receta.validityDays()
                    + " días finalizada el " + fechaVencimiento + "). No es posible dispensar medicamentos.");
        }

        // 2. Validación de lista no vacía y sin duplicados
        List<DeliveryDetailRequest> detallesRequest = request.details();
        if (detallesRequest == null || detallesRequest.isEmpty()) {
            throw new InvalidDataException("Debe especificar al menos un medicamento para dispensar.");
        }

        Set<String> medicamentosEnSolicitud = new HashSet<>();
        for (DeliveryDetailRequest det : detallesRequest) {
            if (!medicamentosEnSolicitud.add(det.medicationPublicId().trim())) {
                throw new InvalidDataException("La solicitud contiene medicamentos duplicados: " + det.medicationPublicId());
            }
        }

        // 3. Validación de correspondencia con la receta y control matemático de saldos
        List<PrescribedItemInfo> itemsPrescritos = dispensationRepository.findPrescriptionItems(receta.id());
        Map<String, PrescribedItemInfo> itemsMap = itemsPrescritos.stream()
                .collect(Collectors.toMap(PrescribedItemInfo::medicationPublicId, Function.identity()));

        Map<Long, Integer> previosDispensados = dispensationRepository.getDispensedTotalsByPrescriptionId(receta.id());

        for (DeliveryDetailRequest det : detallesRequest) {
            PrescribedItemInfo item = itemsMap.get(det.medicationPublicId().trim());
            if (item == null) {
                throw new InvalidDataException("El medicamento con ID " + det.medicationPublicId()
                        + " no forma parte de la receta médica especificada.");
            }

            int yaDispensado = previosDispensados.getOrDefault(item.prescriptionDetailId(), 0);
            int saldoDisponible = item.prescribedQuantity() - yaDispensado;

            if (saldoDisponible <= 0) {
                throw new InvalidDataException("El medicamento '" + item.medicationName()
                        + "' ya ha sido dispensado en su totalidad (saldo: 0).");
            }

            if (det.deliveredQuantity() > saldoDisponible) {
                throw new InvalidDataException("La cantidad a entregar (" + det.deliveredQuantity()
                        + ") excede el saldo pendiente (" + saldoDisponible + ") para el medicamento '"
                        + item.medicationName() + "'.");
            }
        }

        // 4. Persistencia atómica de la cabecera y detalles de dispensación
        String dispensacionPublicId = UUID.randomUUID().toString();
        Dispensation dispensacion = new Dispensation(
                null,
                dispensacionPublicId,
                receta.id(),
                sede.id(),
                usuario.id(),
                request.notes() != null ? request.notes().trim() : null,
                ahora
        );

        Long dispensacionId = dispensationRepository.createDispensation(dispensacion);

        List<DispensationDetail> detallesEntities = detallesRequest.stream().map(d -> {
            PrescribedItemInfo item = itemsMap.get(d.medicationPublicId().trim());
            return new DispensationDetail(
                    null,
                    dispensacionId,
                    item.prescriptionDetailId(),
                    d.deliveredQuantity(),
                    d.batch() != null ? d.batch().trim() : null,
                    d.batchExpirationDate(),
                    ahora
            );
        }).toList();

        dispensationRepository.saveDetails(dispensacionId, detallesEntities);

        // 5. Auditoría inmutable obligatoria (ADR-011, ADR-016)
        auditService.recordEvent(
                usuario.id(),
                AuditableAction.DISPENSACION_RECETA,
                "RECETA",
                receta.publicId(),
                AuditResult.EXITO,
                ipOrigen
        );

        return dispensationRepository.findByPublicId(dispensacionPublicId)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar la dispensación recién registrada."));
    }

    /**
     * Consulta el detalle y saldos de una receta médica para ventanilla de farmacia.
     *
     * @param recetaPublicId UUID público de la receta
     * @param authorities    Roles del usuario solicitante
     * @return RecetaDispensacionResponse con saldos y entregas previas
     */
    public PrescriptionDispensationResponse getPrescriptionForPharmacy(
            String recetaPublicId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        validatePharmacistRole(authorities);
        return dispensationRepository.findPrescriptionDispensationByPublicId(recetaPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Receta médica no encontrada: " + recetaPublicId));
    }

    /**
     * Búsqueda paginada de recetas para ventanilla de farmacia por documento o código.
     *
     * @param query       Término de búsqueda (código de reclamación REC-XXXXXXXX, documento o nombre del paciente)
     * @param page        Página (0-indexada)
     * @param size        Tamaño de página
     * @param authorities Roles del solicitante
     * @return PaginatedResponse con recetas para dispensación
     */
    public PaginatedResponse<PrescriptionDispensationResponse> searchPrescriptionsForPharmacy(
            String query,
            int page,
            int size,
            Collection<? extends GrantedAuthority> authorities
    ) {
        validatePharmacistRole(authorities);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));

        List<PrescriptionDispensationResponse> content = dispensationRepository.searchPrescriptionDispensations(query, safePage, safeSize);
        int total = dispensationRepository.countPrescriptionDispensations(query);

        return PaginatedResponse.of(content, safePage, safeSize, total);
    }

    /**
     * Consulta del estado de entrega y código de reclamación de una receta por parte del paciente titular.
     *
     * @param recetaPublicId             UUID público de la receta
     * @param usuarioAutenticadoPublicId UUID del paciente autenticado
     * @return RecetaDispensacionResponse del paciente
     */
    public PrescriptionDispensationResponse getPatientDispensation(
            String recetaPublicId,
            String usuarioAutenticadoPublicId
    ) {
        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        Patient paciente = patientRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como paciente en el sistema."));

        Prescription receta = prescriptionRepository.findEntityByPublicId(recetaPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Receta médica no encontrada: " + recetaPublicId));

        if (!receta.patientId().equals(paciente.id())) {
            throw new UnauthorizedAccessException("No tiene autorización para consultar recetas médicas de otro paciente.");
        }

        return dispensationRepository.findPrescriptionDispensationByPublicId(recetaPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Receta médica no encontrada: " + recetaPublicId));
    }

    /**
     * Consulta una dispensación individual por su UUID público.
     *
     * @param dispensacionPublicId UUID público de la dispensación
     * @return DispensacionResponse
     */
    public DispensationResponse getDispensationByPublicId(String dispensacionPublicId) {
        return dispensationRepository.findByPublicId(dispensacionPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispensación no encontrada: " + dispensacionPublicId));
    }

    private void validatePharmacistRole(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null) {
            throw new UnauthorizedAccessException("Acceso restringido: credenciales de autorización ausentes.");
        }

        boolean esFarmaceutico = authorities.stream()
                .anyMatch(a -> "ROLE_FARMACEUTICO".equals(a.getAuthority()));

        if (!esFarmaceutico) {
            throw new UnauthorizedAccessException("Solo personal de farmacia autorizado (ROLE_FARMACEUTICO) puede acceder a esta operación.");
        }
    }
}
