package com.meditriaje.service;

import com.meditriaje.dto.admin.UpdateSpecialtyRequest;
import com.meditriaje.dto.admin.CreateSpecialtyRequest;
import com.meditriaje.dto.admin.SpecialtyResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.Specialty;
import com.meditriaje.model.AuditResult;
import com.meditriaje.model.User;
import com.meditriaje.repository.SpecialtyRepository;
import com.meditriaje.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio de administración de catálogos asistenciales e infraestructura (HU-10, ADR-003, ADR-011).
 * Gestiona las especialidades. El hospital es único: no hay administración de sedes ni instituciones.
 * Aplica auditoría inmutable en cada cambio administrativo.
 */
@Service
public class AdminCatalogService {

    private static final String STATUS_ACTIVE = "ACTIVO";
    private static final String STATUS_INACTIVE = "INACTIVO";
    private static final Set<String> ALLOWED_STATUSES = Set.of(STATUS_ACTIVE, STATUS_INACTIVE);

    private static final String RESOURCE_SPECIALTY = "ESPECIALIDAD";

    private final SpecialtyRepository specialtyRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public AdminCatalogService(
            SpecialtyRepository specialtyRepository,
            UserRepository userRepository,
            AuditService auditService
    ) {
        this.specialtyRepository = Objects.requireNonNull(specialtyRepository, "EspecialidadRepository no puede ser nulo");
        this.userRepository = Objects.requireNonNull(userRepository, "UsuarioRepository no puede ser nulo");
        this.auditService = Objects.requireNonNull(auditService, "AuditoriaService no puede ser nulo");
    }

    // =========================================================================
    // 1. ESPECIALIDADES
    // =========================================================================

    @Transactional
    public SpecialtyResponse createSpecialty(CreateSpecialtyRequest req, String adminPublicId, String ipOrigen) {
        String nombreLimpio = req.name().trim();
        if (specialtyRepository.existsByName(nombreLimpio)) {
            throw new InvalidDataException("Ya existe una especialidad con el nombre: " + nombreLimpio);
        }

        String publicId = UUID.randomUUID().toString();
        Specialty especialidad = specialtyRepository.create(publicId, nombreLimpio, req.slotDurationMinutes());

        Long adminId = getAdminUserId(adminPublicId);
        auditService.recordEvent(
                adminId,
                AuditableAction.CAMBIO_ADMINISTRATIVO,
                RESOURCE_SPECIALTY,
                especialidad.publicId(),
                AuditResult.EXITO,
                ipOrigen
        );

        return toSpecialtyResponse(especialidad);
    }

    @Transactional
    public SpecialtyResponse updateSpecialty(String publicId, UpdateSpecialtyRequest req, String adminPublicId, String ipOrigen) {
        Specialty actual = specialtyRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Especialidad: " + publicId));

        String nombreLimpio = req.name().trim();
        if (specialtyRepository.existsByNameAndNotPublicId(nombreLimpio, publicId)) {
            throw new InvalidDataException("Ya existe otra especialidad con el nombre: " + nombreLimpio);
        }

        specialtyRepository.update(publicId, nombreLimpio, req.slotDurationMinutes());

        Long adminId = getAdminUserId(adminPublicId);
        auditService.recordEvent(
                adminId,
                AuditableAction.CAMBIO_ADMINISTRATIVO,
                RESOURCE_SPECIALTY,
                publicId,
                AuditResult.EXITO,
                ipOrigen
        );

        return new SpecialtyResponse(publicId, nombreLimpio, req.slotDurationMinutes(), actual.status());
    }

    @Transactional(readOnly = true)
    public SpecialtyResponse getSpecialty(String publicId) {
        Specialty especialidad = specialtyRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Especialidad: " + publicId));
        return toSpecialtyResponse(especialidad);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SpecialtyResponse> listSpecialties(int page, int size, String estadoFiltro) {
        String estadoNormalizado = normalizeAndValidateStatus(estadoFiltro);
        List<Specialty> lista = specialtyRepository.list(page, size, estadoNormalizado);
        long total = specialtyRepository.count(estadoNormalizado);

        List<SpecialtyResponse> responses = lista.stream().map(this::toSpecialtyResponse).toList();
        return PaginatedResponse.of(responses, page, size, total);
    }

    @Transactional
    public SpecialtyResponse deactivateSpecialty(String publicId, String adminPublicId, String ipOrigen) {
        Specialty especialidad = specialtyRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Especialidad: " + publicId));

        specialtyRepository.changeStatus(publicId, STATUS_INACTIVE);

        Long adminId = getAdminUserId(adminPublicId);
        auditService.recordEvent(
                adminId,
                AuditableAction.CAMBIO_ADMINISTRATIVO,
                RESOURCE_SPECIALTY,
                publicId,
                AuditResult.EXITO,
                ipOrigen
        );

        return new SpecialtyResponse(especialidad.publicId(), especialidad.name(), especialidad.slotDurationMinutes(), STATUS_INACTIVE);
    }

    @Transactional
    public SpecialtyResponse activateSpecialty(String publicId, String adminPublicId, String ipOrigen) {
        Specialty especialidad = specialtyRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Especialidad: " + publicId));

        specialtyRepository.changeStatus(publicId, STATUS_ACTIVE);

        Long adminId = getAdminUserId(adminPublicId);
        auditService.recordEvent(
                adminId,
                AuditableAction.CAMBIO_ADMINISTRATIVO,
                RESOURCE_SPECIALTY,
                publicId,
                AuditResult.EXITO,
                ipOrigen
        );

        return new SpecialtyResponse(especialidad.publicId(), especialidad.name(), especialidad.slotDurationMinutes(), STATUS_ACTIVE);
    }

    // =========================================================================
    // MAPPERS Y HELPERS PRIVADOS
    // =========================================================================

    private Long getAdminUserId(String adminPublicId) {
        return userRepository.findByPublicId(adminPublicId)
                .map(User::id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario administrador"));
    }

    private String normalizeAndValidateStatus(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        String normalizado = estado.trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(normalizado)) {
            throw new InvalidDataException("Estado invalido. Los valores permitidos son: ACTIVO, INACTIVO.");
        }
        return normalizado;
    }

    private SpecialtyResponse toSpecialtyResponse(Specialty esp) {
        return new SpecialtyResponse(
                esp.publicId(),
                esp.name(),
                esp.slotDurationMinutes(),
                esp.status()
        );
    }
}
