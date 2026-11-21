package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meditriaje.model.AppointmentReminder;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** El repositorio de recordatorios no contiene SQL: delega en PKG_SCHEDULING a través de PackageGateway. */
class AppointmentReminderRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final AppointmentReminderRepository repository = new AppointmentReminderRepository(gateway);

    @Test
    @DisplayName("declara las rutinas de PKG_SCHEDULING para recordatorios")
    void declaresRoutines() {
        assertThat(AppointmentReminderRepository.CREATE_REMINDER.qualifiedName())
                .isEqualTo("PKG_SCHEDULING.create_reminder");
        assertThat(AppointmentReminderRepository.CREATE_REMINDER.parameters())
                .extracting(ProcedureSpec.Param::name)
                .containsExactly("p_public_id", "p_appointment_id", "p_patient_id", "p_type",
                        "p_channel", "p_recipient", "p_delivery_status", "p_error_message", "p_sent_at", "o_id");
    }

    @Test
    @DisplayName("guardar delega en create_reminder con tipos explícitos y retorna o_id")
    void save_executesInsert() {
        AppointmentReminder recordatorio = new AppointmentReminder(
                null, "rec-uuid-1", 10L, 20L, "CONFIRMACION_RESERVA", "EMAIL",
                "paciente@example.com", "ENVIADO", null, Instant.now());
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(gateway.execute(eq(AppointmentReminderRepository.CREATE_REMINDER), params.capture()))
                .thenReturn(Map.of("o_id", new BigDecimal("50")));

        Long id = repository.save(recordatorio);

        assertThat(id).isEqualTo(50L);
        assertThat(params.getValue().getValue("p_appointment_id")).isEqualTo(10L);
        assertThat(params.getValue().getValue("p_error_message")).isNull();
        assertThat(params.getValue().getSqlType("p_error_message")).isEqualTo(Types.VARCHAR);
    }

    @Test
    @DisplayName("listarPorCitaId delega en list_reminders_by_appointment")
    void listByAppointmentId_executesQuery() {
        Long citaId = 15L;
        AppointmentReminder r1 = new AppointmentReminder(
                1L, "rec-1", citaId, 2L, "CONFIRMACION_RESERVA", "EMAIL", "a@b.com", "ENVIADO", null, Instant.now());
        when(gateway.<AppointmentReminder>list(
                        eq(AppointmentReminderRepository.LIST_REMINDERS_BY_APPOINTMENT),
                        any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of(r1));

        List<AppointmentReminder> resultado = repository.listByAppointmentId(citaId);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).publicId()).isEqualTo("rec-1");
    }

    @Test
    @DisplayName("contarPorCitaIdYTipo delega en count_reminders_by_type")
    void countByAppointmentIdAndType_executesCount() {
        Long citaId = 15L;
        String tipo = "CONFIRMACION_RESERVA";
        when(gateway.<Number>callFunction(
                        eq(AppointmentReminderRepository.COUNT_REMINDERS_BY_TYPE), any(SqlParameterSource.class)))
                .thenReturn(new BigDecimal("1"));

        int count = repository.countByAppointmentIdAndType(citaId, tipo);

        assertThat(count).isEqualTo(1);
    }
}
