package com.meditriaje.repository;

import com.meditriaje.dto.admin.AdminCitaDetalleResponse;
import com.meditriaje.dto.appointment.CitaResponse;
import com.meditriaje.model.Cita;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Repositorio para la entidad {@code CITA} a través de {@code PKG_SCHEDULING}.
 * No contiene SQL.
 */
@Repository
public class CitaRepository {

    static final String PACKAGE = "PKG_SCHEDULING";

    static final RowMapper<Cita> CITA_MAPPER = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        Timestamp tsUpdated = rs.getTimestamp("UPDATED_AT");
        Long triajeId = rs.getObject("TRIAJE_ID") != null ? rs.getLong("TRIAJE_ID") : null;
        Long citaOrigenId = rs.getObject("CITA_ORIGEN_ID") != null ? rs.getLong("CITA_ORIGEN_ID") : null;
        return new Cita(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("SLOT_ID"),
                rs.getLong("PACIENTE_ID"),
                triajeId,
                citaOrigenId,
                rs.getString("ESTADO"),
                rs.getString("MOTIVO_CANCELACION"),
                tsCreated != null ? tsCreated.toInstant() : null,
                tsUpdated != null ? tsUpdated.toInstant() : null);
    };

    static final RowMapper<CitaResponse> CITA_RESPONSE_MAPPER = (rs, rowNum) -> {
        Timestamp tsInicio = rs.getTimestamp("FECHA_HORA_INICIO");
        Timestamp tsFin = rs.getTimestamp("FECHA_HORA_FIN");
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");

        String triajeNivel = null;
        try {
            triajeNivel = rs.getString("TRIAJE_NIVEL");
        } catch (SQLException ignored) {
        }

        String motivoConsulta = null;
        try {
            motivoConsulta = rs.getString("MOTIVO_CONSULTA");
        } catch (SQLException ignored) {
        }

        boolean tieneAtencion = false;
        try {
            Object obj = rs.getObject("TIENE_ATENCION");
            if (obj != null) {
                tieneAtencion = rs.getInt("TIENE_ATENCION") > 0;
            }
        } catch (SQLException ignored) {
        }

        String atencionPublicId = null;
        try {
            atencionPublicId = rs.getString("ATENCION_PUBLIC_ID");
        } catch (SQLException ignored) {
        }

        String atencionEstado = null;
        try {
            atencionEstado = rs.getString("ATENCION_ESTADO");
        } catch (SQLException ignored) {
        }

        return new CitaResponse(
                rs.getString("CITA_PUBLIC_ID"),
                rs.getString("SLOT_PUBLIC_ID"),
                rs.getString("PACIENTE_PUBLIC_ID"),
                rs.getString("PACIENTE_NOMBRE"),
                rs.getString("PROFESIONAL_PUBLIC_ID"),
                rs.getString("PROFESIONAL_NOMBRE"),
                rs.getString("ESPECIALIDAD_PUBLIC_ID"),
                rs.getString("ESPECIALIDAD_NOMBRE"),
                rs.getString("SEDE_PUBLIC_ID"),
                rs.getString("SEDE_NOMBRE"),
                rs.getString("SEDE_DIRECCION"),
                tsInicio != null ? tsInicio.toInstant() : null,
                tsFin != null ? tsFin.toInstant() : null,
                rs.getString("MODALIDAD"),
                rs.getString("ESTADO"),
                rs.getString("TRIAJE_PUBLIC_ID"),
                rs.getString("CITA_ORIGEN_PUBLIC_ID"),
                triajeNivel,
                motivoConsulta,
                tsCreated != null ? tsCreated.toInstant() : null,
                tieneAtencion,
                atencionPublicId,
                atencionEstado);
    };

    static final RowMapper<AdminCitaDetalleResponse> ADMIN_CITA_DETALLE_MAPPER = (rs, rowNum) -> {
        Timestamp tsInicio = rs.getTimestamp("FECHA_HORA_INICIO");
        Timestamp tsFin = rs.getTimestamp("FECHA_HORA_FIN");
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");

        return new AdminCitaDetalleResponse(
                rs.getString("CITA_PUBLIC_ID"),
                rs.getString("PACIENTE_PUBLIC_ID"),
                rs.getString("PACIENTE_NOMBRE"),
                rs.getString("PACIENTE_DOC_TIPO"),
                rs.getString("PACIENTE_DOC_NUM"),
                rs.getString("PACIENTE_TELEFONO"),
                rs.getString("PACIENTE_EMAIL"),
                rs.getString("PROFESIONAL_PUBLIC_ID"),
                rs.getString("PROFESIONAL_NOMBRE"),
                rs.getString("REGISTRO_MEDICO"),
                rs.getString("ESPECIALIDAD_PUBLIC_ID"),
                rs.getString("ESPECIALIDAD_NOMBRE"),
                rs.getString("SEDE_PUBLIC_ID"),
                rs.getString("SEDE_NOMBRE"),
                rs.getString("SEDE_CIUDAD"),
                tsInicio != null ? tsInicio.toInstant() : null,
                tsFin != null ? tsFin.toInstant() : null,
                rs.getString("MODALIDAD"),
                rs.getString("ESTADO"),
                rs.getString("MOTIVO_CANCELACION"),
                rs.getString("TRIAJE_PUBLIC_ID"),
                rs.getString("TRIAJE_NIVEL"),
                rs.getString("MOTIVO_CONSULTA"),
                tsCreated != null ? tsCreated.toInstant() : null);
    };

    static final ProcedureSpec CREATE_APPOINTMENT = ProcedureSpec.procedure(PACKAGE, "create_appointment")
            .in("p_public_id", Types.VARCHAR)
            .in("p_slot_id", Types.NUMERIC)
            .in("p_patient_id", Types.NUMERIC)
            .in("p_triage_id", Types.NUMERIC)
            .in("p_origin_appointment_id", Types.NUMERIC)
            .in("p_status", Types.VARCHAR)
            .in("p_cancel_reason", Types.VARCHAR)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_APPOINTMENT_DETAIL = ProcedureSpec.procedure(PACKAGE, "get_appointment_detail")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_row", CITA_RESPONSE_MAPPER)
            .build();

    static final ProcedureSpec GET_APPOINTMENT_BY_PUBLIC_ID =
            ProcedureSpec.procedure(PACKAGE, "get_appointment_by_public_id")
                    .in("p_public_id", Types.VARCHAR)
                    .cursor("o_row", CITA_MAPPER)
                    .build();

    static final ProcedureSpec GET_APPOINTMENT_BY_ID = ProcedureSpec.procedure(PACKAGE, "get_appointment_by_id")
            .in("p_id", Types.NUMERIC)
            .cursor("o_row", CITA_MAPPER)
            .build();

    static final ProcedureSpec UPDATE_APPOINTMENT_STATUS =
            ProcedureSpec.procedure(PACKAGE, "update_appointment_status")
                    .in("p_id", Types.NUMERIC)
                    .in("p_status", Types.VARCHAR)
                    .in("p_cancel_reason", Types.VARCHAR)
                    .build();

    static final ProcedureSpec COUNT_ACTIVE_APPOINTMENTS_IN_SLOT =
            ProcedureSpec.function(PACKAGE, "count_active_appointments_in_slot", "result", Types.NUMERIC)
                    .in("p_slot_id", Types.NUMERIC)
                    .build();

    static final ProcedureSpec LIST_PROFESSIONAL_AGENDA =
            ProcedureSpec.procedure(PACKAGE, "list_professional_agenda")
                    .in("p_professional_id", Types.NUMERIC)
                    .in("p_from", Types.TIMESTAMP)
                    .in("p_to", Types.TIMESTAMP)
                    .in("p_status", Types.VARCHAR)
                    .in("p_offset", Types.NUMERIC)
                    .in("p_limit", Types.NUMERIC)
                    .cursor("o_rows", CITA_RESPONSE_MAPPER)
                    .build();

    static final ProcedureSpec COUNT_PROFESSIONAL_AGENDA =
            ProcedureSpec.function(PACKAGE, "count_professional_agenda", "result", Types.NUMERIC)
                    .in("p_professional_id", Types.NUMERIC)
                    .in("p_from", Types.TIMESTAMP)
                    .in("p_to", Types.TIMESTAMP)
                    .in("p_status", Types.VARCHAR)
                    .build();

    static final ProcedureSpec COUNT_FUTURE_ACTIVE_APPOINTMENTS =
            ProcedureSpec.function(PACKAGE, "count_future_active_appointments", "result", Types.NUMERIC)
                    .in("p_professional_id", Types.NUMERIC)
                    .in("p_patient_id", Types.NUMERIC)
                    .in("p_now", Types.TIMESTAMP)
                    .build();

    static final ProcedureSpec LIST_PATIENT_APPOINTMENTS =
            ProcedureSpec.procedure(PACKAGE, "list_patient_appointments")
                    .in("p_patient_id", Types.NUMERIC)
                    .in("p_offset", Types.NUMERIC)
                    .in("p_limit", Types.NUMERIC)
                    .cursor("o_rows", CITA_RESPONSE_MAPPER)
                    .build();

    static final ProcedureSpec COUNT_PATIENT_APPOINTMENTS =
            ProcedureSpec.function(PACKAGE, "count_patient_appointments", "result", Types.NUMERIC)
                    .in("p_patient_id", Types.NUMERIC)
                    .build();

    static final ProcedureSpec LIST_ADMIN_APPOINTMENTS =
            ProcedureSpec.procedure(PACKAGE, "list_admin_appointments")
                    .in("p_from", Types.TIMESTAMP)
                    .in("p_to", Types.TIMESTAMP)
                    .in("p_professional_public_id", Types.VARCHAR)
                    .in("p_specialty_public_id", Types.VARCHAR)
                    .in("p_status", Types.VARCHAR)
                    .in("p_offset", Types.NUMERIC)
                    .in("p_limit", Types.NUMERIC)
                    .cursor("o_rows", ADMIN_CITA_DETALLE_MAPPER)
                    .build();

    static final ProcedureSpec COUNT_ADMIN_APPOINTMENTS =
            ProcedureSpec.function(PACKAGE, "count_admin_appointments", "result", Types.NUMERIC)
                    .in("p_from", Types.TIMESTAMP)
                    .in("p_to", Types.TIMESTAMP)
                    .in("p_professional_public_id", Types.VARCHAR)
                    .in("p_specialty_public_id", Types.VARCHAR)
                    .in("p_status", Types.VARCHAR)
                    .build();

    private final PackageGateway gateway;

    public CitaRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    /** Inserta una nueva cita asistencial y retorna el ID interno autogenerado. */
    public Long crear(Cita cita) {
        Objects.requireNonNull(cita, "La cita no puede ser nula");
        Map<String, Object> out = gateway.execute(CREATE_APPOINTMENT, new MapSqlParameterSource()
                .addValue("p_public_id", cita.publicId(), Types.VARCHAR)
                .addValue("p_slot_id", cita.slotId(), Types.NUMERIC)
                .addValue("p_patient_id", cita.pacienteId(), Types.NUMERIC)
                .addValue("p_triage_id", cita.triajeId(), Types.NUMERIC)
                .addValue("p_origin_appointment_id", cita.citaOrigenId(), Types.NUMERIC)
                .addValue("p_status", cita.estado(), Types.VARCHAR)
                .addValue("p_cancel_reason", cita.motivoCancelacion(), Types.VARCHAR));

        Number key = (Number) out.get("o_id");
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para la cita.");
        }
        return key.longValue();
    }

    /** Busca los detalles consolidados de una cita por su clave pública UUID (ADR-003, HU-04). */
    public Optional<CitaResponse> buscarPorPublicId(String publicId) {
        return first(gateway.list(
                GET_APPOINTMENT_DETAIL,
                new MapSqlParameterSource().addValue("p_public_id", publicId, Types.VARCHAR),
                "o_row"));
    }

    /** Busca la entidad Cita por su clave pública UUID. */
    public Optional<Cita> buscarEntidadPorPublicId(String publicId) {
        return first(gateway.list(
                GET_APPOINTMENT_BY_PUBLIC_ID,
                new MapSqlParameterSource().addValue("p_public_id", publicId, Types.VARCHAR),
                "o_row"));
    }

    /** Busca la entidad Cita por su ID interno. */
    public Optional<Cita> buscarEntidadPorId(Long id) {
        return first(gateway.list(
                GET_APPOINTMENT_BY_ID,
                new MapSqlParameterSource().addValue("p_id", id, Types.NUMERIC),
                "o_row"));
    }

    /** Actualiza el estado y motivo de cancelación de una cita asistencial. */
    public void actualizarEstado(Long id, String nuevoEstado, String motivoCancelacion) {
        gateway.execute(UPDATE_APPOINTMENT_STATUS, new MapSqlParameterSource()
                .addValue("p_id", id, Types.NUMERIC)
                .addValue("p_status", nuevoEstado, Types.VARCHAR)
                .addValue("p_cancel_reason", motivoCancelacion, Types.VARCHAR));
    }

    /** Determina si ya existe una cita activa (PROGRAMADA o CONFIRMADA) para el slot dado. */
    public boolean existeCitaActivaEnSlot(Long slotId) {
        Number count = gateway.callFunction(
                COUNT_ACTIVE_APPOINTMENTS_IN_SLOT,
                new MapSqlParameterSource().addValue("p_slot_id", slotId, Types.NUMERIC));
        return count != null && count.intValue() > 0;
    }

    /** Consulta la agenda de citas del profesional asistencial con filtros opcionales (HU-06, ADR-007). */
    public List<CitaResponse> listarAgendaProfesional(
            Long profesionalId,
            Instant fechaDesde,
            Instant fechaHasta,
            String estado,
            int page,
            int size) {
        Objects.requireNonNull(profesionalId, "profesionalId no puede ser nulo");
        int offset = Math.max(0, page) * Math.max(1, size);
        int limit = Math.max(1, size);
        return gateway.list(
                LIST_PROFESSIONAL_AGENDA,
                new MapSqlParameterSource()
                        .addValue("p_professional_id", profesionalId, Types.NUMERIC)
                        .addValue("p_from", toTimestamp(fechaDesde), Types.TIMESTAMP)
                        .addValue("p_to", toTimestamp(fechaHasta), Types.TIMESTAMP)
                        .addValue("p_status", normalizeUpper(estado), Types.VARCHAR)
                        .addValue("p_offset", offset, Types.NUMERIC)
                        .addValue("p_limit", limit, Types.NUMERIC),
                "o_rows");
    }

    /** Cuenta el total de citas en la agenda del profesional para los filtros dados (HU-06). */
    public int contarAgendaProfesional(Long profesionalId, Instant fechaDesde, Instant fechaHasta, String estado) {
        Objects.requireNonNull(profesionalId, "profesionalId no puede ser nulo");
        Number total = gateway.callFunction(
                COUNT_PROFESSIONAL_AGENDA,
                new MapSqlParameterSource()
                        .addValue("p_professional_id", profesionalId, Types.NUMERIC)
                        .addValue("p_from", toTimestamp(fechaDesde), Types.TIMESTAMP)
                        .addValue("p_to", toTimestamp(fechaHasta), Types.TIMESTAMP)
                        .addValue("p_status", normalizeUpper(estado), Types.VARCHAR));
        return total != null ? total.intValue() : 0;
    }

    /** Verifica si existe una cita activa futura entre un profesional y un paciente (ADR-007). */
    public boolean existeCitaActivaFutura(Long profesionalId, Long pacienteId, Instant ahora) {
        Objects.requireNonNull(profesionalId, "profesionalId no puede ser nulo");
        Objects.requireNonNull(pacienteId, "pacienteId no puede ser nulo");
        Objects.requireNonNull(ahora, "ahora no puede ser nulo");
        Number count = gateway.callFunction(
                COUNT_FUTURE_ACTIVE_APPOINTMENTS,
                new MapSqlParameterSource()
                        .addValue("p_professional_id", profesionalId, Types.NUMERIC)
                        .addValue("p_patient_id", pacienteId, Types.NUMERIC)
                        .addValue("p_now", Timestamp.from(ahora), Types.TIMESTAMP));
        return count != null && count.intValue() > 0;
    }

    /** Consulta paginada de las citas médicas reservadas por un paciente (HU-09). */
    public List<CitaResponse> listarPorPacienteId(Long pacienteId, int page, int size) {
        Objects.requireNonNull(pacienteId, "pacienteId no puede ser nulo");
        int offset = Math.max(0, page) * Math.max(1, size);
        int limit = Math.max(1, size);
        return gateway.list(
                LIST_PATIENT_APPOINTMENTS,
                new MapSqlParameterSource()
                        .addValue("p_patient_id", pacienteId, Types.NUMERIC)
                        .addValue("p_offset", offset, Types.NUMERIC)
                        .addValue("p_limit", limit, Types.NUMERIC),
                "o_rows");
    }

    /** Cuenta el total de citas asociadas a un paciente (HU-09). */
    public int contarPorPacienteId(Long pacienteId) {
        Objects.requireNonNull(pacienteId, "pacienteId no puede ser nulo");
        Number count = gateway.callFunction(
                COUNT_PATIENT_APPOINTMENTS,
                new MapSqlParameterSource().addValue("p_patient_id", pacienteId, Types.NUMERIC));
        return count != null ? count.intValue() : 0;
    }

    /** Consulta consolidada de citas médicas para supervisión, calendario semanal y reportes de administrador. */
    public List<AdminCitaDetalleResponse> listarCitasAdmin(
            Instant fechaDesde,
            Instant fechaHasta,
            String profesionalPublicId,
            String especialidadPublicId,
            String estado,
            int page,
            int size) {
        Integer offset = size > 0 ? Math.max(0, page) * size : null;
        Integer limit = size > 0 ? size : null;
        return gateway.list(
                LIST_ADMIN_APPOINTMENTS,
                new MapSqlParameterSource()
                        .addValue("p_from", toTimestamp(fechaDesde), Types.TIMESTAMP)
                        .addValue("p_to", toTimestamp(fechaHasta), Types.TIMESTAMP)
                        .addValue("p_professional_public_id", normalizeTrim(profesionalPublicId), Types.VARCHAR)
                        .addValue("p_specialty_public_id", normalizeTrim(especialidadPublicId), Types.VARCHAR)
                        .addValue("p_status", normalizeUpper(estado), Types.VARCHAR)
                        .addValue("p_offset", offset, Types.NUMERIC)
                        .addValue("p_limit", limit, Types.NUMERIC),
                "o_rows");
    }

    /** Cuenta el total de citas para los filtros administrativos dados. */
    public int contarCitasAdmin(
            Instant fechaDesde,
            Instant fechaHasta,
            String profesionalPublicId,
            String especialidadPublicId,
            String estado) {
        Number total = gateway.callFunction(
                COUNT_ADMIN_APPOINTMENTS,
                new MapSqlParameterSource()
                        .addValue("p_from", toTimestamp(fechaDesde), Types.TIMESTAMP)
                        .addValue("p_to", toTimestamp(fechaHasta), Types.TIMESTAMP)
                        .addValue("p_professional_public_id", normalizeTrim(profesionalPublicId), Types.VARCHAR)
                        .addValue("p_specialty_public_id", normalizeTrim(especialidadPublicId), Types.VARCHAR)
                        .addValue("p_status", normalizeUpper(estado), Types.VARCHAR));
        return total != null ? total.intValue() : 0;
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static String normalizeTrim(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private static String normalizeUpper(String value) {
        return value != null && !value.isBlank() ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static <T> Optional<T> first(List<T> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
