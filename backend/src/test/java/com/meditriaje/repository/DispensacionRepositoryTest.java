package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meditriaje.dto.pharmacy.DispensacionDetalleResponse;
import com.meditriaje.dto.pharmacy.DispensacionResponse;
import com.meditriaje.dto.pharmacy.RecetaDispensacionResponse;
import com.meditriaje.dto.pharmacy.SaldoMedicamentoDto;
import com.meditriaje.model.Dispensacion;
import com.meditriaje.model.DispensacionDetalle;
import com.meditriaje.model.EstadoRecetaDispensacion;
import com.meditriaje.repository.plsql.PackageGateway;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** El repositorio de dispensaciones delega en PKG_PHARMACY a través de la pasarela. */
class DispensacionRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final DispensacionRepository repository = new DispensacionRepository(gateway);

    @Test
    @DisplayName("crearDispensacion - Delega sede única en PKG_PHARMACY y retorna ID generado")
    void crearDispensacion_retornaIdGenerado() {
        Dispensacion disp = new Dispensacion(null, "disp-uuid-1", 10L, 20L, 30L, "Entrega en ventanilla", Instant.now());
        when(gateway.execute(eq(DispensacionRepository.CREATE_DISPENSATION),
                        argThat(p -> "disp-uuid-1".equals(p.getValue("p_public_id"))
                                && Long.valueOf(10L).equals(p.getValue("p_prescription_id"))
                                && Long.valueOf(30L).equals(p.getValue("p_user_id"))
                                && "Entrega en ventanilla".equals(p.getValue("p_notes")))))
                .thenReturn(Map.of("o_id", new BigDecimal("777")));

        Long id = repository.crearDispensacion(disp);

        assertThat(id).isEqualTo(777L);
    }

    @Test
    @DisplayName("crearDispensacion - Falla si el paquete no devuelve o_id")
    void crearDispensacion_sinId_falla() {
        Dispensacion disp = new Dispensacion(null, "disp-uuid-1", 10L, 20L, 30L, null, Instant.now());
        when(gateway.execute(eq(DispensacionRepository.CREATE_DISPENSATION), any(SqlParameterSource.class)))
                .thenReturn(new HashMap<>());

        assertThatThrownBy(() -> repository.crearDispensacion(disp)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("guardarDetalles - Inserta cada detalle con lote y fecha de vencimiento")
    void guardarDetalles_ejecutaPorItem() {
        List<DispensacionDetalle> detalles = List.of(
                new DispensacionDetalle(null, 100L, 501L, 10, "LOTE-A1", LocalDate.of(2027, 12, 31), Instant.now()),
                new DispensacionDetalle(null, 100L, 502L, 5, null, null, Instant.now()));

        repository.guardarDetalles(100L, detalles);

        verify(gateway).execute(eq(DispensacionRepository.CREATE_DISPENSATION_ITEM),
                argThat(p -> Long.valueOf(100L).equals(p.getValue("p_dispensation_id"))
                        && Long.valueOf(501L).equals(p.getValue("p_prescription_item_id"))
                        && Integer.valueOf(10).equals(p.getValue("p_delivered_quantity"))
                        && "LOTE-A1".equals(p.getValue("p_batch"))
                        && Date.valueOf(LocalDate.of(2027, 12, 31)).equals(p.getValue("p_batch_expiry_date"))));
        verify(gateway).execute(eq(DispensacionRepository.CREATE_DISPENSATION_ITEM),
                argThat(p -> Long.valueOf(502L).equals(p.getValue("p_prescription_item_id"))
                        && p.getValue("p_batch") == null
                        && p.getValue("p_batch_expiry_date") == null));
    }

    @Test
    @DisplayName("buscarEntidadPorPublicId - Retorna entidad Dispensacion si existe")
    void buscarEntidadPorPublicId_retornaDispensacion() {
        Dispensacion mockDisp = new Dispensacion(1L, "disp-uuid-1", 10L, 20L, 30L, "Obs", Instant.now());
        when(gateway.<Dispensacion>single(eq(DispensacionRepository.GET_DISPENSATION_BY_PUBLIC_ID),
                        argThat(p -> "disp-uuid-1".equals(p.getValue("p_public_id"))), eq("o_row")))
                .thenReturn(Optional.of(mockDisp));

        Optional<Dispensacion> res = repository.buscarEntidadPorPublicId(" disp-uuid-1 ");

        assertThat(res).contains(mockDisp);
    }

    @Test
    @DisplayName("buscarDetallesPorDispensacionId - Retorna lista de detalles entregados")
    void buscarDetallesPorDispensacionId_retornaLista() {
        DispensacionDetalleResponse det = new DispensacionDetalleResponse(
                "med-uuid-1", "MED-ACE-500", "Acetaminofen", 10, "LOTE-99", LocalDate.of(2028, 5, 20), Instant.now());
        when(gateway.<DispensacionDetalleResponse>list(eq(DispensacionRepository.LIST_DISPENSATION_ITEMS),
                        argThat(p -> Long.valueOf(55L).equals(p.getValue("p_dispensation_id"))), eq("o_rows")))
                .thenReturn(List.of(det));

        List<DispensacionDetalleResponse> list = repository.buscarDetallesPorDispensacionId(55L);

        assertThat(list).containsExactly(det);
    }

    @Test
    @DisplayName("obtenerTotalesDispensadosPorRecetaId - Construye mapa con acumulados")
    void obtenerTotalesDispensadosPorRecetaId_retornaMapa() {
        when(gateway.<DispensacionRepository.DispensedTotalRow>list(
                        eq(DispensacionRepository.LIST_DISPENSED_TOTALS),
                        argThat(p -> Long.valueOf(10L).equals(p.getValue("p_prescription_id"))), eq("o_rows")))
                .thenReturn(List.of(new DispensacionRepository.DispensedTotalRow(501L, 15)));

        Map<Long, Integer> mapa = repository.obtenerTotalesDispensadosPorRecetaId(10L);

        assertThat(mapa).containsEntry(501L, 15);
    }

    @Test
    @DisplayName("BALANCE_MAPPER - Calcula saldos y estados PENDIENTE, PARCIAL y TOTAL")
    void balanceMapper_calculaSaldosYEstados() throws SQLException {
        SaldoMedicamentoDto s1 = DispensacionRepository.BALANCE_MAPPER.mapRow(mockBalanceRow(20, 0), 0);
        SaldoMedicamentoDto s2 = DispensacionRepository.BALANCE_MAPPER.mapRow(mockBalanceRow(10, 4), 1);
        SaldoMedicamentoDto s3 = DispensacionRepository.BALANCE_MAPPER.mapRow(mockBalanceRow(30, 30), 2);

        assertThat(s1.estado()).isEqualTo("PENDIENTE");
        assertThat(s1.saldoPendiente()).isEqualTo(20);
        assertThat(s2.estado()).isEqualTo("DISPENSADA_PARCIAL");
        assertThat(s2.saldoPendiente()).isEqualTo(6);
        assertThat(s3.estado()).isEqualTo("DISPENSADA_TOTAL");
        assertThat(s3.saldoPendiente()).isZero();
    }

    @Test
    @DisplayName("buscarItemsPrescripcion - Delega en list_prescribed_items")
    void buscarItemsPrescripcion_retornaItems() {
        DispensacionRepository.ItemPrescritoInfo item =
                new DispensacionRepository.ItemPrescritoInfo(888L, "med-uuid-abc", "Ibuprofeno", 12);
        when(gateway.<DispensacionRepository.ItemPrescritoInfo>list(eq(DispensacionRepository.LIST_PRESCRIBED_ITEMS),
                        argThat(p -> Long.valueOf(99L).equals(p.getValue("p_prescription_id"))), eq("o_rows")))
                .thenReturn(List.of(item));

        assertThat(repository.buscarItemsPrescripcion(99L)).containsExactly(item);
    }

    @Test
    @DisplayName("buscarRecetaDispensacionPorPublicId - Construye respuesta completa con código y estado")
    void buscarRecetaDispensacionPorPublicId_retornaRespuestaCompleta() {
        DispensacionRepository.DispensingHeaderRow row = new DispensacionRepository.DispensingHeaderRow(
                10L, "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "atn-1", "pac-1", "CC 10203040",
                "Carlos Gomez", "prof-1", "Dra. Perez", "Medicina General", 30, Instant.now());

        when(gateway.<DispensacionRepository.DispensingHeaderRow>single(
                        eq(DispensacionRepository.GET_DISPENSING_PRESCRIPTION),
                        argThat(p -> "a1b2c3d4-e5f6-7890-abcd-ef1234567890".equals(p.getValue("p_public_id"))),
                        eq("o_row")))
                .thenReturn(Optional.of(row));
        when(gateway.<SaldoMedicamentoDto>list(eq(DispensacionRepository.LIST_PRESCRIPTION_BALANCES),
                        any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of());
        when(gateway.<DispensacionRepository.DispensationHeaderRow>list(
                        eq(DispensacionRepository.LIST_DISPENSATIONS_BY_PRESCRIPTION),
                        any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of());

        Optional<RecetaDispensacionResponse> res =
                repository.buscarRecetaDispensacionPorPublicId("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

        assertThat(res).isPresent();
        RecetaDispensacionResponse r = res.get();
        assertThat(r.recetaPublicId()).isEqualTo("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        assertThat(r.codigoReclamacion()).isEqualTo("REC-A1B2C3D4");
        assertThat(r.vencida()).isFalse();
        assertThat(r.estadoDispensacion()).isEqualTo(EstadoRecetaDispensacion.PENDIENTE);
        assertThat(r.pacienteDocumento()).isEqualTo("CC 10203040");
    }

    @Test
    @DisplayName("buscarRecetasDispensacion y contarRecetasDispensacion - Normalizan prefijo REC-")
    void buscarYContarRecetasDispensacion_normalizanPrefijo() {
        when(gateway.<DispensacionRepository.DispensingHeaderRow>list(
                        eq(DispensacionRepository.SEARCH_DISPENSING_PRESCRIPTIONS),
                        argThat(p -> "REC-A1B2".equals(p.getValue("p_term"))
                                && "%REC-A1B2%".equals(p.getValue("p_pattern"))
                                && "%A1B2%".equals(p.getValue("p_id_pattern"))),
                        eq("o_rows")))
                .thenReturn(List.of());
        when(gateway.<Object>callFunction(eq(DispensacionRepository.COUNT_DISPENSING_PRESCRIPTIONS),
                        argThat(p -> p.getValue("p_term") == null)))
                .thenReturn(new BigDecimal("5"));

        assertThat(repository.buscarRecetasDispensacion("REC-A1B2", 0, 10)).isEmpty();
        assertThat(repository.contarRecetasDispensacion("  ")).isEqualTo(5);
    }

    @Test
    @DisplayName("Entradas nulas no consultan la base")
    void nulosNoConsultan() {
        assertThat(repository.buscarEntidadPorPublicId(" ")).isEmpty();
        assertThat(repository.buscarPorPublicId(null)).isEmpty();
        assertThat(repository.buscarDetallesPorDispensacionId(null)).isEmpty();
        assertThat(repository.listarPorRecetaId(null)).isEmpty();
        assertThat(repository.obtenerTotalesDispensadosPorRecetaId(null)).isEmpty();
        assertThat(repository.obtenerSaldosPorRecetaId(null)).isEmpty();
        assertThat(repository.buscarItemsPrescripcion(null)).isEmpty();
        assertThat(repository.buscarRecetaDispensacionPorPublicId(null)).isEmpty();
        verifyNoInteractions(gateway);
    }

    private static ResultSet mockBalanceRow(int prescrita, int dispensada) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("MEDICAMENTO_PUBLIC_ID")).thenReturn("med-1");
        when(rs.getString("MEDICAMENTO_CODIGO")).thenReturn("MED-1");
        when(rs.getString("SNAPSHOT_NOMBRE")).thenReturn("Farmaco");
        when(rs.getString("SNAPSHOT_PRINCIPIO_ACTIVO")).thenReturn("Principio");
        when(rs.getString("SNAPSHOT_PRESENTACION")).thenReturn("Tableta");
        when(rs.getString("SNAPSHOT_CONCENTRACION")).thenReturn("500 mg");
        when(rs.getString("DOSIS")).thenReturn("1 c/8h");
        when(rs.getString("FRECUENCIA")).thenReturn("8h");
        when(rs.getInt("DURACION_DIAS")).thenReturn(5);
        when(rs.getInt("CANTIDAD_PRESCRITA")).thenReturn(prescrita);
        when(rs.getInt("CANTIDAD_DISPENSADA")).thenReturn(dispensada);
        when(rs.getString("INDICACIONES")).thenReturn("Oral");
        return rs;
    }
}
