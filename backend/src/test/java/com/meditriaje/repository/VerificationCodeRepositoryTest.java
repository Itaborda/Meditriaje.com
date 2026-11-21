package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meditriaje.model.VerificationCode;
import com.meditriaje.repository.plsql.PackageGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** Códigos de verificación vía PKG_IDENTITY (sin SQL en Java). */
class VerificationCodeRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final VerificationCodeRepository repository = new VerificationCodeRepository(gateway);

    @Test
    void create_sendsUsedFlagAsNumberAndReturnsId() {
        when(gateway.execute(eq(VerificationCodeRepository.CREATE_VERIFICATION_CODE), any(SqlParameterSource.class)))
                .thenReturn(Map.of("o_code_id", new BigDecimal("5")));
        VerificationCode codigo = new VerificationCode(
                null, "pub", 7L, "RECUPERACION_PASSWORD", "hash", Instant.now(), 0, 3, false, null);

        assertThat(repository.create(codigo)).isEqualTo(5L);
        verify(gateway).execute(eq(VerificationCodeRepository.CREATE_VERIFICATION_CODE),
                argThat((SqlParameterSource p) -> Integer.valueOf(0).equals(p.getValue("p_used"))));
    }

    @Test
    void findById_emptyWhenNoRows() {
        when(gateway.list(eq(VerificationCodeRepository.GET_VERIFICATION_CODE_BY_ID), any(SqlParameterSource.class),
                eq("o_code"))).thenReturn(List.of());

        assertThat(repository.findById(1L)).isEmpty();
    }

    @Test
    void invalidatePreviousCodes_sendsUserAndType() {
        repository.invalidatePreviousCodes(3L, "RECUPERACION_PASSWORD");

        verify(gateway).execute(eq(VerificationCodeRepository.INVALIDATE_PENDING_CODES),
                argThat((SqlParameterSource p) -> Long.valueOf(3L).equals(p.getValue("p_user_id"))
                        && "RECUPERACION_PASSWORD".equals(p.getValue("p_type"))));
    }
}
