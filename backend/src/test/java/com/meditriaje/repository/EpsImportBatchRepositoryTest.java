package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meditriaje.model.EpsImportDetail;
import com.meditriaje.model.EpsImportBatch;
import com.meditriaje.repository.plsql.PackageGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** El repositorio de LOTE_IMPORTACION_EPS delega en PKG_AFFILIATIONS a través de la pasarela. */
class EpsImportBatchRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final EpsImportBatchRepository repository = new EpsImportBatchRepository(gateway);

    @Test
    void saveBatch_insertsAndRetrievesById() {
        EpsImportBatch lote = new EpsImportBatch(
                null, "lote-1", "test.xlsx", "sha", 1L, 5, 4, 1, null, 9L, null, null);
        EpsImportBatch guardado = new EpsImportBatch(
                100L, "lote-1", "test.xlsx", "sha", 1L, 5, 4, 1, "PREVIEW", 9L, Instant.now(), null);

        when(gateway.execute(eq(EpsImportBatchRepository.CREATE_IMPORT_BATCH),
                        argThat(p -> "lote-1".equals(p.getValue("p_public_id")) && p.getValue("p_status") == null)))
                .thenReturn(Map.of("o_id", new BigDecimal("100")));
        when(gateway.<EpsImportBatch>single(eq(EpsImportBatchRepository.GET_IMPORT_BATCH_BY_ID),
                        argThat(p -> Long.valueOf(100L).equals(p.getValue("p_id"))), eq("o_row")))
                .thenReturn(Optional.of(guardado));

        assertThat(repository.saveBatch(lote)).isEqualTo(guardado);
    }

    @Test
    void saveBatchDetails_nullOrEmptyListDoesNothing() {
        repository.saveBatchDetails(100L, null);
        repository.saveBatchDetails(100L, List.of());
        verifyNoInteractions(gateway);
    }

    @Test
    void saveBatchDetails_callsCreateItemPerRow() {
        List<EpsImportDetail> filas = List.of(
                new EpsImportDetail(null, null, 2, "CC", "101", "Ana", "Diaz", "CONTRIBUTIVO", "COTIZANTE", "VALIDO", null),
                new EpsImportDetail(null, null, 3, "XX", "1", null, null, null, null, "ERROR", "Invalido"));

        repository.saveBatchDetails(100L, filas);

        verify(gateway, times(2)).execute(eq(EpsImportBatchRepository.CREATE_IMPORT_BATCH_ITEM), any(SqlParameterSource.class));
    }

    @Test
    void totalsStatusAndListings_delegateToTheirRoutines() {
        repository.updateTotals(100L, 10, 9, 1);
        repository.changeBatchStatus(100L, "PROCESADO", null);
        when(gateway.<EpsImportDetail>list(eq(EpsImportBatchRepository.LIST_IMPORT_BATCH_ITEMS),
                        argThat(p -> Integer.valueOf(20).equals(p.getValue("p_limit"))), eq("o_rows")))
                .thenReturn(List.of());
        when(gateway.<EpsImportDetail>list(eq(EpsImportBatchRepository.LIST_VALID_IMPORT_BATCH_ITEMS),
                        argThat(p -> Long.valueOf(100L).equals(p.getValue("p_batch_id"))), eq("o_rows")))
                .thenReturn(List.of());

        verify(gateway).execute(eq(EpsImportBatchRepository.UPDATE_IMPORT_BATCH_TOTALS), any(SqlParameterSource.class));
        verify(gateway).execute(eq(EpsImportBatchRepository.UPDATE_IMPORT_BATCH_STATUS),
                argThat(p -> p.getValue("p_processed_at") == null));
        assertThat(repository.listDetails(100L, 20)).isEmpty();
        assertThat(repository.listValidRows(100L)).isEmpty();
        assertThat(repository.findByPublicId(" ")).isEmpty();
    }
}
