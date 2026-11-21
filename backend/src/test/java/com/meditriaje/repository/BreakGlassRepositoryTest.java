package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meditriaje.model.AccesoBreakGlass;
import com.meditriaje.repository.plsql.PackageGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** Accesos Break-Glass vía PKG_AUDIT (sin SQL en Java). */
class BreakGlassRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final BreakGlassRepository repository = new BreakGlassRepository(gateway);

    @Test
    void registrarAcceso_devuelveElIdGenerado() {
        when(gateway.execute(eq(BreakGlassRepository.CREATE_BREAK_GLASS_ACCESS), any(SqlParameterSource.class)))
                .thenReturn(Map.of("o_access_id", new BigDecimal("9")));

        Long id = repository.registrarAcceso(new AccesoBreakGlass(
                null, "pub", 1L, 2L, "Paciente inconsciente en urgencias", Instant.now().plusSeconds(3600), null));

        assertThat(id).isEqualTo(9L);
    }

    @Test
    void existeAccesoActivo_interpretaElConteo() {
        when(gateway.callFunction(eq(BreakGlassRepository.COUNT_ACTIVE_BREAK_GLASS), any(SqlParameterSource.class)))
                .thenReturn(BigDecimal.ONE);

        assertThat(repository.existeAccesoActivo(1L, 2L, Instant.now())).isTrue();
    }

    @Test
    void parametrosNulos_noLlamanALaBase() {
        assertThat(repository.existeAccesoActivo(null, 2L, Instant.now())).isFalse();
        assertThat(repository.buscarEntidadPorPublicId(" ")).isEmpty();
        assertThat(repository.buscarPorPublicId(null, Instant.now())).isEmpty();
        assertThat(repository.listarActivosPorProfesional(1L, null)).isEmpty();
        verifyNoInteractions(gateway);
    }
}
