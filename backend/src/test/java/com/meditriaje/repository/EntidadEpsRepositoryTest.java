package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meditriaje.model.EntidadEps;
import com.meditriaje.repository.plsql.PackageGateway;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;

/** El repositorio de ENTIDAD_EPS delega en PKG_AFFILIATIONS a través de la pasarela. */
class EntidadEpsRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final EntidadEpsRepository repository = new EntidadEpsRepository(gateway);

    @Test
    void nulosYBlancosNoConsultanLaBase() {
        assertThat(repository.buscarPorId(null)).isEmpty();
        assertThat(repository.buscarPorPublicId(null)).isEmpty();
        assertThat(repository.buscarPorPublicId("   ")).isEmpty();
        assertThat(repository.buscarPorCodigo(null)).isEmpty();
        assertThat(repository.buscarPorCodigo(" ")).isEmpty();
        verifyNoInteractions(gateway);
    }

    @Test
    void consultasDeEpsYListadoDeActivasDeleganEnPkgAffiliations() {
        EntidadEps eps = new EntidadEps(1L, "eps-1", "EPS037", "NUEVA EPS S.A.", "900", "MIXTO", "ACTIVA", Instant.now());
        when(gateway.<EntidadEps>single(eq(EntidadEpsRepository.GET_EPS_BY_ID),
                        argThat(p -> Long.valueOf(1L).equals(p.getValue("p_id"))), eq("o_row")))
                .thenReturn(Optional.of(eps));
        when(gateway.<EntidadEps>single(eq(EntidadEpsRepository.GET_EPS_BY_PUBLIC_ID),
                        argThat(p -> "eps-1".equals(p.getValue("p_public_id"))), eq("o_row")))
                .thenReturn(Optional.of(eps));
        when(gateway.<EntidadEps>single(eq(EntidadEpsRepository.GET_EPS_BY_CODE),
                        argThat(p -> "EPS037".equals(p.getValue("p_code"))), eq("o_row")))
                .thenReturn(Optional.of(eps));
        when(gateway.<EntidadEps>list(eq(EntidadEpsRepository.LIST_ACTIVE_EPS),
                        eq(EmptySqlParameterSource.INSTANCE), eq("o_rows")))
                .thenReturn(List.of(eps));

        assertThat(repository.buscarPorId(1L)).contains(eps);
        assertThat(repository.buscarPorPublicId("eps-1")).contains(eps);
        assertThat(repository.buscarPorCodigo("EPS037")).contains(eps);
        assertThat(repository.listarActivas()).containsExactly(eps);
    }
}
