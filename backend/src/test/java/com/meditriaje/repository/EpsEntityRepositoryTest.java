package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meditriaje.model.EpsEntity;
import com.meditriaje.repository.plsql.PackageGateway;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;

/** El repositorio de ENTIDAD_EPS delega en PKG_AFFILIATIONS a través de la pasarela. */
class EpsEntityRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final EpsEntityRepository repository = new EpsEntityRepository(gateway);

    @Test
    void nullsAndBlanks_doNotQueryDatabase() {
        assertThat(repository.findById(null)).isEmpty();
        assertThat(repository.findByPublicId(null)).isEmpty();
        assertThat(repository.findByPublicId("   ")).isEmpty();
        assertThat(repository.findByCode(null)).isEmpty();
        assertThat(repository.findByCode(" ")).isEmpty();
        verifyNoInteractions(gateway);
    }

    @Test
    void epsQueriesAndActiveListing_delegateToPkgAffiliations() {
        EpsEntity eps = new EpsEntity(1L, "eps-1", "EPS037", "NUEVA EPS S.A.", "900", "MIXTO", "ACTIVA", Instant.now());
        when(gateway.<EpsEntity>single(eq(EpsEntityRepository.GET_EPS_BY_ID),
                        argThat(p -> Long.valueOf(1L).equals(p.getValue("p_id"))), eq("o_row")))
                .thenReturn(Optional.of(eps));
        when(gateway.<EpsEntity>single(eq(EpsEntityRepository.GET_EPS_BY_PUBLIC_ID),
                        argThat(p -> "eps-1".equals(p.getValue("p_public_id"))), eq("o_row")))
                .thenReturn(Optional.of(eps));
        when(gateway.<EpsEntity>single(eq(EpsEntityRepository.GET_EPS_BY_CODE),
                        argThat(p -> "EPS037".equals(p.getValue("p_code"))), eq("o_row")))
                .thenReturn(Optional.of(eps));
        when(gateway.<EpsEntity>list(eq(EpsEntityRepository.LIST_ACTIVE_EPS),
                        eq(EmptySqlParameterSource.INSTANCE), eq("o_rows")))
                .thenReturn(List.of(eps));

        assertThat(repository.findById(1L)).contains(eps);
        assertThat(repository.findByPublicId("eps-1")).contains(eps);
        assertThat(repository.findByCode("EPS037")).contains(eps);
        assertThat(repository.listActive()).containsExactly(eps);
    }
}
