package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meditriaje.dto.clinical.Cie10DiagnosisResponse;
import com.meditriaje.model.Cie10Diagnosis;
import com.meditriaje.repository.plsql.PackageGateway;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** El repositorio del catálogo CIE-10 delega en PKG_CATALOGS a través de la pasarela. */
class Cie10DiagnosisRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final Cie10DiagnosisRepository repository = new Cie10DiagnosisRepository(gateway);

    @Test
    void declaresPkgCatalogsRoutines() {
        assertThat(Cie10DiagnosisRepository.GET_DIAGNOSIS_BY_CODE.qualifiedName())
                .isEqualTo("PKG_CATALOGS.get_diagnosis_by_code");
        assertThat(Cie10DiagnosisRepository.LIST_ACTIVE_DIAGNOSES.qualifiedName())
                .isEqualTo("PKG_CATALOGS.list_active_diagnoses");
    }

    @Test
    void findByCode_normalizesCodeAndReturnsRow() {
        Cie10Diagnosis diag = new Cie10Diagnosis(1L, "J00", "Rinofaringitis aguda", "ACTIVO");
        when(gateway.<Cie10Diagnosis>single(eq(Cie10DiagnosisRepository.GET_DIAGNOSIS_BY_CODE),
                        argThat(p -> "J00".equals(p.getValue("p_code"))), eq("o_row")))
                .thenReturn(Optional.of(diag));

        Optional<Cie10Diagnosis> resultado = repository.findByCode(" j00 ");

        assertThat(resultado).contains(diag);
    }

    @Test
    void findByCode_emptyDoesNotQueryDatabase() {
        assertThat(repository.findByCode("  ")).isEmpty();
        verifyNoInteractions(gateway);
    }

    @Test
    void listActive_withQuerySendsUppercasePattern() {
        Cie10DiagnosisResponse item = new Cie10DiagnosisResponse("J00", "Rinofaringitis aguda");
        when(gateway.<Cie10DiagnosisResponse>list(eq(Cie10DiagnosisRepository.LIST_ACTIVE_DIAGNOSES),
                        argThat(p -> "%RINO%".equals(p.getValue("p_pattern"))), eq("o_rows")))
                .thenReturn(List.of(item));

        assertThat(repository.listActive("rino")).containsExactly(item);
    }

    @Test
    void listActive_withoutQuerySendsNullPattern() {
        when(gateway.<Cie10DiagnosisResponse>list(eq(Cie10DiagnosisRepository.LIST_ACTIVE_DIAGNOSES),
                        any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of());

        assertThat(repository.listActive(null)).isEmpty();
    }
}
