package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.repository.plsql.PackageGateway;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** Bitácora de auditoría vía PKG_AUDIT (sin SQL en Java). */
class AuditoriaRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final AuditoriaRepository repository = new AuditoriaRepository(gateway);

    @Test
    void registrar_enviaLosNombresDeLosEnumerados() {
        AccionAuditable accion = AccionAuditable.values()[0];
        repository.registrar(new EventoAuditoria(5L, accion, "PACIENTE", "pub", ResultadoAuditoria.EXITO, "127.0.0.1"));

        verify(gateway).execute(eq(AuditoriaRepository.INSERT_AUDIT_EVENT), argThat((SqlParameterSource p) ->
                accion.name().equals(p.getValue("p_action"))
                        && "EXITO".equals(p.getValue("p_result"))
                        && Long.valueOf(5L).equals(p.getValue("p_user_id"))));
    }

    @Test
    void listarEventos_normalizaFiltrosYCalculaElDesplazamiento() {
        when(gateway.list(eq(AuditoriaRepository.LIST_AUDIT_EVENTS), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of());

        repository.listarEventos(Instant.now(), null, " login ", "  ", 2, 10);

        verify(gateway).list(eq(AuditoriaRepository.LIST_AUDIT_EVENTS), argThat((SqlParameterSource p) ->
                "LOGIN".equals(p.getValue("p_action"))
                        && p.getValue("p_result") == null
                        && p.getValue("p_to") == null
                        && p.getValue("p_from") != null
                        && Integer.valueOf(20).equals(p.getValue("p_offset"))
                        && Integer.valueOf(10).equals(p.getValue("p_size"))), eq("o_rows"));
    }

    @Test
    void contarEventos_retornaTotal() {
        when(gateway.callFunction(eq(AuditoriaRepository.COUNT_AUDIT_EVENTS), any(SqlParameterSource.class)))
                .thenReturn(new BigDecimal("42"));

        assertThat(repository.contarEventos(null, null, null, null)).isEqualTo(42L);
    }

    @Test
    void repositorio_esEstrictamenteInsertOnly() {
        // ADR-011, ADR-012, ADR-019: AuditoriaRepository no debe exponer métodos de modificación ni borrado
        List<String> operacionesProhibidas = List.of("update", "delete", "remove", "modify", "drop", "truncate");

        for (Method method : AuditoriaRepository.class.getDeclaredMethods()) {
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
