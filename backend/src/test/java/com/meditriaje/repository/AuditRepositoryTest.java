package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.AuditResult;
import com.meditriaje.repository.plsql.PackageGateway;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** Bitácora de auditoría vía PKG_AUDIT (sin SQL en Java). */
class AuditRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final AuditRepository repository = new AuditRepository(gateway);

    @Test
    void record_sendsEnumNames() {
        AuditableAction accion = AuditableAction.values()[0];
        repository.record(new AuditEvent(5L, accion, "PACIENTE", "pub", AuditResult.EXITO, "127.0.0.1"));

        verify(gateway).execute(eq(AuditRepository.INSERT_AUDIT_EVENT), argThat((SqlParameterSource p) ->
                accion.name().equals(p.getValue("p_action"))
                        && "EXITO".equals(p.getValue("p_result"))
                        && Long.valueOf(5L).equals(p.getValue("p_user_id"))));
    }

    @Test
    void listEvents_normalizesFiltersAndComputesOffset() {
        when(gateway.list(eq(AuditRepository.LIST_AUDIT_EVENTS), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of());

        repository.listEvents(Instant.now(), null, " login ", "  ", 2, 10);

        verify(gateway).list(eq(AuditRepository.LIST_AUDIT_EVENTS), argThat((SqlParameterSource p) ->
                "LOGIN".equals(p.getValue("p_action"))
                        && p.getValue("p_result") == null
                        && p.getValue("p_to") == null
                        && p.getValue("p_from") != null
                        && Integer.valueOf(20).equals(p.getValue("p_offset"))
                        && Integer.valueOf(10).equals(p.getValue("p_size"))), eq("o_rows"));
    }

    @Test
    void countEvents_returnsTotal() {
        when(gateway.callFunction(eq(AuditRepository.COUNT_AUDIT_EVENTS), any(SqlParameterSource.class)))
                .thenReturn(new BigDecimal("42"));

        assertThat(repository.countEvents(null, null, null, null)).isEqualTo(42L);
    }

    @Test
    void repository_isStrictlyInsertOnly() {
        // ADR-011, ADR-012, ADR-019: AuditoriaRepository no debe exponer métodos de modificación ni borrado
        List<String> operacionesProhibidas = List.of("update", "delete", "remove", "modify", "drop", "truncate");

        for (Method method : AuditRepository.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                String methodNameLower = method.getName().toLowerCase();
                for (String prohibida : operacionesProhibidas) {
                    assertThat(methodNameLower)
                            .as("AuditoriaRepository no debe exponer el método '%s'", method.getName())
                            .doesNotContain(prohibida);
                }
            }
        }
    }
}
