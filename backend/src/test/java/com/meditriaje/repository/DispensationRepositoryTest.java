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

import com.meditriaje.dto.pharmacy.DispensationDetailResponse;
import com.meditriaje.dto.pharmacy.DispensationResponse;
import com.meditriaje.dto.pharmacy.PrescriptionDispensationResponse;
import com.meditriaje.dto.pharmacy.MedicationBalanceDto;
import com.meditriaje.model.Dispensation;
import com.meditriaje.model.DispensationDetail;
import com.meditriaje.model.PrescriptionDispensationStatus;
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
class DispensationRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final DispensationRepository repository = new DispensationRepository(gateway);

    @Test
    @DisplayName("crearDispensacion - Delega sede única en PKG_PHARMACY y retorna ID generado")
    void createDispensation_returnsGeneratedId() {
        Dispensation disp = new Dispensation(null, "disp-uuid-1", 10L, 20L, 30L, "Entrega en ventanilla", Instant.now());
        when(gateway.execute(eq(DispensationRepository.CREATE_DISPENSATION),
                        argThat(p -> "disp-uuid-1".equals(p.getValue("p_public_id"))
                                && Long.valueOf(10L).equals(p.getValue("p_prescription_id"))
                                && Long.valueOf(30L).equals(p.getValue("p_user_id"))
                                && "Entrega en ventanilla".equals(p.getValue("p_notes")))))
                .thenReturn(Map.of("o_id", new BigDecimal("777")));

        Long id = repository.createDispensation(disp);

        assertThat(id).isEqualTo(777L);
    }

    @Test
    @DisplayName("crearDispensacion - Falla si el paquete no devuelve o_id")
    void createDispensation_withoutId_fails() {
        Dispensation disp = new Dispensation(null, "disp-uuid-1", 10L, 20L, 30L, null, Instant.now());
        when(gateway.execute(eq(DispensationRepository.CREATE_DISPENSATION), any(SqlParameterSource.class)))
                .thenReturn(new HashMap<>());

        assertThatThrownBy(() -> repository.createDispensation(disp)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("guardarDetalles - Inserta cada detalle con lote y fecha de vencimiento")
    void saveDetails_executesPerItem() {
        List<DispensationDetail> detalles = List.of(
                new DispensationDetail(null, 100L, 501L, 10, "LOTE-A1", LocalDate.of(2027, 12, 31), Instant.now()),
                new DispensationDetail(null, 100L, 502L, 5, null, null, Instant.now()));

        repository.saveDetails(100L, detalles);

        verify(gateway).execute(eq(DispensationRepository.CREATE_DISPENSATION_ITEM),
                argThat(p -> Long.valueOf(100L).equals(p.getValue("p_dispensation_id"))
                        && Long.valueOf(501L).equals(p.getValue("p_prescription_item_id"))
                        && Integer.valueOf(10).equals(p.getValue("p_delivered_quantity"))
                        && "LOTE-A1".equals(p.getValue("p_batch"))
                        && Date.valueOf(LocalDate.of(2027, 12, 31)).equals(p.getValue("p_batch_expiry_date"))));
        verify(gateway).execute(eq(DispensationRepository.CREATE_DISPENSATION_ITEM),
                argThat(p -> Long.valueOf(502L).equals(p.getValue("p_prescription_item_id"))
                        && p.getValue("p_batch") == null
                        && p.getValue("p_batch_expiry_date") == null));
    }

    @Test
    @DisplayName("buscarEntidadPorPublicId - Retorna entidad Dispensacion si existe")
    void findEntityByPublicId_returnsDispensation() {
        Dispensation mockDisp = new Dispensation(1L, "disp-uuid-1", 10L, 20L, 30L, "Obs", Instant.now());
        when(gateway.<Dispensation>single(eq(DispensationRepository.GET_DISPENSATION_BY_PUBLIC_ID),
                        argThat(p -> "disp-uuid-1".equals(p.getValue("p_public_id"))), eq("o_row")))
                .thenReturn(Optional.of(mockDisp));

        Optional<Dispensation> res = repository.findEntityByPublicId(" disp-uuid-1 ");

        assertThat(res).contains(mockDisp);
    }

    @Test
    @DisplayName("buscarDetallesPorDispensacionId - Retorna lista de detalles entregados")
    void findDetailsByDispensationId_returnsList() {
        DispensationDetailResponse det = new DispensationDetailResponse(
                "med-uuid-1", "MED-ACE-500", "Acetaminofen", 10, "LOTE-99", LocalDate.of(2028, 5, 20), Instant.now());
        when(gateway.<DispensationDetailResponse>list(eq(DispensationRepository.LIST_DISPENSATION_ITEMS),
                        argThat(p -> Long.valueOf(55L).equals(p.getValue("p_dispensation_id"))), eq("o_rows")))
                .thenReturn(List.of(det));

        List<DispensationDetailResponse> list = repository.findDetailsByDispensationId(55L);

        assertThat(list).containsExactly(det);
    }

    @Test
    @DisplayName("obtenerTotalesDispensadosPorRecetaId - Construye mapa con acumulados")
    void getDispensedTotalsByPrescriptionId_returnsMap() {
        when(gateway.<DispensationRepository.DispensedTotalRow>list(
                        eq(DispensationRepository.LIST_DISPENSED_TOTALS),
                        argThat(p -> Long.valueOf(10L).equals(p.getValue("p_prescription_id"))), eq("o_rows")))
                .thenReturn(List.of(new DispensationRepository.DispensedTotalRow(501L, 15)));

        Map<Long, Integer> mapa = repository.getDispensedTotalsByPrescriptionId(10L);

        assertThat(mapa).containsEntry(501L, 15);
    }

    @Test
    @DisplayName("BALANCE_MAPPER - Calcula saldos y estados PENDIENTE, PARCIAL y TOTAL")
    void balanceMapper_computesBalancesAndStatuses() throws SQLException {
        MedicationBalanceDto s1 = DispensationRepository.BALANCE_MAPPER.mapRow(mockBalanceRow(20, 0), 0);
        MedicationBalanceDto s2 = DispensationRepository.BALANCE_MAPPER.mapRow(mockBalanceRow(10, 4), 1);
        MedicationBalanceDto s3 = DispensationRepository.BALANCE_MAPPER.mapRow(mockBalanceRow(30, 30), 2);

        assertThat(s1.status()).isEqualTo("PENDIENTE");
        assertThat(s1.pendingBalance()).isEqualTo(20);
        assertThat(s2.status()).isEqualTo("DISPENSADA_PARCIAL");
        assertThat(s2.pendingBalance()).isEqualTo(6);
        assertThat(s3.status()).isEqualTo("DISPENSADA_TOTAL");
        assertThat(s3.pendingBalance()).isZero();
    }

    @Test
    @DisplayName("buscarItemsPrescripcion - Delega en list_prescribed_items")
    void findPrescriptionItems_returnsItems() {
        DispensationRepository.PrescribedItemInfo item =
                new DispensationRepository.PrescribedItemInfo(888L, "med-uuid-abc", "Ibuprofeno", 12);
        when(gateway.<DispensationRepository.PrescribedItemInfo>list(eq(DispensationRepository.LIST_PRESCRIBED_ITEMS),
                        argThat(p -> Long.valueOf(99L).equals(p.getValue("p_prescription_id"))), eq("o_rows")))
                .thenReturn(List.of(item));

        assertThat(repository.findPrescriptionItems(99L)).containsExactly(item);
    }

    @Test
    @DisplayName("buscarRecetaDispensacionPorPublicId - Construye respuesta completa con código y estado")
    void findPrescriptionDispensationByPublicId_returnsFullResponse() {
        DispensationRepository.DispensingHeaderRow row = new DispensationRepository.DispensingHeaderRow(
                10L, "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "atn-1", "pac-1", "CC 10203040",
                "Carlos Gomez", "prof-1", "Dra. Perez", "Medicina General", 30, Instant.now());

        when(gateway.<DispensationRepository.DispensingHeaderRow>single(
                        eq(DispensationRepository.GET_DISPENSING_PRESCRIPTION),
                        argThat(p -> "a1b2c3d4-e5f6-7890-abcd-ef1234567890".equals(p.getValue("p_public_id"))),
                        eq("o_row")))
                .thenReturn(Optional.of(row));
        when(gateway.<MedicationBalanceDto>list(eq(DispensationRepository.LIST_PRESCRIPTION_BALANCES),
                        any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of());
        when(gateway.<DispensationRepository.DispensationHeaderRow>list(
                        eq(DispensationRepository.LIST_DISPENSATIONS_BY_PRESCRIPTION),
                        any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of());

        Optional<PrescriptionDispensationResponse> res =
                repository.findPrescriptionDispensationByPublicId("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

        assertThat(res).isPresent();
        PrescriptionDispensationResponse r = res.get();
        assertThat(r.prescriptionPublicId()).isEqualTo("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        assertThat(r.claimCode()).isEqualTo("REC-A1B2C3D4");
        assertThat(r.expired()).isFalse();
        assertThat(r.dispensationStatus()).isEqualTo(PrescriptionDispensationStatus.PENDIENTE);
        assertThat(r.patientDocument()).isEqualTo("CC 10203040");
    }

    @Test
    @DisplayName("buscarRecetasDispensacion y contarRecetasDispensacion - Normalizan prefijo REC-")
    void searchAndCountPrescriptionDispensations_normalizePrefix() {
        when(gateway.<DispensationRepository.DispensingHeaderRow>list(
                        eq(DispensationRepository.SEARCH_DISPENSING_PRESCRIPTIONS),
                        argThat(p -> "REC-A1B2".equals(p.getValue("p_term"))
                                && "%REC-A1B2%".equals(p.getValue("p_pattern"))
                                && "%A1B2%".equals(p.getValue("p_id_pattern"))),
                        eq("o_rows")))
                .thenReturn(List.of());
        when(gateway.<Object>callFunction(eq(DispensationRepository.COUNT_DISPENSING_PRESCRIPTIONS),
                        argThat(p -> p.getValue("p_term") == null)))
                .thenReturn(new BigDecimal("5"));

        assertThat(repository.searchPrescriptionDispensations("REC-A1B2", 0, 10)).isEmpty();
        assertThat(repository.countPrescriptionDispensations("  ")).isEqualTo(5);
    }

    @Test
    @DisplayName("Entradas nulas no consultan la base")
    void nulls_doNotQuery() {
        assertThat(repository.findEntityByPublicId(" ")).isEmpty();
        assertThat(repository.findByPublicId(null)).isEmpty();
        assertThat(repository.findDetailsByDispensationId(null)).isEmpty();
        assertThat(repository.listByPrescriptionId(null)).isEmpty();
        assertThat(repository.getDispensedTotalsByPrescriptionId(null)).isEmpty();
        assertThat(repository.getBalancesByPrescriptionId(null)).isEmpty();
        assertThat(repository.findPrescriptionItems(null)).isEmpty();
        assertThat(repository.findPrescriptionDispensationByPublicId(null)).isEmpty();
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
