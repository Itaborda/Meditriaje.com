package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meditriaje.dto.admin.SlotResponse;
import com.meditriaje.dto.availability.AvailabilitySlotResponse;
import com.meditriaje.model.AvailabilitySlot;
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
class AvailabilitySlotRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final AvailabilitySlotRepository repository = new AvailabilitySlotRepository(gateway);

    @Test
    void declaresPkgSchedulingRoutines() {
        assertThat(AvailabilitySlotRepository.CREATE_SLOT.qualifiedName()).isEqualTo("PKG_SCHEDULING.create_slot");
        assertThat(AvailabilitySlotRepository.CREATE_SLOT.parameters())
                .extracting(ProcedureSpec.Param::name)
                .containsExactly("p_public_id", "p_professional_id", "p_specialty_id",
                        "p_start_at", "p_end_at", "p_modality", "p_status", "o_id");
        assertThat(AvailabilitySlotRepository.RESERVE_SLOT.qualifiedName()).isEqualTo("PKG_SCHEDULING.reserve_slot");
        assertThat(AvailabilitySlotRepository.RELEASE_SLOT.qualifiedName()).isEqualTo("PKG_SCHEDULING.release_slot");
    }

    @Test
    void save_insertsAndReturnsGeneratedId() {
        Instant inicio = Instant.parse("2026-10-10T08:00:00Z");
        Instant fin = Instant.parse("2026-10-10T08:20:00Z");
        AvailabilitySlot slot = new AvailabilitySlot(
                "slot-uuid", 1L, 2L, 3L, inicio, fin, "PRESENCIAL", "LIBRE");
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(gateway.execute(eq(AvailabilitySlotRepository.CREATE_SLOT), params.capture()))
                .thenReturn(Map.of("o_id", new BigDecimal("99")));

        Long id = repository.save(slot);

        assertThat(id).isEqualTo(99L);
        SqlParameterSource sent = params.getValue();
        assertThat(sent.getValue("p_public_id")).isEqualTo("slot-uuid");
        assertThat(sent.getValue("p_professional_id")).isEqualTo(1L);
        assertThat(sent.getValue("p_specialty_id")).isEqualTo(3L);
        assertThat(sent.getValue("p_modality")).isEqualTo("PRESENCIAL");
        assertThat(sent.getValue("p_status")).isEqualTo("LIBRE");
    }

    @Test
    void saveBatch_callsCreateSlotPerSlot() {
        Instant inicio = Instant.parse("2026-10-10T08:00:00Z");
        Instant fin = Instant.parse("2026-10-10T08:20:00Z");
        AvailabilitySlot slot1 = new AvailabilitySlot("slot-1", 1L, 2L, 3L, inicio, fin, "PRESENCIAL", "LIBRE");
        AvailabilitySlot slot2 = new AvailabilitySlot(
                "slot-2", 1L, 2L, 3L, fin, fin.plusSeconds(1200), "PRESENCIAL", "LIBRE");
        when(gateway.execute(eq(AvailabilitySlotRepository.CREATE_SLOT), any(SqlParameterSource.class)))
                .thenReturn(Map.of("o_id", new BigDecimal("1")), Map.of("o_id", new BigDecimal("2")));

        repository.saveBatch(List.of(slot1, slot2));

        verify(gateway, times(2)).execute(eq(AvailabilitySlotRepository.CREATE_SLOT), any(SqlParameterSource.class));
    }

    @Test
    void findByPublicIdAndById_returnResults() {
        SlotResponse esperado = new SlotResponse(
                "slot-uuid", "prof-uuid", "Dr. Gregory House", "sede-uuid", "Sede Central",
                "esp-uuid", "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "LIBRE");
        AvailabilitySlot entidad = new AvailabilitySlot(
                10L, "slot-uuid", 1L, 2L, 3L, Instant.now(), Instant.now().plusSeconds(1200), "PRESENCIAL", "LIBRE");

        when(gateway.<SlotResponse>list(
                        eq(AvailabilitySlotRepository.GET_SLOT_DETAIL), any(SqlParameterSource.class), eq("o_row")))
                .thenReturn(List.of(esperado));
        when(gateway.<AvailabilitySlot>list(
                        eq(AvailabilitySlotRepository.GET_SLOT_BY_ID), any(SqlParameterSource.class), eq("o_row")))
                .thenReturn(List.of(entidad));

        Optional<SlotResponse> detalle = repository.findByPublicId("slot-uuid");
        Optional<AvailabilitySlot> porId = repository.findById(10L);

        assertThat(detalle).contains(esperado);
        assertThat(porId).contains(entidad);
    }

    @Test
    void existsOverlap_sendsIntervalInNaturalOrder() {
        Instant inicio = Instant.parse("2026-10-10T08:00:00Z");
        Instant fin = Instant.parse("2026-10-10T08:20:00Z");
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(gateway.<Number>callFunction(eq(AvailabilitySlotRepository.COUNT_OVERLAPPING_SLOTS), params.capture()))
                .thenReturn(new BigDecimal("1"));

        boolean existe = repository.existsOverlap(1L, inicio, fin);

        assertThat(existe).isTrue();
        assertThat(params.getValue().getValue("p_professional_id")).isEqualTo(1L);
        assertThat(params.getValue().getSqlType("p_start_at")).isEqualTo(Types.TIMESTAMP);
        assertThat(params.getValue().getSqlType("p_end_at")).isEqualTo(Types.TIMESTAMP);
    }

    @Test
    void changeStatusAndDelete_delegateToPackage() {
        when(gateway.execute(eq(AvailabilitySlotRepository.DELETE_FREE_SLOT), any(SqlParameterSource.class)))
                .thenReturn(Map.of("o_deleted", new BigDecimal("1")));

        repository.changeStatus("slot-uuid", "BLOQUEADO");
        int eliminados = repository.delete("slot-uuid");

        verify(gateway).execute(eq(AvailabilitySlotRepository.UPDATE_SLOT_STATUS), any(SqlParameterSource.class));
        assertThat(eliminados).isEqualTo(1);
    }

    @Test
    void listAndFindAvailable_applyFiltersAndPagination() {
        Instant desde = Instant.now();
        Instant hasta = desde.plusSeconds(3600);
        when(gateway.<SlotResponse>list(
                        eq(AvailabilitySlotRepository.LIST_SLOTS), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of());
        when(gateway.<Number>callFunction(eq(AvailabilitySlotRepository.COUNT_SLOTS), any(SqlParameterSource.class)))
                .thenReturn(BigDecimal.ZERO);
        when(gateway.<AvailabilitySlotResponse>list(
                        eq(AvailabilitySlotRepository.LIST_AVAILABLE_SLOTS), any(SqlParameterSource.class),
                        eq("o_rows")))
                .thenReturn(List.of());
        when(gateway.<Number>callFunction(
                        eq(AvailabilitySlotRepository.COUNT_AVAILABLE_SLOTS), any(SqlParameterSource.class)))
                .thenReturn(BigDecimal.ZERO);

        assertThat(repository.list(0, 10, "prof-1", "sede-1", "esp-1", desde, hasta, "LIBRE")).isEmpty();
        assertThat(repository.count("prof-1", "sede-1", "esp-1", desde, hasta, "LIBRE")).isZero();
        assertThat(repository.findAvailable("esp-1", "sede-1", "PRESENCIAL", desde, hasta, 0, 10)).isEmpty();
        assertThat(repository.countAvailable("esp-1", "sede-1", "PRESENCIAL", desde, hasta)).isZero();
    }

    @Test
    void reserveAndReleaseSlot_returnAffectedRows() {
        when(gateway.execute(eq(AvailabilitySlotRepository.RESERVE_SLOT), any(SqlParameterSource.class)))
                .thenReturn(Map.of("o_updated", new BigDecimal("1")));
        when(gateway.execute(eq(AvailabilitySlotRepository.RELEASE_SLOT), any(SqlParameterSource.class)))
                .thenReturn(Map.of("o_updated", new BigDecimal("1")));

        assertThat(repository.reserveSlot(55L)).isEqualTo(1);
        assertThat(repository.releaseSlot(55L)).isEqualTo(1);
    }
}
