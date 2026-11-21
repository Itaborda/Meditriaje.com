package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meditriaje.dto.followup.FollowUpResponse;
import com.meditriaje.model.PostAttentionFollowUp;
import com.meditriaje.repository.plsql.PackageGateway;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** Pruebas unitarias de {@link SeguimientoRepository}: delega en PKG_CLINICAL a través de la pasarela. */
class FollowUpRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final FollowUpRepository repository = new FollowUpRepository(gateway);

    @Test
    @DisplayName("guardar envía la fecha de control como Date y devuelve el ID generado")
    void save_executesInsert() {
        LocalDate control = LocalDate.now().plusDays(7);
        PostAttentionFollowUp seguimiento = new PostAttentionFollowUp(null, "seg-uuid-1", 10L, 20L, 30L,
                "CONTROL_MEDICO", "Control de presión arterial en 7 días", control, null, null, null,
                Instant.now(), Instant.now());
        when(gateway.execute(eq(FollowUpRepository.CREATE_FOLLOW_UP),
                        argThat(p -> Date.valueOf(control).equals(p.getValue("p_suggested_control_date"))
                                && "PENDIENTE".equals(p.getValue("p_status")))))
                .thenReturn(Map.of("o_id", BigDecimal.ONE));

        assertThat(repository.save(seguimiento)).isEqualTo(1L);
    }

    @Test
    @DisplayName("buscarPorPublicId retorna la primera fila de la vista")
    void findByPublicId_returnsResponse() {
        FollowUpResponse resp = new FollowUpResponse("seg-uuid-1", "at-1", "pac-1", "Carlos Gomez",
                "prof-1", "Dra. Laura Perez", "Medicina General", "CONTROL_MEDICO", "Control de presión",
                LocalDate.now().plusDays(7), "PENDIENTE", null, null, Instant.now(), Instant.now());
        when(gateway.<FollowUpResponse>list(eq(FollowUpRepository.GET_VIEW_BY_PUBLIC_ID),
                        any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of(resp));

        Optional<FollowUpResponse> opt = repository.findByPublicId("seg-uuid-1");

        assertThat(opt).contains(resp);
    }

    @Test
    @DisplayName("registrarReportePaciente y actualizarEstado llaman a sus rutinas")
    void updates() {
        repository.recordPatientReport(10L, "Evolución favorable sin dolor", Instant.now());
        repository.updateStatus(10L, "CANCELADO");

        verify(gateway).execute(eq(FollowUpRepository.REGISTER_REPORT),
                argThat(p -> "Evolución favorable sin dolor".equals(p.getValue("p_report"))));
        verify(gateway).execute(eq(FollowUpRepository.UPDATE_STATUS),
                argThat(p -> "CANCELADO".equals(p.getValue("p_status"))));
    }

    @Test
    @DisplayName("listarPorPacienteId normaliza el filtro y calcula el desplazamiento")
    void listByPatientId_pagination() {
        when(gateway.<FollowUpResponse>list(eq(FollowUpRepository.LIST_BY_PATIENT),
                        argThat(p -> "PENDIENTE".equals(p.getValue("p_status"))
                                && Integer.valueOf(20).equals(p.getValue("p_offset"))
                                && Integer.valueOf(10).equals(p.getValue("p_size"))), eq("o_rows")))
                .thenReturn(List.of());

        assertThat(repository.listByPatientId(20L, " pendiente ", 2, 10)).isEmpty();
    }

    @Test
    @DisplayName("contarPorPacienteId sin filtro envía estado nulo")
    void countByPatientId_returnsTotal() {
        when(gateway.<Object>callFunction(eq(FollowUpRepository.COUNT_BY_PATIENT),
                        argThat(p -> p.getValue("p_status") == null)))
                .thenReturn(new BigDecimal("3"));

        assertThat(repository.countByPatientId(20L, null)).isEqualTo(3);
    }
}
