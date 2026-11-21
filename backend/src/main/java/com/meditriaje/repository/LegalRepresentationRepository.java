package com.meditriaje.repository;

import com.meditriaje.dto.affiliation.LegalRepresentationResponse;
import com.meditriaje.model.LegalRepresentation;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Repositorio de {@code REPRESENTACION_LEGAL} a través de {@code PKG_PATIENTS}. No contiene SQL.
 */
@Repository
public class LegalRepresentationRepository {

    static final RowMapper<LegalRepresentation> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("CREADO_AT");
        return new LegalRepresentation(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("PACIENTE_MENOR_ID"),
                rs.getLong("REPRESENTANTE_PACIENTE_ID"),
                rs.getString("PARENTESCO"),
                rs.getString("DOCUMENTO_SOPORTE"),
                rs.getInt("VERIFICADO") == 1,
                ts != null ? ts.toInstant() : null);
    };

    static final RowMapper<LegalRepresentationResponse> RESPONSE_MAPPER = (rs, rowNum) ->
            new LegalRepresentationResponse(
                    rs.getString("PUBLIC_ID"),
                    rs.getString("MENOR_PUB_ID"),
                    (rs.getString("MENOR_NOMBRE") + " " + rs.getString("MENOR_APELLIDO")),
                    rs.getString("MENOR_DOC"),
                    rs.getString("REP_PUB_ID"),
                    (rs.getString("REP_NOMBRE") + " " + rs.getString("REP_APELLIDO")),
                    rs.getString("REP_DOC"),
                    rs.getString("PARENTESCO"),
                    rs.getInt("VERIFICADO") == 1);

    static final ProcedureSpec CREATE_LEGAL_REPRESENTATION = ProcedureSpec.procedure(
                    PatientRepository.PACKAGE, "create_legal_representation")
            .in("p_public_id", Types.VARCHAR)
            .in("p_minor_patient_id", Types.NUMERIC)
            .in("p_representative_patient_id", Types.NUMERIC)
            .in("p_relationship", Types.VARCHAR)
            .in("p_supporting_document", Types.VARCHAR)
            .in("p_verified", Types.NUMERIC)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_LEGAL_REPRESENTATION_BY_ID = ProcedureSpec.procedure(
                    PatientRepository.PACKAGE, "get_legal_representation_by_id")
            .in("p_id", Types.NUMERIC)
            .cursor("o_row", ROW_MAPPER)
            .build();

    static final ProcedureSpec LIST_BY_REPRESENTATIVE = ProcedureSpec.procedure(
                    PatientRepository.PACKAGE, "list_legal_representations_by_representative")
            .in("p_representative_patient_id", Types.NUMERIC)
            .cursor("o_rows", RESPONSE_MAPPER)
            .build();

    private final PackageGateway gateway;

    public LegalRepresentationRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public LegalRepresentation save(LegalRepresentation r) {
        Map<String, Object> out = gateway.execute(CREATE_LEGAL_REPRESENTATION, new MapSqlParameterSource()
                .addValue("p_public_id", r.publicId())
                .addValue("p_minor_patient_id", Objects.requireNonNull(r.minorPatientId()))
                .addValue("p_representative_patient_id", Objects.requireNonNull(r.representativePatientId()))
                .addValue("p_relationship", r.relationship())
                .addValue("p_supporting_document", r.supportingDocument(), Types.VARCHAR)
                .addValue("p_verified", r.verified() ? 1 : 0));
        Number key = (Number) out.get("o_id");
        Long id = key != null ? key.longValue() : null;
        return findById(id).orElseThrow(() -> new IllegalStateException("Error al recuperar representación legal"));
    }

    public Optional<LegalRepresentation> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return gateway.single(GET_LEGAL_REPRESENTATION_BY_ID, new MapSqlParameterSource("p_id", id), "o_row");
    }

    public List<LegalRepresentationResponse> listByRepresentativeId(Long representanteId) {
        return gateway.list(LIST_BY_REPRESENTATIVE,
                new MapSqlParameterSource("p_representative_patient_id", representanteId), "o_rows");
    }
}
