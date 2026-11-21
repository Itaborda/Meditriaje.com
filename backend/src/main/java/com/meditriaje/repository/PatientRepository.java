package com.meditriaje.repository;

import com.meditriaje.dto.PatientProfileResponse;
import com.meditriaje.model.Patient;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Repositorio de la entidad {@code PACIENTE} a través de {@code PKG_PATIENTS}. No contiene SQL: declara las
 * rutinas del paquete y delega la llamada en {@link PackageGateway}.
 */
@Repository
public class PatientRepository {

    static final String PACKAGE = "PKG_PATIENTS";

    static final RowMapper<Patient> PATIENT_MAPPER = (rs, rowNum) -> {
        Date fNac = rs.getDate("FECHA_NACIMIENTO");
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        Timestamp tsUpdated = rs.getTimestamp("UPDATED_AT");
        return new Patient(
                rs.getLong("ID"),
                rs.getLong("USUARIO_ID"),
                rs.getString("PUBLIC_ID"),
                rs.getString("TIPO_DOCUMENTO"),
                rs.getString("NUMERO_DOCUMENTO"),
                rs.getString("NOMBRES"),
                rs.getString("APELLIDOS"),
                fNac != null ? fNac.toLocalDate() : null,
                rs.getString("TELEFONO"),
                tsCreated != null ? tsCreated.toInstant() : null,
                tsUpdated != null ? tsUpdated.toInstant() : null);
    };

    static final RowMapper<PatientProfileResponse> PROFILE_MAPPER = (rs, rowNum) -> {
        Date fNac = rs.getDate("FECHA_NACIMIENTO");
        return new PatientProfileResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("TIPO_DOCUMENTO"),
                rs.getString("NUMERO_DOCUMENTO"),
                rs.getString("NOMBRES"),
                rs.getString("APELLIDOS"),
                fNac != null ? fNac.toLocalDate() : null,
                rs.getString("TELEFONO"),
                rs.getString("EMAIL"));
    };

    static final ProcedureSpec COUNT_PATIENTS_BY_DOCUMENT = ProcedureSpec.function(
                    PACKAGE, "count_patients_by_document", "result", Types.NUMERIC)
            .in("p_document_type", Types.VARCHAR)
            .in("p_document_number", Types.VARCHAR)
            .build();

    static final ProcedureSpec CREATE_PATIENT = ProcedureSpec.procedure(PACKAGE, "create_patient")
            .in("p_user_id", Types.NUMERIC)
            .in("p_public_id", Types.VARCHAR)
            .in("p_document_type", Types.VARCHAR)
            .in("p_document_number", Types.VARCHAR)
            .in("p_first_names", Types.VARCHAR)
            .in("p_last_names", Types.VARCHAR)
            .in("p_birth_date", Types.DATE)
            .in("p_phone", Types.VARCHAR)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_PATIENT_PROFILE_BY_USER_PUBLIC_ID = ProcedureSpec.procedure(
                    PACKAGE, "get_patient_profile_by_user_public_id")
            .in("p_user_public_id", Types.VARCHAR)
            .cursor("o_row", PROFILE_MAPPER)
            .build();

    static final ProcedureSpec GET_PATIENT_BY_USER_ID = ProcedureSpec.procedure(PACKAGE, "get_patient_by_user_id")
            .in("p_user_id", Types.NUMERIC)
            .cursor("o_row", PATIENT_MAPPER)
            .build();

    static final ProcedureSpec GET_PATIENT_BY_ID = ProcedureSpec.procedure(PACKAGE, "get_patient_by_id")
            .in("p_id", Types.NUMERIC)
            .cursor("o_row", PATIENT_MAPPER)
            .build();

    static final ProcedureSpec GET_PATIENT_BY_PUBLIC_ID = ProcedureSpec.procedure(
                    PACKAGE, "get_patient_by_public_id")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_row", PATIENT_MAPPER)
            .build();

    static final ProcedureSpec GET_PATIENT_BY_DOCUMENT = ProcedureSpec.procedure(PACKAGE, "get_patient_by_document")
            .in("p_document_type", Types.VARCHAR)
            .in("p_document_number", Types.VARCHAR)
            .cursor("o_row", PATIENT_MAPPER)
            .build();

    private final PackageGateway gateway;

    public PatientRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public boolean existsByDocument(String tipoDocumento, String numeroDocumento) {
        Number count = gateway.callFunction(COUNT_PATIENTS_BY_DOCUMENT, new MapSqlParameterSource()
                .addValue("p_document_type", tipoDocumento, Types.VARCHAR)
                .addValue("p_document_number", numeroDocumento, Types.VARCHAR));
        return count != null && count.longValue() > 0;
    }

    public Long create(
            Long usuarioId,
            String publicId,
            String tipoDocumento,
            String numeroDocumento,
            String nombres,
            String apellidos,
            LocalDate fechaNacimiento,
            String telefono) {
        Objects.requireNonNull(usuarioId, "usuarioId no puede ser nulo");
        Map<String, Object> out = gateway.execute(CREATE_PATIENT, new MapSqlParameterSource()
                .addValue("p_user_id", usuarioId)
                .addValue("p_public_id", publicId)
                .addValue("p_document_type", tipoDocumento)
                .addValue("p_document_number", numeroDocumento)
                .addValue("p_first_names", nombres)
                .addValue("p_last_names", apellidos)
                .addValue("p_birth_date", Date.valueOf(fechaNacimiento))
                .addValue("p_phone", telefono, Types.VARCHAR));
        Number key = (Number) out.get("o_id");
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para el paciente.");
        }
        return key.longValue();
    }

    public Optional<PatientProfileResponse> findProfileByUserPublicId(String usuarioPublicId) {
        return gateway.single(GET_PATIENT_PROFILE_BY_USER_PUBLIC_ID,
                new MapSqlParameterSource("p_user_public_id", usuarioPublicId), "o_row");
    }

    public Optional<Patient> findByUserId(Long usuarioId) {
        return gateway.single(GET_PATIENT_BY_USER_ID, new MapSqlParameterSource("p_user_id", usuarioId), "o_row");
    }

    public Optional<Patient> findById(Long id) {
        return gateway.single(GET_PATIENT_BY_ID, new MapSqlParameterSource("p_id", id), "o_row");
    }

    public Optional<Patient> findByPublicId(String publicId) {
        return gateway.single(GET_PATIENT_BY_PUBLIC_ID, new MapSqlParameterSource("p_public_id", publicId), "o_row");
    }

    public Optional<Patient> findByDocument(String tipoDocumento, String numeroDocumento) {
        return gateway.single(GET_PATIENT_BY_DOCUMENT, new MapSqlParameterSource()
                .addValue("p_document_type", tipoDocumento)
                .addValue("p_document_number", numeroDocumento), "o_row");
    }
}
