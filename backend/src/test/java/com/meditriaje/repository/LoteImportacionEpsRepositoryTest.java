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

import com.meditriaje.model.DetalleImportacionEps;
import com.meditriaje.model.LoteImportacionEps;
import com.meditriaje.repository.plsql.PackageGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** El repositorio de LOTE_IMPORTACION_EPS delega en PKG_AFFILIATIONS a través de la pasarela. */
class LoteImportacionEpsRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final LoteImportacionEpsRepository repository = new LoteImportacionEpsRepository(gateway);

    @Test
    void guardarLote_insertaYRecuperaPorId() {
        LoteImportacionEps lote = new LoteImportacionEps(
                null, "lote-1", "test.xlsx", "sha", 1L, 5, 4, 1, null, 9L, null, null);
        LoteImportacionEps guardado = new LoteImportacionEps(
                100L, "lote-1", "test.xlsx", "sha", 1L, 5, 4, 1, "PREVIEW", 9L, Instant.now(), null);

        when(gateway.execute(eq(LoteImportacionEpsRepository.CREATE_IMPORT_BATCH),
                        argThat(p -> "lote-1".equals(p.getValue("p_public_id")) && p.getValue("p_status") == null)))
                .thenReturn(Map.of("o_id", new BigDecimal("100")));
        when(gateway.<LoteImportacionEps>single(eq(LoteImportacionEpsRepository.GET_IMPORT_BATCH_BY_ID),
                        argThat(p -> Long.valueOf(100L).equals(p.getValue("p_id"))), eq("o_row")))
                .thenReturn(Optional.of(guardado));

        assertThat(repository.guardarLote(lote)).isEqualTo(guardado);
    }

    @Test
    void guardarDetallesBatch_listaNulaOVaciaNoEjecutaNada() {
        repository.guardarDetallesBatch(100L, null);
        repository.guardarDetallesBatch(100L, List.of());
        verifyNoInteractions(gateway);
    }

    @Test
    void guardarDetallesBatch_llamaCreateItemPorCadaFila() {
        List<DetalleImportacionEps> filas = List.of(
                new DetalleImportacionEps(null, null, 2, "CC", "101", "Ana", "Diaz", "CONTRIBUTIVO", "COTIZANTE", "VALIDO", null),
                new DetalleImportacionEps(null, null, 3, "XX", "1", null, null, null, null, "ERROR", "Invalido"));

        repository.guardarDetallesBatch(100L, filas);

        verify(gateway, times(2)).execute(eq(LoteImportacionEpsRepository.CREATE_IMPORT_BATCH_ITEM), any(SqlParameterSource.class));
    }

    @Test
    void totalesEstadoYListadosDeleganEnSusRutinas() {
        repository.actualizarTotales(100L, 10, 9, 1);
        repository.cambiarEstadoLote(100L, "PROCESADO", null);
        when(gateway.<DetalleImportacionEps>list(eq(LoteImportacionEpsRepository.LIST_IMPORT_BATCH_ITEMS),
                        argThat(p -> Integer.valueOf(20).equals(p.getValue("p_limit"))), eq("o_rows")))
                .thenReturn(List.of());
        when(gateway.<DetalleImportacionEps>list(eq(LoteImportacionEpsRepository.LIST_VALID_IMPORT_BATCH_ITEMS),
                        argThat(p -> Long.valueOf(100L).equals(p.getValue("p_batch_id"))), eq("o_rows")))
                .thenReturn(List.of());

        verify(gateway).execute(eq(LoteImportacionEpsRepository.UPDATE_IMPORT_BATCH_TOTALS), any(SqlParameterSource.class));
        verify(gateway).execute(eq(LoteImportacionEpsRepository.UPDATE_IMPORT_BATCH_STATUS),
                argThat(p -> p.getValue("p_processed_at") == null));
        assertThat(repository.listarDetalles(100L, 20)).isEmpty();
        assertThat(repository.listarFilasValidas(100L)).isEmpty();
        assertThat(repository.buscarPorPublicId(" ")).isEmpty();
    }
}
