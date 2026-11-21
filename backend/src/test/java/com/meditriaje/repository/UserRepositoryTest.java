package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meditriaje.repository.plsql.PackageGateway;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** El repositorio de usuarios no contiene SQL: arma los parámetros de PKG_IDENTITY y delega en la pasarela. */
class UserRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final UserRepository repository = new UserRepository(gateway);

    @Test
    void updatePassword_sendsHashAndFlagAsNumber() {
        repository.updatePassword(10L, "nuevoHash", false);

        verify(gateway).execute(eq(UserRepository.UPDATE_PASSWORD), argThat((SqlParameterSource p) ->
                Long.valueOf(10L).equals(p.getValue("p_user_id"))
                        && "nuevoHash".equals(p.getValue("p_password_hash"))
                        && Integer.valueOf(0).equals(p.getValue("p_must_change_password"))));
    }

    @Test
    void updateStatus_sendsStatus() {
        repository.updateStatus(10L, "INACTIVO");

        verify(gateway).execute(eq(UserRepository.UPDATE_STATUS), argThat((SqlParameterSource p) ->
                Long.valueOf(10L).equals(p.getValue("p_user_id")) && "INACTIVO".equals(p.getValue("p_status"))));
    }

    @Test
    void create_returnsGeneratedId() {
        when(gateway.execute(eq(UserRepository.CREATE_USER), any(SqlParameterSource.class)))
                .thenReturn(Map.of("o_user_id", new BigDecimal("42")));

        assertThat(repository.create("pub", "a@b.co", "hash", true)).isEqualTo(42L);
    }

    @Test
    void findRoleIdByName_emptyWhenFunctionReturnsNull() {
        when(gateway.callFunction(eq(UserRepository.GET_ROLE_ID_BY_NAME), any(SqlParameterSource.class)))
                .thenReturn(null);

        assertThat(repository.findRoleIdByName("ROLE_X")).isEmpty();
    }

    @Test
    void existsByEmail_interpretsCount() {
        when(gateway.callFunction(eq(UserRepository.COUNT_USERS_BY_EMAIL), any(SqlParameterSource.class)))
                .thenReturn(new BigDecimal("1"));

        assertThat(repository.existsByEmail("a@b.co")).isTrue();
    }
}
