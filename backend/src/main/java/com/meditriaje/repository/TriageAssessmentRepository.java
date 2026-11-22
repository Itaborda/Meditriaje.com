package com.meditriaje.repository;

import com.meditriaje.dto.emergency.TriageAssessmentResponse;
import com.meditriaje.dto.triage.SymptomItemResponse;
import com.meditriaje.model.TriageAssessment;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Valoraciones clínicas de triaje de urgencias ({@code VALORACION_TRIAJE}, inmutable, Fase U, ADR-027, U04) a través
 * de {@code PKG_TRIAGE}. No contiene SQL.
 */
@Repository
public class TriageAssessmentRepository {

    static final String PACKAGE = "PKG_TRIAGE";

    /** Síntoma reportado listo para persistirse en {@code VALORACION_TRIAJE_SINTOMA}. */
    public record AssessmentSymptomEntry(Long symptomId, BigDecimal durationHours, int intensity) {}

    private record SymptomRow(String assessmentPublicId, SymptomItemResponse symptom) {}

    static final RowMapper<TriageAssessment> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        return new TriageAssessment(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("EPISODIO_ID"),
                rs.getInt("VERSION"),
                rs.getString("NIVEL"),
                rs.getString("MOTIVO_CONSULTA"),
                rs.getString("HALLAZGOS_CLINICOS"),
                rs.getString("PRESION_ARTERIAL"),
                nullableInt(rs, "FRECUENCIA_CARDIACA"),
                nullableInt(rs, "FRECUENCIA_RESPIRATORIA"),
                nullableInt(rs, "SATURACION_OXIGENO"),
                rs.getBigDecimal("TEMPERATURA"),
                nullableInt(rs, "ESCALA_GLASGOW"),
                rs.getLong("EVALUADOR_USUARIO_ID"),
                rs.getInt("ES_REEVALUACION") == 1,
                rs.getString("MOTIVO_REEVALUACION"),
                tsCreated != null ? tsCreated.toInstant() : null);
    };

    static final RowMapper<TriageAssessmentResponse> RESPONSE_MAPPER = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        return new TriageAssessmentResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("EPISODIO_PUB_ID"),
                rs.getInt("VERSION"),
                rs.getString("NIVEL"),
                rs.getString("MOTIVO_CONSULTA"),
                rs.getString("HALLAZGOS_CLINICOS"),
                rs.getString("PRESION_ARTERIAL"),
                nullableInt(rs, "FRECUENCIA_CARDIACA"),
                nullableInt(rs, "FRECUENCIA_RESPIRATORIA"),
                nullableInt(rs, "SATURACION_OXIGENO"),
                rs.getBigDecimal("TEMPERATURA"),
                nullableInt(rs, "ESCALA_GLASGOW"),
                rs.getString("EVALUADOR_NOMBRE"),
                rs.getInt("ES_REEVALUACION") == 1,
                rs.getString("MOTIVO_REEVALUACION"),
                tsCreated != null ? tsCreated.toInstant() : null);
    };

    static final RowMapper<SymptomRow> SYMPTOM_ROW_MAPPER = (rs, rowNum) -> new SymptomRow(
            rs.getString("VALORACION_PUBLIC_ID"),
            new SymptomItemResponse(
                    rs.getString("SINTOMA_CODIGO"),
                    rs.getString("SINTOMA_NOMBRE"),
                    rs.getBigDecimal("DURACION_HORAS"),
                    rs.getInt("INTENSIDAD"),
                    rs.getInt("ES_ALARMA") == 1));

    static final ProcedureSpec GET_NEXT_ASSESSMENT_VERSION =
            ProcedureSpec.function(PACKAGE, "get_next_assessment_version", "result", Types.NUMERIC)
                    .in("p_episode_id", Types.NUMERIC)
                    .build();

    static final ProcedureSpec CREATE_ASSESSMENT = ProcedureSpec.procedure(PACKAGE, "create_assessment")
            .in("p_public_id", Types.VARCHAR)
            .in("p_episode_id", Types.NUMERIC)
            .in("p_version", Types.NUMERIC)
            .in("p_level", Types.VARCHAR)
            .in("p_chief_complaint", Types.VARCHAR)
            .in("p_findings", Types.VARCHAR)
            .in("p_blood_pressure", Types.VARCHAR)
            .in("p_heart_rate", Types.NUMERIC)
            .in("p_respiratory_rate", Types.NUMERIC)
            .in("p_oxygen_saturation", Types.NUMERIC)
            .in("p_temperature", Types.NUMERIC)
            .in("p_glasgow", Types.NUMERIC)
            .in("p_evaluator_user_id", Types.NUMERIC)
            .in("p_is_reassessment", Types.NUMERIC)
            .in("p_reassessment_reason", Types.VARCHAR)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec ADD_ASSESSMENT_SYMPTOM = ProcedureSpec.procedure(PACKAGE, "add_assessment_symptom")
            .in("p_assessment_id", Types.NUMERIC)
            .in("p_symptom_id", Types.NUMERIC)
            .in("p_duration_hours", Types.NUMERIC)
            .in("p_intensity", Types.NUMERIC)
            .build();

    static final ProcedureSpec LIST_ASSESSMENTS_BY_EPISODE =
            ProcedureSpec.procedure(PACKAGE, "list_assessments_by_episode")
                    .in("p_episode_id", Types.NUMERIC)
                    .cursor("o_rows", RESPONSE_MAPPER)
                    .build();

    static final ProcedureSpec LIST_ASSESSMENT_SYMPTOMS =
            ProcedureSpec.procedure(PACKAGE, "list_assessment_symptoms")
                    .in("p_episode_id", Types.NUMERIC)
                    .cursor("o_rows", SYMPTOM_ROW_MAPPER)
                    .build();

    static final ProcedureSpec GET_LATEST_ASSESSMENT = ProcedureSpec.procedure(PACKAGE, "get_latest_assessment")
            .in("p_episode_id", Types.NUMERIC)
            .cursor("o_row", ROW_MAPPER)
            .build();

    private final PackageGateway gateway;

    public TriageAssessmentRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public int getNextVersion(Long episodioId) {
        Number version = gateway.callFunction(
                GET_NEXT_ASSESSMENT_VERSION,
                new MapSqlParameterSource().addValue("p_episode_id", episodioId, Types.NUMERIC));
        return version != null ? version.intValue() : 1;
    }

    public TriageAssessment save(TriageAssessment valoracion) {
        Map<String, Object> out = gateway.execute(CREATE_ASSESSMENT, new MapSqlParameterSource()
                .addValue("p_public_id", valoracion.publicId(), Types.VARCHAR)
                .addValue("p_episode_id", valoracion.episodeId(), Types.NUMERIC)
                .addValue("p_version", valoracion.version(), Types.NUMERIC)
                .addValue("p_level", valoracion.level(), Types.VARCHAR)
                .addValue("p_chief_complaint", valoracion.consultationReason(), Types.VARCHAR)
                .addValue("p_findings", valoracion.clinicalFindings(), Types.VARCHAR)
                .addValue("p_blood_pressure", valoracion.bloodPressure(), Types.VARCHAR)
                .addValue("p_heart_rate", valoracion.heartRate(), Types.NUMERIC)
                .addValue("p_respiratory_rate", valoracion.respiratoryRate(), Types.NUMERIC)
                .addValue("p_oxygen_saturation", valoracion.oxygenSaturation(), Types.NUMERIC)
                .addValue("p_temperature", valoracion.temperature(), Types.NUMERIC)
                .addValue("p_glasgow", valoracion.glasgowScale(), Types.NUMERIC)
                .addValue("p_evaluator_user_id", valoracion.evaluatorUserId(), Types.NUMERIC)
                .addValue("p_is_reassessment", valoracion.isReevaluation() ? 1 : 0, Types.NUMERIC)
                .addValue("p_reassessment_reason", valoracion.reevaluationReason(), Types.VARCHAR));

        Number id = (Number) out.get("o_id");
        Long generatedId = id != null ? id.longValue() : null;

        return new TriageAssessment(
                generatedId,
                valoracion.publicId(),
                valoracion.episodeId(),
                valoracion.version(),
                valoracion.level(),
                valoracion.consultationReason(),
                valoracion.clinicalFindings(),
                valoracion.bloodPressure(),
                valoracion.heartRate(),
                valoracion.respiratoryRate(),
                valoracion.oxygenSaturation(),
                valoracion.temperature(),
                valoracion.glasgowScale(),
                valoracion.evaluatorUserId(),
                valoracion.isReevaluation(),
                valoracion.reevaluationReason(),
                Instant.now());
    }

    /**
     * Persiste los síntomas reportados en una valoración presencial de triaje (append-only).
     */
    public void saveSymptoms(Long assessmentId, List<AssessmentSymptomEntry> symptoms) {
        Objects.requireNonNull(assessmentId, "assessmentId no puede ser nulo");
        if (symptoms == null || symptoms.isEmpty()) {
            return;
        }
        for (AssessmentSymptomEntry s : symptoms) {
            gateway.execute(ADD_ASSESSMENT_SYMPTOM, new MapSqlParameterSource()
                    .addValue("p_assessment_id", assessmentId, Types.NUMERIC)
                    .addValue("p_symptom_id", s.symptomId(), Types.NUMERIC)
                    .addValue("p_duration_hours", s.durationHours(), Types.NUMERIC)
                    .addValue("p_intensity", s.intensity(), Types.NUMERIC));
        }
    }

    public List<TriageAssessmentResponse> listByEpisodeId(Long episodioId) {
        List<TriageAssessmentResponse> assessments = gateway.list(
                LIST_ASSESSMENTS_BY_EPISODE,
                new MapSqlParameterSource().addValue("p_episode_id", episodioId, Types.NUMERIC),
                "o_rows");
        if (assessments.isEmpty()) {
            return assessments;
        }

        List<SymptomRow> symptomRows = gateway.list(
                LIST_ASSESSMENT_SYMPTOMS,
                new MapSqlParameterSource().addValue("p_episode_id", episodioId, Types.NUMERIC),
                "o_rows");
        if (symptomRows.isEmpty()) {
            return assessments;
        }

        Map<String, List<SymptomItemResponse>> symptomsByAssessment = symptomRows.stream()
                .collect(Collectors.groupingBy(
                        SymptomRow::assessmentPublicId,
                        Collectors.mapping(SymptomRow::symptom, Collectors.toList())));

        return assessments.stream()
                .map(a -> new TriageAssessmentResponse(
                        a.publicId(),
                        a.episodePublicId(),
                        a.version(),
                        a.level(),
                        a.consultationReason(),
                        a.clinicalFindings(),
                        a.bloodPressure(),
                        a.heartRate(),
                        a.respiratoryRate(),
                        a.oxygenSaturation(),
                        a.temperature(),
                        a.glasgowScale(),
                        a.evaluatorName(),
                        a.isReevaluation(),
                        a.reevaluationReason(),
                        a.createdAt(),
                        symptomsByAssessment.getOrDefault(a.publicId(), List.of())))
                .toList();
    }

    public Optional<TriageAssessment> findLatestByEpisodeId(Long episodioId) {
        List<TriageAssessment> rows = gateway.list(
                GET_LATEST_ASSESSMENT,
                new MapSqlParameterSource().addValue("p_episode_id", episodioId, Types.NUMERIC),
                "o_row");
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
