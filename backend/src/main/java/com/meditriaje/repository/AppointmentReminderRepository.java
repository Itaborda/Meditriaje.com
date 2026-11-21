package com.meditriaje.repository;

import com.meditriaje.model.AppointmentReminder;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Repositorio para {@code RECORDATORIO_CITA} a través de {@code PKG_SCHEDULING}.
 * No contiene SQL.
 */
@Repository
public class AppointmentReminderRepository {

    static final String PACKAGE = "PKG_SCHEDULING";

    static final RowMapper<AppointmentReminder> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp enviadoTs = rs.getTimestamp("ENVIADO_AT");
        Instant enviadoAt = enviadoTs != null ? enviadoTs.toInstant() : null;

        return new AppointmentReminder(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("CITA_ID"),
                rs.getLong("PACIENTE_ID"),
                rs.getString("TIPO"),
                rs.getString("CANAL"),
                rs.getString("DESTINATARIO"),
                rs.getString("ESTADO_ENVIO"),
                rs.getString("ERROR_MENSAJE"),
                enviadoAt);
    };

    static final ProcedureSpec CREATE_REMINDER = ProcedureSpec.procedure(PACKAGE, "create_reminder")
            .in("p_public_id", Types.VARCHAR)
            .in("p_appointment_id", Types.NUMERIC)
            .in("p_patient_id", Types.NUMERIC)
            .in("p_type", Types.VARCHAR)
            .in("p_channel", Types.VARCHAR)
            .in("p_recipient", Types.VARCHAR)
            .in("p_delivery_status", Types.VARCHAR)
            .in("p_error_message", Types.VARCHAR)
            .in("p_sent_at", Types.TIMESTAMP)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec LIST_REMINDERS_BY_APPOINTMENT =
            ProcedureSpec.procedure(PACKAGE, "list_reminders_by_appointment")
                    .in("p_appointment_id", Types.NUMERIC)
                    .cursor("o_rows", ROW_MAPPER)
                    .build();

    static final ProcedureSpec COUNT_REMINDERS_BY_TYPE =
            ProcedureSpec.function(PACKAGE, "count_reminders_by_type", "result", Types.NUMERIC)
                    .in("p_appointment_id", Types.NUMERIC)
                    .in("p_type", Types.VARCHAR)
                    .build();

    private final PackageGateway gateway;

    public AppointmentReminderRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    /** Inserta un nuevo registro de recordatorio/notificación de cita y devuelve su ID autogenerado. */
    public Long save(AppointmentReminder recordatorio) {
        Instant sentAt = recordatorio.sentAt() != null ? recordatorio.sentAt() : Instant.now();
        Map<String, Object> out = gateway.execute(CREATE_REMINDER, new MapSqlParameterSource()
                .addValue("p_public_id", recordatorio.publicId(), Types.VARCHAR)
                .addValue("p_appointment_id", recordatorio.appointmentId(), Types.NUMERIC)
                .addValue("p_patient_id", recordatorio.patientId(), Types.NUMERIC)
                .addValue("p_type", recordatorio.type(), Types.VARCHAR)
                .addValue("p_channel", recordatorio.channel() != null ? recordatorio.channel() : "EMAIL", Types.VARCHAR)
                .addValue("p_recipient", recordatorio.recipient(), Types.VARCHAR)
                .addValue("p_delivery_status",
                        recordatorio.deliveryStatus() != null ? recordatorio.deliveryStatus() : "ENVIADO", Types.VARCHAR)
                .addValue("p_error_message", recordatorio.errorMessage(), Types.VARCHAR)
                .addValue("p_sent_at", Timestamp.from(sentAt), Types.TIMESTAMP));

        Number key = (Number) out.get("o_id");
        return key != null ? key.longValue() : null;
    }

    /** Lista los recordatorios asociados a una cita. */
    public List<AppointmentReminder> listByAppointmentId(Long citaId) {
        return gateway.list(
                LIST_REMINDERS_BY_APPOINTMENT,
                new MapSqlParameterSource().addValue("p_appointment_id", citaId, Types.NUMERIC),
                "o_rows");
    }

    /** Cuenta cuántos recordatorios de un tipo específico se han emitido para una cita. */
    public int countByAppointmentIdAndType(Long citaId, String tipo) {
        Number count = gateway.callFunction(
                COUNT_REMINDERS_BY_TYPE,
                new MapSqlParameterSource()
                        .addValue("p_appointment_id", citaId, Types.NUMERIC)
                        .addValue("p_type", tipo, Types.VARCHAR));
        return count != null ? count.intValue() : 0;
    }
}
