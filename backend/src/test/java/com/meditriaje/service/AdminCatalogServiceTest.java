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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCatalogServiceTest {

    @Mock
    private EspecialidadRepository especialidadRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AuditoriaService auditoriaService;

    private AdminCatalogService catalogService;

    private static final String ADMIN_PUBLIC_ID = "admin-uuid-1";
    private static final Long ADMIN_ID = 10L;
    private static final String IP_ORIGEN = "192.168.1.50";

    @BeforeEach
    void setUp() {
        catalogService = new AdminCatalogService(
                especialidadRepository,
                usuarioRepository,
                auditoriaService
        );
    }

    private void mockAdminUsuario() {
        Usuario adminUsuario = new Usuario(
                ADMIN_ID,
                ADMIN_PUBLIC_ID,
                "admin@meditriaje.com",
                "hash",
                "ACTIVO",
                0,
                null,
                Instant.now()
        );
        when(usuarioRepository.buscarPorPublicId(ADMIN_PUBLIC_ID)).thenReturn(Optional.of(adminUsuario));
    }

    // =========================================================================
    // ESPECIALIDAD TESTS
    // =========================================================================

    @Test
    void crearEspecialidad_exito_creaYAudita() {
        mockAdminUsuario();
        CrearEspecialidadRequest req = new CrearEspecialidadRequest("Medicina General", 20);
        when(especialidadRepository.existePorNombre("Medicina General")).thenReturn(false);
        when(especialidadRepository.crear(anyString(), eq("Medicina General"), eq(20)))
                .thenReturn(new Especialidad(1L, "esp-uuid-1", "Medicina General", 20, "ACTIVO"));

        EspecialidadResponse resp = catalogService.crearEspecialidad(req, ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp).isNotNull();
        assertThat(resp.publicId()).isEqualTo("esp-uuid-1");
        assertThat(resp.nombre()).isEqualTo("Medicina General");
        assertThat(resp.duracionSlotMin()).isEqualTo(20);
        assertThat(resp.estado()).isEqualTo("ACTIVO");

        verify(auditoriaService).registrarEvento(
                eq(ADMIN_ID),
                eq(AccionAuditable.CAMBIO_ADMINISTRATIVO),
                eq("ESPECIALIDAD"),
                eq("esp-uuid-1"),
                eq(ResultadoAuditoria.EXITO),
                eq(IP_ORIGEN)
        );
    }

    @Test
    void crearEspecialidad_nombreDuplicado_lanzaDatosInvalidos() {
        CrearEspecialidadRequest req = new CrearEspecialidadRequest("Medicina General", 20);
        when(especialidadRepository.existePorNombre("Medicina General")).thenReturn(true);

        assertThatThrownBy(() -> catalogService.crearEspecialidad(req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Ya existe una especialidad");

        verify(especialidadRepository, never()).crear(anyString(), anyString(), anyInt());
        verify(auditoriaService, never()).registrarEvento(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void actualizarEspecialidad_exito_actualizaYAudita() {
        mockAdminUsuario();
        ActualizarEspecialidadRequest req = new ActualizarEspecialidadRequest("Pediatría Integral", 30);
        Especialidad actual = new Especialidad(2L, "esp-uuid-2", "Pediatría", 20, "ACTIVO");
        when(especialidadRepository.buscarPorPublicId("esp-uuid-2")).thenReturn(Optional.of(actual));
        when(especialidadRepository.existePorNombreYNoPublicId("Pediatría Integral", "esp-uuid-2")).thenReturn(false);

        EspecialidadResponse resp = catalogService.actualizarEspecialidad("esp-uuid-2", req, ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.nombre()).isEqualTo("Pediatría Integral");
        assertThat(resp.duracionSlotMin()).isEqualTo(30);

        verify(especialidadRepository).actualizar("esp-uuid-2", "Pediatría Integral", 30);
        verify(auditoriaService).registrarEvento(
                eq(ADMIN_ID),
                eq(AccionAuditable.CAMBIO_ADMINISTRATIVO),
                eq("ESPECIALIDAD"),
                eq("esp-uuid-2"),
                eq(ResultadoAuditoria.EXITO),
                eq(IP_ORIGEN)
        );
    }

    @Test
    void actualizarEspecialidad_noExiste_lanzaRecursoNoEncontrado() {
        ActualizarEspecialidadRequest req = new ActualizarEspecialidadRequest("Pediatría", 30);
        when(especialidadRepository.buscarPorPublicId("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogService.actualizarEspecialidad("no-existe", req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void actualizarEspecialidad_nombreDuplicado_lanzaDatosInvalidos() {
        ActualizarEspecialidadRequest req = new ActualizarEspecialidadRequest("Cardiología", 30);
        Especialidad actual = new Especialidad(2L, "esp-uuid-2", "Pediatría", 20, "ACTIVO");
        when(especialidadRepository.buscarPorPublicId("esp-uuid-2")).thenReturn(Optional.of(actual));
        when(especialidadRepository.existePorNombreYNoPublicId("Cardiología", "esp-uuid-2")).thenReturn(true);

        assertThatThrownBy(() -> catalogService.actualizarEspecialidad("esp-uuid-2", req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Ya existe otra especialidad");
    }

    @Test
    void desactivarEspecialidad_exito_cambiaEstadoAInactivo() {
        mockAdminUsuario();
        Especialidad actual = new Especialidad(1L, "esp-uuid-1", "Medicina", 20, "ACTIVO");
        when(especialidadRepository.buscarPorPublicId("esp-uuid-1")).thenReturn(Optional.of(actual));

        EspecialidadResponse resp = catalogService.desactivarEspecialidad("esp-uuid-1", ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.estado()).isEqualTo("INACTIVO");
        verify(especialidadRepository).cambiarEstado("esp-uuid-1", "INACTIVO");
        verify(auditoriaService).registrarEvento(eq(ADMIN_ID), eq(AccionAuditable.CAMBIO_ADMINISTRATIVO), eq("ESPECIALIDAD"), eq("esp-uuid-1"), eq(ResultadoAuditoria.EXITO), eq(IP_ORIGEN));
    }

    @Test
    void activarEspecialidad_exito_cambiaEstadoAActivo() {
        mockAdminUsuario();
        Especialidad actual = new Especialidad(1L, "esp-uuid-1", "Medicina", 20, "INACTIVO");
        when(especialidadRepository.buscarPorPublicId("esp-uuid-1")).thenReturn(Optional.of(actual));

        EspecialidadResponse resp = catalogService.activarEspecialidad("esp-uuid-1", ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.estado()).isEqualTo("ACTIVO");
        verify(especialidadRepository).cambiarEstado("esp-uuid-1", "ACTIVO");
    }

    @Test
    void listarEspecialidades_conFiltroValido_retornaPaginado() {
        Especialidad esp = new Especialidad(1L, "esp-1", "Cardiología", 25, "ACTIVO");
        when(especialidadRepository.listar(0, 10, "ACTIVO")).thenReturn(List.of(esp));
        when(especialidadRepository.contar("ACTIVO")).thenReturn(1L);

        PaginatedResponse<EspecialidadResponse> pag = catalogService.listarEspecialidades(0, 10, "ACTIVO");

        assertThat(pag.content()).hasSize(1);
        assertThat(pag.totalElements()).isEqualTo(1L);
        assertThat(pag.totalPages()).isEqualTo(1);
        assertThat(pag.hasNext()).isFalse();
        assertThat(pag.hasPrevious()).isFalse();
    }

    @Test
    void listarEspecialidades_estadoInvalido_lanzaDatosInvalidos() {
        assertThatThrownBy(() -> catalogService.listarEspecialidades(0, 10, "PENDIENTE"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Estado invalido");
    }
}
