package com.meditriaje.repository;

import com.meditriaje.dto.affiliation.AffiliationResponse;
import com.meditriaje.model.PatientAffiliation;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Repositorio de {@code AFILIACION_PACIENTE} a través de {@code PKG_AFFILIATIONS} (Fase A, ADR-025). No
 * contiene SQL: declara las rutinas del paquete y delega en {@link PackageGateway}.
 */
@Repository
public class PatientAffiliationRepository {

    static final RowMapper<PatientAffiliation> ROW_MAPPER = (rs, rowNum) -> {
        Date d = rs.getDate("FECHA_AFILIACION");
        Timestamp tsVerif = rs.getTimestamp("ULTIMA_VERIFICACION_AT");
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        Timestamp tsUpdated = rs.getTimestamp("UPDATED_AT");
        return new PatientAffiliation(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                getNullableLong(rs, "PACIENTE_ID"),
                rs.getString("TIPO_DOCUMENTO"),
                rs.getString("NUMERO_DOCUMENTO"),
                rs.getLong("EPS_ID"),
                rs.getString("REGIMEN"),
                rs.getString("TIPO_AFILIADO"),
                rs.getString("ESTADO"),
                d != null ? d.toLocalDate() : null,
                rs.getString("FUENTE_VERIFICACION"),
                tsVerif != null ? tsVerif.toInstant() : null,
                tsCreated != null ? tsCreated.toInstant() : null,
                tsUpdated != null ? tsUpdated.toInstant() : null);
    };

    static final RowMapper<AffiliationResponse> RESPONSE_MAPPER = (rs, rowNum) -> {
        Date d = rs.getDate("FECHA_AFILIACION");
        Timestamp tsVerif = rs.getTimestamp("ULTIMA_VERIFICACION_AT");
        return new AffiliationResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("PACIENTE_PUB_ID"),
                rs.getString("TIPO_DOCUMENTO"),
                rs.getString("NUMERO_DOCUMENTO"),
                rs.getString("PACIENTE_NOMBRE"),
                rs.getString("EPS_CODIGO"),
                rs.getString("EPS_NOMBRE"),
                rs.getString("REGIMEN"),
                rs.getString("TIPO_AFILIADO"),
                rs.getString("ESTADO"),
                d != null ? d.toLocalDate() : null,
                rs.getString("FUENTE_VERIFICACION"),
                tsVerif != null ? tsVerif.toInstant() : null);
    };

    static final ProcedureSpec CREATE_AFFILIATION = ProcedureSpec.procedure(
                    EpsEntityRepository.PACKAGE, "create_affiliation")
            .in("p_public_id", Types.VARCHAR)
            .in("p_patient_id", Types.NUMERIC)
            .in("p_document_type", Types.VARCHAR)
            .in("p_document_number", Types.VARCHAR)
            .in("p_eps_id", Types.NUMERIC)
            .in("p_regime", Types.VARCHAR)
            .in("p_affiliate_type", Types.VARCHAR)
            .in("p_status", Types.VARCHAR)
            .in("p_affiliation_date", Types.DATE)
            .in("p_verification_source", Types.VARCHAR)
            .in("p_last_verification_at", Types.TIMESTAMP)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_AFFILIATION_BY_ID = ProcedureSpec.procedure(
                    EpsEntityRepository.PACKAGE, "get_affiliation_by_id")
            .in("p_id", Types.NUMERIC)
            .cursor("o_row", ROW_MAPPER)
            .build();

    static final ProcedureSpec GET_AFFILIATION_BY_DOCUMENT = ProcedureSpec.procedure(
                    EpsEntityRepository.PACKAGE, "get_affiliation_by_document")
            .in("p_document_type", Types.VARCHAR)
            .in("p_document_number", Types.VARCHAR)
            .cursor("o_row", ROW_MAPPER)
            .build();

    static final ProcedureSpec UPDATE_AFFILIATION = ProcedureSpec.procedure(
                    EpsEntityRepository.PACKAGE, "update_affiliation")
            .in("p_id", Types.NUMERIC)
            .in("p_eps_id", Types.NUMERIC)
            .in("p_regime", Types.VARCHAR)
            .in("p_affiliate_type", Types.VARCHAR)
            .in("p_status", Types.VARCHAR)
            .in("p_last_verification_at", Types.TIMESTAMP)
            .build();

    static final ProcedureSpec LINK_PATIENT_AFFILIATION = ProcedureSpec.procedure(
                    EpsEntityRepository.PACKAGE, "link_patient_affiliation")
            .in("p_document_type", Types.VARCHAR)
            .in("p_document_number", Types.VARCHAR)
            .in("p_patient_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_AFFILIATION_DETAIL_BY_DOCUMENT = ProcedureSpec.procedure(
                    EpsEntityRepository.PACKAGE, "get_affiliation_detail_by_document")
            .in("p_document_type", Types.VARCHAR)
            .in("p_document_number", Types.VARCHAR)
            .cursor("o_row", RESPONSE_MAPPER)
            .build();

    static final ProcedureSpec GET_AFFILIATION_DETAIL_BY_PATIENT_ID = ProcedureSpec.procedure(
                    EpsEntityRepository.PACKAGE, "get_affiliation_detail_by_patient_id")
            .in("p_patient_id", Types.NUMERIC)
            .cursor("o_row", RESPONSE_MAPPER)
            .build();

    private final PackageGateway gateway;

    public PatientAffiliationRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public PatientAffiliation save(PatientAffiliation a) {
        Objects.requireNonNull(a, "afiliacion no puede ser nula");
        Map<String, Object> out = gateway.execute(CREATE_AFFILIATION, new MapSqlParameterSource()
                .addValue("p_public_id", a.publicId())
                .addValue("p_patient_id", a.patientId(), Types.NUMERIC)
                .addValue("p_document_type", a.documentType())
                .addValue("p_document_number", a.documentNumber())
                .addValue("p_eps_id", Objects.requireNonNull(a.epsId()))
                .addValue("p_regime", a.regime())
                .addValue("p_affiliate_type", a.affiliateType(), Types.VARCHAR)
                .addValue("p_status", a.status(), Types.VARCHAR)
                .addValue("p_affiliation_date",
                        a.affiliationDate() != null ? Date.valueOf(a.affiliationDate()) : null, Types.DATE)
                .addValue("p_verification_source", a.verificationSource(), Types.VARCHAR)
                .addValue("p_last_verification_at",
                        a.lastVerifiedAt() != null ? Timestamp.from(a.lastVerifiedAt()) : null,
                        Types.TIMESTAMP));

        Number key = (Number) out.get("o_id");
        Long id = key != null ? key.longValue() : null;
        return findById(id).orElseThrow(() -> new IllegalStateException("Error al recuperar afiliación guardada"));
    }

    public Optional<PatientAffiliation> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return gateway.single(GET_AFFILIATION_BY_ID, new MapSqlParameterSource("p_id", id), "o_row");
    }

    public Optional<PatientAffiliation> findByDocument(String tipoDoc, String numDoc) {
        return gateway.single(GET_AFFILIATION_BY_DOCUMENT, new MapSqlParameterSource()
                .addValue("p_document_type", tipoDoc, Types.VARCHAR)
                .addValue("p_document_number", numDoc, Types.VARCHAR), "o_row");
    }

    public void updateAffiliation(
            Long id, Long epsId, String regimen, String tipoAfiliado, String estado, Instant verificacionAt) {
        gateway.execute(UPDATE_AFFILIATION, new MapSqlParameterSource()
                .addValue("p_id", id)
                .addValue("p_eps_id", epsId)
                .addValue("p_regime", regimen)
                .addValue("p_affiliate_type", tipoAfiliado)
                .addValue("p_status", estado)
                .addValue("p_last_verification_at",
                        Timestamp.from(verificacionAt != null ? verificacionAt : Instant.now())));
    }

    public void linkPatient(String tipoDoc, String numDoc, Long pacienteId) {
        gateway.execute(LINK_PATIENT_AFFILIATION, new MapSqlParameterSource()
                .addValue("p_document_type", tipoDoc, Types.VARCHAR)
                .addValue("p_document_number", numDoc, Types.VARCHAR)
                .addValue("p_patient_id", pacienteId, Types.NUMERIC));
    }

    public Optional<AffiliationResponse> findDetailByDocument(String tipoDoc, String numDoc) {
        return gateway.single(GET_AFFILIATION_DETAIL_BY_DOCUMENT, new MapSqlParameterSource()
                .addValue("p_document_type", tipoDoc, Types.VARCHAR)
                .addValue("p_document_number", numDoc, Types.VARCHAR), "o_row");
    }

    public Optional<AffiliationResponse> findDetailByPatientId(Long pacienteId) {
        return gateway.single(GET_AFFILIATION_DETAIL_BY_PATIENT_ID,
                new MapSqlParameterSource().addValue("p_patient_id", pacienteId, Types.NUMERIC), "o_row");
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
