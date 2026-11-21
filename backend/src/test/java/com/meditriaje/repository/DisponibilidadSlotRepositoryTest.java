package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meditriaje.dto.admin.SlotResponse;
import com.meditriaje.dto.availability.DisponibilidadSlotResponse;
import com.meditriaje.model.DisponibilidadSlot;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** El repositorio de slots no contiene SQL: declara PKG_SCHEDULING y delega en PackageGateway. */
class DisponibilidadSlotRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final DisponibilidadSlotRepository repository = new DisponibilidadSlotRepository(gateway);

    @Test
    void declaraLasRutinasDePkgScheduling() {
        assertThat(DisponibilidadSlotRepository.CREATE_SLOT.qualifiedName()).isEqualTo("PKG_SCHEDULING.create_slot");
        assertThat(DisponibilidadSlotRepository.CREATE_SLOT.parameters())
                .extracting(ProcedureSpec.Param::name)
                .containsExactly("p_public_id", "p_professional_id", "p_specialty_id",
                        "p_start_at", "p_end_at", "p_modality", "p_status", "o_id");
        assertThat(DisponibilidadSlotRepository.RESERVE_SLOT.qualifiedName()).isEqualTo("PKG_SCHEDULING.reserve_slot");
        assertThat(DisponibilidadSlotRepository.RELEASE_SLOT.qualifiedName()).isEqualTo("PKG_SCHEDULING.release_slot");
    }

    @Test
    void guardar_insertaYRetornaIdGenerado() {
        Instant inicio = Instant.parse("2026-10-10T08:00:00Z");
        Instant fin = Instant.parse("2026-10-10T08:20:00Z");
        DisponibilidadSlot slot = new DisponibilidadSlot(
                "slot-uuid", 1L, 2L, 3L, inicio, fin, "PRESENCIAL", "LIBRE");
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(gateway.execute(eq(DisponibilidadSlotRepository.CREATE_SLOT), params.capture()))
                .thenReturn(Map.of("o_id", new BigDecimal("99")));

        Long id = repository.guardar(slot);

        assertThat(id).isEqualTo(99L);
        SqlParameterSource sent = params.getValue();
        assertThat(sent.getValue("p_public_id")).isEqualTo("slot-uuid");
        assertThat(sent.getValue("p_professional_id")).isEqualTo(1L);
        assertThat(sent.getValue("p_specialty_id")).isEqualTo(3L);
        assertThat(sent.getValue("p_modality")).isEqualTo("PRESENCIAL");
        assertThat(sent.getValue("p_status")).isEqualTo("LIBRE");
    }

    @Test
    void guardarLote_llamaCreateSlotPorCadaSlot() {
        Instant inicio = Instant.parse("2026-10-10T08:00:00Z");
        Instant fin = Instant.parse("2026-10-10T08:20:00Z");
        DisponibilidadSlot slot1 = new DisponibilidadSlot("slot-1", 1L, 2L, 3L, inicio, fin, "PRESENCIAL", "LIBRE");
        DisponibilidadSlot slot2 = new DisponibilidadSlot(
                "slot-2", 1L, 2L, 3L, fin, fin.plusSeconds(1200), "PRESENCIAL", "LIBRE");
        when(gateway.execute(eq(DisponibilidadSlotRepository.CREATE_SLOT), any(SqlParameterSource.class)))
                .thenReturn(Map.of("o_id", new BigDecimal("1")), Map.of("o_id", new BigDecimal("2")));

        repository.guardarLote(List.of(slot1, slot2));

        verify(gateway, times(2)).execute(eq(DisponibilidadSlotRepository.CREATE_SLOT), any(SqlParameterSource.class));
    }

    @Test
    void buscarPorPublicIdYPorId_retornanResultados() {
        SlotResponse esperado = new SlotResponse(
                "slot-uuid", "prof-uuid", "Dr. Gregory House", "sede-uuid", "Sede Central",
                "esp-uuid", "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "LIBRE");
        DisponibilidadSlot entidad = new DisponibilidadSlot(
                10L, "slot-uuid", 1L, 2L, 3L, Instant.now(), Instant.now().plusSeconds(1200), "PRESENCIAL", "LIBRE");

        when(gateway.<SlotResponse>list(
                        eq(DisponibilidadSlotRepository.GET_SLOT_DETAIL), any(SqlParameterSource.class), eq("o_row")))
                .thenReturn(List.of(esperado));
        when(gateway.<DisponibilidadSlot>list(
                        eq(DisponibilidadSlotRepository.GET_SLOT_BY_ID), any(SqlParameterSource.class), eq("o_row")))
                .thenReturn(List.of(entidad));

        Optional<SlotResponse> detalle = repository.buscarPorPublicId("slot-uuid");
        Optional<DisponibilidadSlot> porId = repository.buscarPorId(10L);

        assertThat(detalle).contains(esperado);
        assertThat(porId).contains(entidad);
    }

    @Test
    void existeSolape_enviaIntervaloEnOrdenNatural() {
        Instant inicio = Instant.parse("2026-10-10T08:00:00Z");
        Instant fin = Instant.parse("2026-10-10T08:20:00Z");
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(gateway.<Number>callFunction(eq(DisponibilidadSlotRepository.COUNT_OVERLAPPING_SLOTS), params.capture()))
                .thenReturn(new BigDecimal("1"));

        boolean existe = repository.existeSolape(1L, inicio, fin);

        assertThat(existe).isTrue();
        assertThat(params.getValue().getValue("p_professional_id")).isEqualTo(1L);
        assertThat(params.getValue().getSqlType("p_start_at")).isEqualTo(Types.TIMESTAMP);
        assertThat(params.getValue().getSqlType("p_end_at")).isEqualTo(Types.TIMESTAMP);
    }

    @Test
    void cambiarEstadoYEliminar_deleganEnElPaquete() {
        when(gateway.execute(eq(DisponibilidadSlotRepository.DELETE_FREE_SLOT), any(SqlParameterSource.class)))
                .thenReturn(Map.of("o_deleted", new BigDecimal("1")));

        repository.cambiarEstado("slot-uuid", "BLOQUEADO");
        int eliminados = repository.eliminar("slot-uuid");

        verify(gateway).execute(eq(DisponibilidadSlotRepository.UPDATE_SLOT_STATUS), any(SqlParameterSource.class));
        assertThat(eliminados).isEqualTo(1);
    }

    @Test
    void listarYConsultarDisponibles_aplicanFiltrosYPaginacion() {
        Instant desde = Instant.now();
        Instant hasta = desde.plusSeconds(3600);
        when(gateway.<SlotResponse>list(
                        eq(DisponibilidadSlotRepository.LIST_SLOTS), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of());
        when(gateway.<Number>callFunction(eq(DisponibilidadSlotRepository.COUNT_SLOTS), any(SqlParameterSource.class)))
                .thenReturn(BigDecimal.ZERO);
        when(gateway.<DisponibilidadSlotResponse>list(
                        eq(DisponibilidadSlotRepository.LIST_AVAILABLE_SLOTS), any(SqlParameterSource.class),
                        eq("o_rows")))
                .thenReturn(List.of());
        when(gateway.<Number>callFunction(
                        eq(DisponibilidadSlotRepository.COUNT_AVAILABLE_SLOTS), any(SqlParameterSource.class)))
                .thenReturn(BigDecimal.ZERO);

        assertThat(repository.listar(0, 10, "prof-1", "sede-1", "esp-1", desde, hasta, "LIBRE")).isEmpty();
        assertThat(repository.contar("prof-1", "sede-1", "esp-1", desde, hasta, "LIBRE")).isZero();
        assertThat(repository.consultarDisponibles("esp-1", "sede-1", "PRESENCIAL", desde, hasta, 0, 10)).isEmpty();
        assertThat(repository.contarDisponibles("esp-1", "sede-1", "PRESENCIAL", desde, hasta)).isZero();
    }

    @Test
    void reservarYLiberarSlot_retornanFilasAfectadas() {
        when(gateway.execute(eq(DisponibilidadSlotRepository.RESERVE_SLOT), any(SqlParameterSource.class)))
                .thenReturn(Map.of("o_updated", new BigDecimal("1")));
        when(gateway.execute(eq(DisponibilidadSlotRepository.RELEASE_SLOT), any(SqlParameterSource.class)))
                .thenReturn(Map.of("o_updated", new BigDecimal("1")));

        assertThat(repository.reservarSlot(55L)).isEqualTo(1);
        assertThat(repository.liberarSlot(55L)).isEqualTo(1);
    }
}
