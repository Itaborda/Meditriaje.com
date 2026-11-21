package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meditriaje.model.BreakGlassAccess;
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
    void recordAccess_returnsGeneratedId() {
        when(gateway.execute(eq(BreakGlassRepository.CREATE_BREAK_GLASS_ACCESS), any(SqlParameterSource.class)))
                .thenReturn(Map.of("o_access_id", new BigDecimal("9")));

        Long id = repository.recordAccess(new BreakGlassAccess(
                null, "pub", 1L, 2L, "Paciente inconsciente en urgencias", Instant.now().plusSeconds(3600), null));

        assertThat(id).isEqualTo(9L);
    }

    @Test
    void existsActiveAccess_interpretsCount() {
        when(gateway.callFunction(eq(BreakGlassRepository.COUNT_ACTIVE_BREAK_GLASS), any(SqlParameterSource.class)))
                .thenReturn(BigDecimal.ONE);

        assertThat(repository.existsActiveAccess(1L, 2L, Instant.now())).isTrue();
    }

    @Test
    void nullParameters_doNotCallDatabase() {
        assertThat(repository.existsActiveAccess(null, 2L, Instant.now())).isFalse();
        assertThat(repository.findEntityByPublicId(" ")).isEmpty();
        assertThat(repository.findByPublicId(null, Instant.now())).isEmpty();
        assertThat(repository.listActiveByProfessional(1L, null)).isEmpty();
        verifyNoInteractions(gateway);
    }
}
