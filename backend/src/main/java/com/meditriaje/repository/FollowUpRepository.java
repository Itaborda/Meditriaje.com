package com.meditriaje.repository;

import com.meditriaje.dto.followup.FollowUpResponse;
import com.meditriaje.model.PostAttentionFollowUp;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Repositorio de {@code SEGUIMIENTO_POST_ATENCION} (ADR-015, F2.2) a través de {@code PKG_CLINICAL}. No contiene
 * SQL y evita exponer claves autonuméricas en DTOs.
 */
@Repository
public class FollowUpRepository {

    static final RowMapper<PostAttentionFollowUp> ENTITY_MAPPER = (rs, rowNum) -> {
        Date dControl = rs.getDate("FECHA_SUGERIDA_CONTROL");
        Timestamp tsResp = rs.getTimestamp("FECHA_RESPUESTA_PACIENTE");
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        Timestamp tsUpdated = rs.getTimestamp("UPDATED_AT");

        return new PostAttentionFollowUp(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("ATENCION_ID"),
                rs.getLong("PACIENTE_ID"),
                rs.getLong("PROFESIONAL_ID"),
                rs.getString("TIPO"),
                rs.getString("INDICACIONES"),
                dControl != null ? dControl.toLocalDate() : null,
                rs.getString("ESTADO"),
                tsResp != null ? tsResp.toInstant() : null,
                rs.getString("REPORTE_PACIENTE"),
                tsCreated != null ? tsCreated.toInstant() : null,
                tsUpdated != null ? tsUpdated.toInstant() : null);
    };

    static final RowMapper<FollowUpResponse> RESPONSE_MAPPER = (rs, rowNum) -> {
        Date dControl = rs.getDate("FECHA_SUGERIDA_CONTROL");
        Timestamp tsResp = rs.getTimestamp("FECHA_RESPUESTA_PACIENTE");
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        Timestamp tsUpdated = rs.getTimestamp("UPDATED_AT");

        return new FollowUpResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("ATENCION_PUBLIC_ID"),
                rs.getString("PACIENTE_PUBLIC_ID"),
                rs.getString("PACIENTE_NOMBRE"),
                rs.getString("PROFESIONAL_PUBLIC_ID"),
                rs.getString("PROFESIONAL_NOMBRE"),
                rs.getString("ESPECIALIDAD_NOMBRE"),
                rs.getString("TIPO"),
                rs.getString("INDICACIONES"),
                dControl != null ? dControl.toLocalDate() : null,
                rs.getString("ESTADO"),
                tsResp != null ? tsResp.toInstant() : null,
                rs.getString("REPORTE_PACIENTE"),
                tsCreated != null ? tsCreated.toInstant() : null,
                tsUpdated != null ? tsUpdated.toInstant() : null);
    };

    static final ProcedureSpec CREATE_FOLLOW_UP = ProcedureSpec.procedure(ClinicalAttentionRepository.PACKAGE, "create_follow_up")
            .in("p_public_id", Types.VARCHAR)
            .in("p_encounter_id", Types.NUMERIC)
            .in("p_patient_id", Types.NUMERIC)
            .in("p_professional_id", Types.NUMERIC)
            .in("p_type", Types.VARCHAR)
            .in("p_instructions", Types.VARCHAR)
            .in("p_suggested_control_date", Types.DATE)
            .in("p_status", Types.VARCHAR)
            .in("p_patient_answered_at", Types.TIMESTAMP)
            .in("p_patient_report", Types.VARCHAR)
            .in("p_created_at", Types.TIMESTAMP)
            .in("p_updated_at", Types.TIMESTAMP)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_BY_PUBLIC_ID = ProcedureSpec.procedure(
                    ClinicalAttentionRepository.PACKAGE, "get_follow_up_by_public_id")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_rows", ENTITY_MAPPER)
            .build();

    static final ProcedureSpec GET_BY_ID = ProcedureSpec.procedure(ClinicalAttentionRepository.PACKAGE, "get_follow_up_by_id")
            .in("p_id", Types.NUMERIC)
            .cursor("o_rows", ENTITY_MAPPER)
            .build();

    static final ProcedureSpec GET_VIEW_BY_PUBLIC_ID = ProcedureSpec.procedure(
                    ClinicalAttentionRepository.PACKAGE, "get_follow_up_view_by_public_id")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_rows", RESPONSE_MAPPER)
            .build();

    static final ProcedureSpec REGISTER_REPORT = ProcedureSpec.procedure(
                    ClinicalAttentionRepository.PACKAGE, "register_follow_up_report")
            .in("p_id", Types.NUMERIC)
            .in("p_report", Types.VARCHAR)
            .in("p_answered_at", Types.TIMESTAMP)
            .build();

    static final ProcedureSpec UPDATE_STATUS = ProcedureSpec.procedure(
                    ClinicalAttentionRepository.PACKAGE, "update_follow_up_status")
            .in("p_id", Types.NUMERIC)
            .in("p_status", Types.VARCHAR)
            .build();

    static final ProcedureSpec LIST_BY_ENCOUNTER = ProcedureSpec.procedure(
                    ClinicalAttentionRepository.PACKAGE, "list_follow_ups_by_encounter")
            .in("p_encounter_id", Types.NUMERIC)
            .cursor("o_rows", RESPONSE_MAPPER)
            .build();

    static final ProcedureSpec LIST_BY_PATIENT = ProcedureSpec.procedure(
                    ClinicalAttentionRepository.PACKAGE, "list_follow_ups_by_patient")
            .in("p_patient_id", Types.NUMERIC)
            .in("p_status", Types.VARCHAR)
            .in("p_offset", Types.NUMERIC)
            .in("p_size", Types.NUMERIC)
            .cursor("o_rows", RESPONSE_MAPPER)
            .build();

    static final ProcedureSpec COUNT_BY_PATIENT = ProcedureSpec.function(
                    ClinicalAttentionRepository.PACKAGE, "count_follow_ups_by_patient", "result", Types.NUMERIC)
            .in("p_patient_id", Types.NUMERIC)
            .in("p_status", Types.VARCHAR)
            .build();

    private final PackageGateway gateway;

    public FollowUpRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    /** Inserta un nuevo seguimiento post-atención en la base de datos. */
    public Long save(PostAttentionFollowUp s) {
        Objects.requireNonNull(s, "SeguimientoPostAtencion no puede ser nulo");
        Map<String, Object> out = gateway.execute(CREATE_FOLLOW_UP, new MapSqlParameterSource()
                .addValue("p_public_id", s.publicId())
                .addValue("p_encounter_id", Objects.requireNonNull(s.attentionId()))
                .addValue("p_patient_id", Objects.requireNonNull(s.patientId()))
                .addValue("p_professional_id", Objects.requireNonNull(s.professionalId()))
                .addValue("p_type", s.type())
                .addValue("p_instructions", s.instructions())
                .addValue("p_suggested_control_date",
                        s.suggestedControlDate() != null ? Date.valueOf(s.suggestedControlDate()) : null, Types.DATE)
                .addValue("p_status", s.status() != null ? s.status() : "PENDIENTE")
                .addValue("p_patient_answered_at",
                        s.patientRespondedAt() != null ? Timestamp.from(s.patientRespondedAt()) : null,
                        Types.TIMESTAMP)
                .addValue("p_patient_report", s.patientReport(), Types.VARCHAR)
                .addValue("p_created_at", Timestamp.from(s.createdAt() != null ? s.createdAt() : Instant.now()))
                .addValue("p_updated_at", Timestamp.from(s.updatedAt() != null ? s.updatedAt() : Instant.now())));
        Number key = (Number) out.get("o_id");
        if (key == null) {
            throw new IllegalStateException("Error al obtener ID autogenerado para el seguimiento.");
        }
        return key.longValue();
    }

    /** Busca la entidad por su identificador público (UUID). */
    public Optional<PostAttentionFollowUp> findEntityByPublicId(String publicId) {
        List<PostAttentionFollowUp> lista =
                gateway.list(GET_BY_PUBLIC_ID, new MapSqlParameterSource("p_public_id", publicId), "o_rows");
        return lista.stream().findFirst();
    }

    /** Busca la entidad por su ID primario numérico. */
    public Optional<PostAttentionFollowUp> findEntityById(Long id) {
        List<PostAttentionFollowUp> lista =
                gateway.list(GET_BY_ID, new MapSqlParameterSource("p_id", id), "o_rows");
        return lista.stream().findFirst();
    }

    /** Busca un seguimiento y lo mapea al DTO completo con información relacionada. */
    public Optional<FollowUpResponse> findByPublicId(String publicId) {
        List<FollowUpResponse> lista =
                gateway.list(GET_VIEW_BY_PUBLIC_ID, new MapSqlParameterSource("p_public_id", publicId), "o_rows");
        return lista.stream().findFirst();
    }

    /** Registra el reporte de evolución aportado por el paciente y marca el seguimiento como COMPLETADO. */
    public void recordPatientReport(Long id, String reportePaciente, Instant fechaRespuesta) {
        gateway.execute(REGISTER_REPORT, new MapSqlParameterSource()
                .addValue("p_id", id)
                .addValue("p_report", reportePaciente)
                .addValue("p_answered_at", Timestamp.from(fechaRespuesta)));
    }

    /** Actualiza el estado de un seguimiento (p. ej. CANCELADO). */
    public void updateStatus(Long id, String nuevoEstado) {
        gateway.execute(UPDATE_STATUS, new MapSqlParameterSource()
                .addValue("p_id", id)
                .addValue("p_status", nuevoEstado));
    }

    /** Lista los seguimientos asociados a una atención médica específica. */
    public List<FollowUpResponse> listByAttentionId(Long atencionId) {
        return gateway.list(LIST_BY_ENCOUNTER, new MapSqlParameterSource("p_encounter_id", atencionId), "o_rows");
    }

    /** Lista paginada de seguimientos pertenecientes a un paciente con filtro opcional de estado. */
    public List<FollowUpResponse> listByPatientId(Long pacienteId, String estadoFiltro, int page, int size) {
        return gateway.list(LIST_BY_PATIENT, new MapSqlParameterSource()
                .addValue("p_patient_id", pacienteId)
                .addValue("p_status", normalizeStatus(estadoFiltro), Types.VARCHAR)
                .addValue("p_offset", page * size)
                .addValue("p_size", size), "o_rows");
    }

    /** Conteo total de seguimientos de un paciente con filtro opcional de estado. */
    public int countByPatientId(Long pacienteId, String estadoFiltro) {
        Number count = gateway.callFunction(COUNT_BY_PATIENT, new MapSqlParameterSource()
                .addValue("p_patient_id", pacienteId)
                .addValue("p_status", normalizeStatus(estadoFiltro), Types.VARCHAR));
        return count != null ? count.intValue() : 0;
    }

    /** Filtro de estado en mayúsculas; nulo cuando no se debe filtrar. */
    private static String normalizeStatus(String estadoFiltro) {
        return estadoFiltro != null && !estadoFiltro.isBlank() ? estadoFiltro.toUpperCase().trim() : null;
    }
}
