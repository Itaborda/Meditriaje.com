package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meditriaje.dto.clinical.DiagnosticoCie10Response;
import com.meditriaje.model.DiagnosticoCie10;
import com.meditriaje.repository.plsql.PackageGateway;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** El repositorio del catálogo CIE-10 delega en PKG_CATALOGS a través de la pasarela. */
class DiagnosticoCie10RepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final DiagnosticoCie10Repository repository = new DiagnosticoCie10Repository(gateway);

    @Test
    void declaraLasRutinasDePkgCatalogs() {
        assertThat(DiagnosticoCie10Repository.GET_DIAGNOSIS_BY_CODE.qualifiedName())
                .isEqualTo("PKG_CATALOGS.get_diagnosis_by_code");
        assertThat(DiagnosticoCie10Repository.LIST_ACTIVE_DIAGNOSES.qualifiedName())
                .isEqualTo("PKG_CATALOGS.list_active_diagnoses");
    }

    @Test
    void buscarPorCodigo_normalizaElCodigoYRetornaLaFila() {
        DiagnosticoCie10 diag = new DiagnosticoCie10(1L, "J00", "Rinofaringitis aguda", "ACTIVO");
        when(gateway.<DiagnosticoCie10>single(eq(DiagnosticoCie10Repository.GET_DIAGNOSIS_BY_CODE),
                        argThat(p -> "J00".equals(p.getValue("p_code"))), eq("o_row")))
                .thenReturn(Optional.of(diag));

        Optional<DiagnosticoCie10> resultado = repository.buscarPorCodigo(" j00 ");

        assertThat(resultado).contains(diag);
    }

    @Test
    void buscarPorCodigo_vacioNoConsultaLaBase() {
        assertThat(repository.buscarPorCodigo("  ")).isEmpty();
        verifyNoInteractions(gateway);
    }

    @Test
    void listarActivos_conQueryEnviaElPatronEnMayusculas() {
        DiagnosticoCie10Response item = new DiagnosticoCie10Response("J00", "Rinofaringitis aguda");
        when(gateway.<DiagnosticoCie10Response>list(eq(DiagnosticoCie10Repository.LIST_ACTIVE_DIAGNOSES),
                        argThat(p -> "%RINO%".equals(p.getValue("p_pattern"))), eq("o_rows")))
                .thenReturn(List.of(item));

        assertThat(repository.listarActivos("rino")).containsExactly(item);
    }

    @Test
    void listarActivos_sinQueryEnviaPatronNulo() {
        when(gateway.<DiagnosticoCie10Response>list(eq(DiagnosticoCie10Repository.LIST_ACTIVE_DIAGNOSES),
                        any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of());

        assertThat(repository.listarActivos(null)).isEmpty();
    }
}
