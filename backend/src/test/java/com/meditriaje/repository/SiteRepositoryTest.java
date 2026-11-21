package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meditriaje.model.Site;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** El repositorio de la sede única no contiene SQL: declara las rutinas de PKG_COMMON y delega en la pasarela. */
class SiteRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final SiteRepository repository = new SiteRepository(gateway);

    @Test
    @DisplayName("declara las rutinas de PKG_COMMON con sus nombres en inglés")
    void declaresPkgCommonRoutines() {
        assertThat(SiteRepository.GET_SINGLE_SITE.qualifiedName()).isEqualTo("PKG_COMMON.get_single_site");
        assertThat(SiteRepository.GET_SINGLE_SITE.parameters())
                .extracting(ProcedureSpec.Param::direction)
                .containsExactly(ProcedureSpec.Direction.CURSOR);
        assertThat(SiteRepository.GET_SINGLE_SITE_ID.isFunction()).isTrue();
        assertThat(SiteRepository.GET_SINGLE_SITE_ID.qualifiedName()).isEqualTo("PKG_COMMON.get_single_site_id");
    }

    @Test
    @DisplayName("getSingleSite devuelve la fila del cursor o_site")
    void getSingleSiteReturnsTheCursorRow() {
        Site sede = new Site(1L, 1L, "sede-pub", "Hospital Central", "Calle 16", "Valledupar", "ACTIVO");
        when(gateway.<Site>single(eq(SiteRepository.GET_SINGLE_SITE), any(SqlParameterSource.class), eq("o_site")))
                .thenReturn(Optional.of(sede));

        assertThat(repository.getSingleSite()).isSameAs(sede);
    }

    @Test
    @DisplayName("getSingleSite falla si el paquete no devolvió filas")
    void getSingleSiteFailsWithoutRows() {
        when(gateway.<Site>single(eq(SiteRepository.GET_SINGLE_SITE), any(SqlParameterSource.class), eq("o_site")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(repository::getSingleSite).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("getSingleSiteId convierte el NUMBER de Oracle a Long")
    void getSingleSiteIdConvertsTheNumber() {
        when(gateway.<Object>callFunction(SiteRepository.GET_SINGLE_SITE_ID)).thenReturn(new BigDecimal("7"));

        assertThat(repository.getSingleSiteId()).isEqualTo(7L);
    }
}
