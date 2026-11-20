package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meditriaje.repository.plsql.PackageGateway;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** Códigos de respaldo MFA vía PKG_IDENTITY (sin SQL en Java). */
class MfaBackupCodeRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final MfaBackupCodeRepository repository = new MfaBackupCodeRepository(gateway);

    @Test
    void guardarLote_conListaVaciaONula_noLlamaALaBase() {
        repository.guardarLote(1L, List.of());
        repository.guardarLote(1L, null);

        verifyNoInteractions(gateway);
    }

    @Test
    void guardarLote_registraCadaHash() {
        repository.guardarLote(1L, List.of("h1", "h2", "h3"));

        verify(gateway, times(3)).execute(eq(MfaBackupCodeRepository.ADD_MFA_BACKUP_CODE), any(SqlParameterSource.class));
    }

    @Test
    void consumirCodigo_segunFilasActualizadas() {
        when(gateway.execute(eq(MfaBackupCodeRepository.CONSUME_MFA_BACKUP_CODE), any(SqlParameterSource.class)))
                .thenReturn(Map.of("o_updated", BigDecimal.ONE))
                .thenReturn(Map.of("o_updated", BigDecimal.ZERO));

        assertThat(repository.consumirCodigo(1L, "h")).isTrue();
        assertThat(repository.consumirCodigo(1L, "h")).isFalse();
    }

    @Test
    void contarDisponibles_nullEsCero() {
        when(gateway.callFunction(eq(MfaBackupCodeRepository.COUNT_AVAILABLE_BACKUP_CODES), any(SqlParameterSource.class)))
                .thenReturn(new BigDecimal("8"))
                .thenReturn(null);

        assertThat(repository.contarDisponibles(1L)).isEqualTo(8);
        assertThat(repository.contarDisponibles(1L)).isZero();
    }
}
