package com.meditriaje.service;

import com.meditriaje.dto.admin.ActualizarEspecialidadRequest;
import com.meditriaje.dto.admin.CrearEspecialidadRequest;
import com.meditriaje.dto.admin.EspecialidadResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.Especialidad;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.EspecialidadRepository;
import com.meditriaje.repository.UsuarioRepository;
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

    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_INACTIVO = "INACTIVO";
    private static final Set<String> ESTADOS_PERMITIDOS = Set.of(ESTADO_ACTIVO, ESTADO_INACTIVO);

    private static final String RECURSO_ESPECIALIDAD = "ESPECIALIDAD";

    private final EspecialidadRepository especialidadRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    public AdminCatalogService(
            EspecialidadRepository especialidadRepository,
            UsuarioRepository usuarioRepository,
            AuditoriaService auditoriaService
    ) {
        this.especialidadRepository = Objects.requireNonNull(especialidadRepository, "EspecialidadRepository no puede ser nulo");
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "UsuarioRepository no puede ser nulo");
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "AuditoriaService no puede ser nulo");
    }

    // =========================================================================
    // 1. ESPECIALIDADES
    // =========================================================================

    @Transactional
    public EspecialidadResponse crearEspecialidad(CrearEspecialidadRequest req, String adminPublicId, String ipOrigen) {
        String nombreLimpio = req.nombre().trim();
        if (especialidadRepository.existePorNombre(nombreLimpio)) {
            throw new DatosInvalidosException("Ya existe una especialidad con el nombre: " + nombreLimpio);
        }

        String publicId = UUID.randomUUID().toString();
        Especialidad especialidad = especialidadRepository.crear(publicId, nombreLimpio, req.duracionSlotMin());

        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_ESPECIALIDAD,
                especialidad.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return toEspecialidadResponse(especialidad);
    }

    @Transactional
    public EspecialidadResponse actualizarEspecialidad(String publicId, ActualizarEspecialidadRequest req, String adminPublicId, String ipOrigen) {
        Especialidad actual = especialidadRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Especialidad: " + publicId));

        String nombreLimpio = req.nombre().trim();
        if (especialidadRepository.existePorNombreYNoPublicId(nombreLimpio, publicId)) {
            throw new DatosInvalidosException("Ya existe otra especialidad con el nombre: " + nombreLimpio);
        }

        especialidadRepository.actualizar(publicId, nombreLimpio, req.duracionSlotMin());

        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_ESPECIALIDAD,
                publicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return new EspecialidadResponse(publicId, nombreLimpio, req.duracionSlotMin(), actual.estado());
    }

    @Transactional(readOnly = true)
    public EspecialidadResponse obtenerEspecialidad(String publicId) {
        Especialidad especialidad = especialidadRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Especialidad: " + publicId));
        return toEspecialidadResponse(especialidad);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<EspecialidadResponse> listarEspecialidades(int page, int size, String estadoFiltro) {
        String estadoNormalizado = normalizarYValidarEstado(estadoFiltro);
        List<Especialidad> lista = especialidadRepository.listar(page, size, estadoNormalizado);
        long total = especialidadRepository.contar(estadoNormalizado);

        List<EspecialidadResponse> responses = lista.stream().map(this::toEspecialidadResponse).toList();
        return PaginatedResponse.of(responses, page, size, total);
    }

    @Transactional
    public EspecialidadResponse desactivarEspecialidad(String publicId, String adminPublicId, String ipOrigen) {
        Especialidad especialidad = especialidadRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Especialidad: " + publicId));

        especialidadRepository.cambiarEstado(publicId, ESTADO_INACTIVO);

        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_ESPECIALIDAD,
                publicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return new EspecialidadResponse(especialidad.publicId(), especialidad.nombre(), especialidad.duracionSlotMin(), ESTADO_INACTIVO);
    }

    @Transactional
    public EspecialidadResponse activarEspecialidad(String publicId, String adminPublicId, String ipOrigen) {
        Especialidad especialidad = especialidadRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Especialidad: " + publicId));

        especialidadRepository.cambiarEstado(publicId, ESTADO_ACTIVO);

        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_ESPECIALIDAD,
                publicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return new EspecialidadResponse(especialidad.publicId(), especialidad.nombre(), especialidad.duracionSlotMin(), ESTADO_ACTIVO);
    }

    // =========================================================================
    // MAPPERS Y HELPERS PRIVADOS
    // =========================================================================

    private Long obtenerAdminUsuarioId(String adminPublicId) {
        return usuarioRepository.buscarPorPublicId(adminPublicId)
                .map(Usuario::id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario administrador"));
    }

    private String normalizarYValidarEstado(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        String normalizado = estado.trim().toUpperCase();
        if (!ESTADOS_PERMITIDOS.contains(normalizado)) {
            throw new DatosInvalidosException("Estado invalido. Los valores permitidos son: ACTIVO, INACTIVO.");
        }
        return normalizado;
    }

    private EspecialidadResponse toEspecialidadResponse(Especialidad esp) {
        return new EspecialidadResponse(
                esp.publicId(),
                esp.nombre(),
                esp.duracionSlotMin(),
                esp.estado()
        );
    }
}
