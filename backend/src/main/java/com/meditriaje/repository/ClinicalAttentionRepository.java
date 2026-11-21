package com.meditriaje.repository;

import com.meditriaje.dto.clinical.ClinicalAttentionResponse;
import com.meditriaje.dto.clinical.AmendmentResponse;
import com.meditriaje.dto.clinical.VitalSignsDto;
import com.meditriaje.model.ClinicalAttention;
import com.meditriaje.model.VitalSign;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Repositorio de {@code ATENCION}, {@code SIGNO_VITAL} y {@code ATENCION_ENMIENDA} a través de
 * {@code PKG_CLINICAL} (ADR-007, ADR-008, HU-07, HU-09). No contiene SQL: declara las rutinas del paquete y
 * delega la llamada en {@link PackageGateway}.
 */
@Repository
public class ClinicalAttentionRepository {

    static final String PACKAGE = "PKG_CLINICAL";

    static final RowMapper<ClinicalAttention> ENCOUNTER_MAPPER = (rs, rowNum) -> {
        Timestamp tsCierre = rs.getTimestamp("FECHA_CIERRE");
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        Long diagId = rs.getObject("DIAGNOSTICO_PRINCIPAL_ID") != null ? rs.getLong("DIAGNOSTICO_PRINCIPAL_ID") : null;

        return new ClinicalAttention(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("CITA_ID"),
                rs.getLong("PACIENTE_ID"),
                rs.getLong("PROFESIONAL_ID"),
                diagId,
                rs.getString("MOTIVO_CONSULTA"),
                rs.getString("EVOLUCION"),
                rs.getString("INDICACIONES"),
                rs.getString("ESTADO"),
                tsCierre != null ? tsCierre.toInstant() : null,
                tsCreated != null ? tsCreated.toInstant() : null);
    };

    static final RowMapper<VitalSignsDto> VITAL_SIGNS_MAPPER = (rs, rowNum) -> new VitalSignsDto(
            getNullableInt(rs, "PRESION_SISTOLICA"),
            getNullableInt(rs, "PRESION_DIASTOLICA"),
            getNullableInt(rs, "FRECUENCIA_CARDIACA"),
            getNullableInt(rs, "FRECUENCIA_RESPIRATORIA"),
            rs.getBigDecimal("TEMPERATURA"),
            getNullableInt(rs, "SATURACION_OXIGENO"),
            rs.getBigDecimal("PESO_KG"),
            rs.getBigDecimal("TALLA_CM"));

    static final RowMapper<AmendmentResponse> AMENDMENT_MAPPER = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("FECHA_ENMIENDA");
        return new AmendmentResponse(
                rs.getString("PROFESIONAL_PUBLIC_ID"),
                rs.getString("PROFESIONAL_NOMBRE"),
                rs.getString("MOTIVO"),
                rs.getString("CONTENIDO"),
                ts != null ? ts.toInstant() : null);
    };

    /** Fila de detalle sin signos ni enmiendas; se completan después de leer el cursor. */
    record EncounterDetailRow(Long encounterId, ClinicalAttentionResponse base) {}

    static final RowMapper<EncounterDetailRow> DETAIL_MAPPER = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        Timestamp tsCierre = rs.getTimestamp("FECHA_CIERRE");
        return new EncounterDetailRow(rs.getLong("ATENCION_ID"), new ClinicalAttentionResponse(
                rs.getString("ATENCION_PUBLIC_ID"),
                rs.getString("CITA_PUBLIC_ID"),
                rs.getString("PACIENTE_PUBLIC_ID"),
                rs.getString("PACIENTE_NOMBRE"),
                rs.getString("PROFESIONAL_PUBLIC_ID"),
                rs.getString("PROFESIONAL_NOMBRE"),
                rs.getString("ESPECIALIDAD_NOMBRE"),
                rs.getString("ESTADO"),
                tsCreated != null ? tsCreated.toInstant() : null,
                tsCierre != null ? tsCierre.toInstant() : null,
                rs.getString("DIAGNOSTICO_CODIGO"),
                rs.getString("DIAGNOSTICO_DESCRIPCION"),
                rs.getString("MOTIVO_CONSULTA"),
                rs.getString("EVOLUCION"),
                rs.getString("INDICACIONES"),
                null,
                List.of()));
    };

    static final ProcedureSpec CREATE_ENCOUNTER = ProcedureSpec.procedure(PACKAGE, "create_encounter")
            .in("p_public_id", Types.VARCHAR)
            .in("p_appointment_id", Types.NUMERIC)
            .in("p_patient_id", Types.NUMERIC)
            .in("p_professional_id", Types.NUMERIC)
            .in("p_status", Types.VARCHAR)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_ENCOUNTER_BY_PUBLIC_ID = ProcedureSpec.procedure(
                    PACKAGE, "get_encounter_by_public_id")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_rows", ENCOUNTER_MAPPER)
            .build();

    static final ProcedureSpec GET_ENCOUNTER_BY_ID = ProcedureSpec.procedure(PACKAGE, "get_encounter_by_id")
            .in("p_id", Types.NUMERIC)
            .cursor("o_rows", ENCOUNTER_MAPPER)
            .build();

    static final ProcedureSpec GET_ENCOUNTER_BY_APPOINTMENT_ID = ProcedureSpec.procedure(
                    PACKAGE, "get_encounter_by_appointment_id")
            .in("p_appointment_id", Types.NUMERIC)
            .cursor("o_rows", ENCOUNTER_MAPPER)
            .build();

    static final ProcedureSpec COUNT_ENCOUNTERS_BY_APPOINTMENT = ProcedureSpec.function(
                    PACKAGE, "count_encounters_by_appointment", "result", Types.NUMERIC)
            .in("p_appointment_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec CLOSE_ENCOUNTER = ProcedureSpec.procedure(PACKAGE, "close_encounter")
            .in("p_id", Types.NUMERIC)
            .in("p_diagnosis_id", Types.NUMERIC)
            .in("p_reason", Types.VARCHAR)
            .in("p_evolution", Types.VARCHAR)
            .in("p_instructions", Types.VARCHAR)
            .in("p_closed_at", Types.TIMESTAMP)
            .out("o_affected", Types.NUMERIC)
            .build();

    static final ProcedureSpec CREATE_VITAL_SIGNS = ProcedureSpec.procedure(PACKAGE, "create_vital_signs")
            .in("p_encounter_id", Types.NUMERIC)
            .in("p_systolic", Types.NUMERIC)
            .in("p_diastolic", Types.NUMERIC)
            .in("p_heart_rate", Types.NUMERIC)
            .in("p_respiratory_rate", Types.NUMERIC)
            .in("p_temperature", Types.NUMERIC)
            .in("p_oxygen_saturation", Types.NUMERIC)
            .in("p_weight_kg", Types.NUMERIC)
            .in("p_height_cm", Types.NUMERIC)
            .build();

    static final ProcedureSpec LIST_VITAL_SIGNS = ProcedureSpec.procedure(PACKAGE, "list_vital_signs_by_encounter")
            .in("p_encounter_id", Types.NUMERIC)
            .cursor("o_rows", VITAL_SIGNS_MAPPER)
            .build();

    static final ProcedureSpec GET_ENCOUNTER_DETAIL = ProcedureSpec.procedure(
                    PACKAGE, "get_encounter_detail_by_public_id")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_rows", DETAIL_MAPPER)
            .build();

    static final ProcedureSpec CREATE_AMENDMENT = ProcedureSpec.procedure(PACKAGE, "create_amendment")
            .in("p_encounter_id", Types.NUMERIC)
            .in("p_professional_id", Types.NUMERIC)
            .in("p_reason", Types.VARCHAR)
            .in("p_content", Types.VARCHAR)
            .in("p_amended_at", Types.TIMESTAMP)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec LIST_AMENDMENTS = ProcedureSpec.procedure(PACKAGE, "list_amendments_by_encounter")
            .in("p_encounter_id", Types.NUMERIC)
            .cursor("o_rows", AMENDMENT_MAPPER)
            .build();

    static final ProcedureSpec COUNT_ENCOUNTERS_SINCE = ProcedureSpec.function(
                    PACKAGE, "count_encounters_since", "result", Types.NUMERIC)
            .in("p_patient_id", Types.NUMERIC)
            .in("p_professional_id", Types.NUMERIC)
            .in("p_since", Types.TIMESTAMP)
            .build();

    static final ProcedureSpec LIST_PATIENT_HISTORY = ProcedureSpec.procedure(PACKAGE, "list_patient_history")
            .in("p_patient_id", Types.NUMERIC)
            .in("p_offset", Types.NUMERIC)
            .in("p_size", Types.NUMERIC)
            .cursor("o_rows", DETAIL_MAPPER)
            .build();

    static final ProcedureSpec COUNT_PATIENT_HISTORY = ProcedureSpec.function(
                    PACKAGE, "count_patient_history", "result", Types.NUMERIC)
            .in("p_patient_id", Types.NUMERIC)
            .build();

    private final PackageGateway gateway;

    public ClinicalAttentionRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    /**
     * Inserta una nueva atención médica en estado inicial ABIERTA.
     *
     * @param atencion Entidad de atención a persistir
     * @return Identificador interno autogenerado
     */
    public Long create(ClinicalAttention atencion) {
        Objects.requireNonNull(atencion, "atencion no puede ser nula");
        Map<String, Object> out = gateway.execute(CREATE_ENCOUNTER, new MapSqlParameterSource()
                .addValue("p_public_id", atencion.publicId())
                .addValue("p_appointment_id", Objects.requireNonNull(atencion.appointmentId()))
                .addValue("p_patient_id", Objects.requireNonNull(atencion.patientId()))
                .addValue("p_professional_id", Objects.requireNonNull(atencion.professionalId()))
                .addValue("p_status", atencion.status() != null ? atencion.status() : "ABIERTA"));
        Number key = (Number) out.get("o_id");
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para la atencion clinica.");
        }
        return key.longValue();
    }

    public Optional<ClinicalAttention> findEntityByPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        return first(GET_ENCOUNTER_BY_PUBLIC_ID, new MapSqlParameterSource("p_public_id", publicId));
    }

    public Optional<ClinicalAttention> findEntityById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return first(GET_ENCOUNTER_BY_ID, new MapSqlParameterSource("p_id", id));
    }

    public Optional<ClinicalAttention> findEntityByAppointmentId(Long citaId) {
        if (citaId == null) {
            return Optional.empty();
        }
        return first(GET_ENCOUNTER_BY_APPOINTMENT_ID, new MapSqlParameterSource("p_appointment_id", citaId));
    }

    public boolean existsByAppointmentId(Long citaId) {
        if (citaId == null) {
            return false;
        }
        Number count = gateway.callFunction(COUNT_ENCOUNTERS_BY_APPOINTMENT,
                new MapSqlParameterSource("p_appointment_id", citaId));
        return count != null && count.longValue() > 0;
    }

    /** Cierra y consolida una atención médica haciéndola inmutable (ADR-008). */
    public void closeAttention(
            Long atencionId,
            Long diagnosticoId,
            String motivoConsulta,
            String evolucion,
            String indicaciones,
            Instant fechaCierre) {
        Objects.requireNonNull(atencionId, "atencionId no puede ser nulo");
        Objects.requireNonNull(diagnosticoId, "diagnosticoId no puede ser nulo");
        Objects.requireNonNull(motivoConsulta, "motivoConsulta no puede ser nulo");
        Objects.requireNonNull(evolucion, "evolucion no puede ser nula");
        Objects.requireNonNull(indicaciones, "indicaciones no puede ser nulo");
        Objects.requireNonNull(fechaCierre, "fechaCierre no puede ser nula");

        Map<String, Object> out = gateway.execute(CLOSE_ENCOUNTER, new MapSqlParameterSource()
                .addValue("p_id", atencionId)
                .addValue("p_diagnosis_id", diagnosticoId)
                .addValue("p_reason", motivoConsulta)
                .addValue("p_evolution", evolucion)
                .addValue("p_instructions", indicaciones)
                .addValue("p_closed_at", Timestamp.from(fechaCierre)));

        if (((Number) out.get("o_affected")).intValue() == 0) {
            throw new IllegalStateException("No fue posible cerrar la atencion: no existe o ya se encuentra cerrada.");
        }
    }

    /** Persiste los signos vitales medidos durante la atención médica. */
    public void saveVitalSigns(VitalSign sv) {
        Objects.requireNonNull(sv, "SignoVital no puede ser nulo");
        Objects.requireNonNull(sv.attentionId(), "atencionId no puede ser nulo");

        gateway.execute(CREATE_VITAL_SIGNS, new MapSqlParameterSource()
                .addValue("p_encounter_id", sv.attentionId())
                .addValue("p_systolic", sv.systolicPressure(), Types.NUMERIC)
                .addValue("p_diastolic", sv.diastolicPressure(), Types.NUMERIC)
                .addValue("p_heart_rate", sv.heartRate(), Types.NUMERIC)
                .addValue("p_respiratory_rate", sv.respiratoryRate(), Types.NUMERIC)
                .addValue("p_temperature", sv.temperature(), Types.NUMERIC)
                .addValue("p_oxygen_saturation", sv.oxygenSaturation(), Types.NUMERIC)
                .addValue("p_weight_kg", sv.weightKg(), Types.NUMERIC)
                .addValue("p_height_cm", sv.heightCm(), Types.NUMERIC));
    }

    public Optional<VitalSignsDto> findVitalSignsByAttentionId(Long atencionId) {
        if (atencionId == null) {
            return Optional.empty();
        }
        List<VitalSignsDto> lista = gateway.list(LIST_VITAL_SIGNS,
                new MapSqlParameterSource("p_encounter_id", atencionId), "o_rows");
        return lista.stream().findFirst();
    }

    /** Consulta detallada de una atención médica con sus datos relacionados y signos vitales (HU-07, HU-09). */
    public Optional<ClinicalAttentionResponse> findDetailByPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        List<EncounterDetailRow> rows = gateway.list(GET_ENCOUNTER_DETAIL,
                new MapSqlParameterSource("p_public_id", publicId), "o_rows");
        return rows.stream().findFirst().map(this::complete);
    }

    /** Registra una enmienda o aclaración médica inmutable en ATENCION_ENMIENDA (ADR-008). */
    public Long createAmendment(Long atencionId, Long profesionalId, String motivo, String contenido, Instant fechaEnmienda) {
        Objects.requireNonNull(atencionId, "atencionId no puede ser nulo");
        Objects.requireNonNull(profesionalId, "profesionalId no puede ser nulo");
        Objects.requireNonNull(motivo, "motivo no puede ser nulo");
        Objects.requireNonNull(contenido, "contenido no puede ser nulo");
        Objects.requireNonNull(fechaEnmienda, "fechaEnmienda no puede ser nula");

        Map<String, Object> out = gateway.execute(CREATE_AMENDMENT, new MapSqlParameterSource()
                .addValue("p_encounter_id", atencionId)
                .addValue("p_professional_id", profesionalId)
                .addValue("p_reason", motivo)
                .addValue("p_content", contenido)
                .addValue("p_amended_at", Timestamp.from(fechaEnmienda)));
        Number key = (Number) out.get("o_id");
        if (key == null) {
            throw new IllegalStateException("Error al registrar la enmienda en la base de datos.");
        }
        return key.longValue();
    }

    /** Consulta las enmiendas médicas asociadas a una atención en orden cronológico ascendente (ADR-008). */
    public List<AmendmentResponse> findAmendmentsByAttentionId(Long atencionId) {
        if (atencionId == null) {
            return List.of();
        }
        return gateway.list(LIST_AMENDMENTS, new MapSqlParameterSource("p_encounter_id", atencionId), "o_rows");
    }

    /**
     * Verifica si existe una atención médica previa entre el profesional y el paciente realizada dentro de la
     * ventana temporal especificada (ADR-007).
     */
    public boolean existsPreviousAttentionInWindow(Long profesionalId, Long pacienteId, Instant fechaLimite) {
        Objects.requireNonNull(profesionalId, "profesionalId no puede ser nulo");
        Objects.requireNonNull(pacienteId, "pacienteId no puede ser nulo");
        Objects.requireNonNull(fechaLimite, "fechaLimite no puede ser nulo");

        Number count = gateway.callFunction(COUNT_ENCOUNTERS_SINCE, new MapSqlParameterSource()
                .addValue("p_patient_id", pacienteId)
                .addValue("p_professional_id", profesionalId)
                .addValue("p_since", Timestamp.from(fechaLimite)));
        return count != null && count.longValue() > 0;
    }

    /** Consulta paginada del historial de atenciones médicas de un paciente con sus signos y enmiendas (HU-09). */
    public List<ClinicalAttentionResponse> listPatientHistory(Long pacienteId, int page, int size) {
        if (pacienteId == null) {
            return List.of();
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int offset = safePage * safeSize;

        List<EncounterDetailRow> rows = gateway.list(LIST_PATIENT_HISTORY, new MapSqlParameterSource()
                .addValue("p_patient_id", pacienteId)
                .addValue("p_offset", offset)
                .addValue("p_size", safeSize), "o_rows");
        return rows.stream().map(this::complete).toList();
    }

    /** Cuenta el número total de atenciones médicas registradas para un paciente (HU-09). */
    public int countPatientHistory(Long pacienteId) {
        if (pacienteId == null) {
            return 0;
        }
        Number total = gateway.callFunction(COUNT_PATIENT_HISTORY, new MapSqlParameterSource("p_patient_id", pacienteId));
        return total != null ? total.intValue() : 0;
    }

    private Optional<ClinicalAttention> first(ProcedureSpec spec, SqlParameterSource params) {
        List<ClinicalAttention> lista = gateway.list(spec, params, "o_rows");
        return lista.stream().findFirst();
    }

    /** Completa la fila de detalle con los signos vitales y las enmiendas de la atención. */
    private ClinicalAttentionResponse complete(EncounterDetailRow row) {
        ClinicalAttentionResponse b = row.base();
        VitalSignsDto signos = findVitalSignsByAttentionId(row.encounterId()).orElse(null);
        List<AmendmentResponse> enmiendas = findAmendmentsByAttentionId(row.encounterId());
        return new ClinicalAttentionResponse(
                b.publicId(),
                b.appointmentPublicId(),
                b.patientPublicId(),
                b.patientName(),
                b.professionalPublicId(),
                b.professionalName(),
                b.specialtyName(),
                b.status(),
                b.createdAt(),
                b.closedAt(),
                b.diagnosisCode(),
                b.diagnosisDescription(),
                b.consultationReason(),
                b.progressNote(),
                b.instructions(),
                signos,
                enmiendas);
    }

    private static Integer getNullableInt(ResultSet rs, String colName) throws SQLException {
        int v = rs.getInt(colName);
        return rs.wasNull() ? null : v;
    }
}
