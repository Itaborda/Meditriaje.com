package com.meditriaje.repository;

import com.meditriaje.dto.admin.ProfesionalResponse;
import com.meditriaje.model.Profesional;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio para la entidad {@code PROFESIONAL} (ADR-001, ADR-003, ADR-006, HU-10, Ley 1164/2007). No
 * contiene SQL: delega en {@code PKG_STAFF} a través de {@link PackageGateway}.
 */
@Repository
public class ProfesionalRepository {

    static final String PACKAGE = "PKG_STAFF";

    static final RowMapper<Profesional> PROFESIONAL_MAPPER = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        Timestamp tsUpdated = rs.getTimestamp("UPDATED_AT");
        return new Profesional(
                rs.getLong("ID"),
                rs.getLong("USUARIO_ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("ESPECIALIDAD_ID"),
                rs.getString("TIPO_DOCUMENTO"),
                rs.getString("NUMERO_DOCUMENTO"),
                rs.getString("REGISTRO_MEDICO"),
                rs.getString("NOMBRES"),
                rs.getString("APELLIDOS"),
                rs.getString("TELEFONO"),
                tsCreated != null ? tsCreated.toInstant() : null,
                tsUpdated != null ? tsUpdated.toInstant() : null
        );
    };

    static final RowMapper<ProfesionalResponse> RESPONSE_MAPPER = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        return new ProfesionalResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("USUARIO_PUBLIC_ID"),
                rs.getString("TIPO_DOCUMENTO"),
                rs.getString("NUMERO_DOCUMENTO"),
                rs.getString("REGISTRO_MEDICO"),
                rs.getString("NOMBRES"),
                rs.getString("APELLIDOS"),
                rs.getString("EMAIL"),
                rs.getString("TELEFONO"),
                rs.getString("ESPECIALIDAD_PUBLIC_ID"),
                rs.getString("ESPECIALIDAD_NOMBRE"),
                rs.getString("ESTADO"),
                rs.getInt("DEBE_CAMBIAR_PASSWORD") == 1,
                tsCreated != null ? tsCreated.toInstant() : null
        );
    };

    static final ProcedureSpec CREATE_PROFESSIONAL = ProcedureSpec.procedure(PACKAGE, "create_professional")
            .in("p_user_id", Types.NUMERIC)
            .in("p_public_id", Types.VARCHAR)
            .in("p_specialty_id", Types.NUMERIC)
            .in("p_document_type", Types.VARCHAR)
            .in("p_document_number", Types.VARCHAR)
            .in("p_medical_registry", Types.VARCHAR)
            .in("p_first_names", Types.VARCHAR)
            .in("p_last_names", Types.VARCHAR)
            .in("p_phone", Types.VARCHAR)
            .out("o_professional_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec UPDATE_PROFESSIONAL = ProcedureSpec.procedure(PACKAGE, "update_professional")
            .in("p_public_id", Types.VARCHAR)
            .in("p_specialty_id", Types.NUMERIC)
            .in("p_first_names", Types.VARCHAR)
            .in("p_last_names", Types.VARCHAR)
            .in("p_phone", Types.VARCHAR)
            .build();

    static final ProcedureSpec GET_PROFESSIONAL_BY_PUBLIC_ID =
            ProcedureSpec.procedure(PACKAGE, "get_professional_by_public_id")
                    .in("p_public_id", Types.VARCHAR)
                    .cursor("o_professional", PROFESIONAL_MAPPER)
                    .build();

    static final ProcedureSpec GET_PROFESSIONAL_BY_ID = ProcedureSpec.procedure(PACKAGE, "get_professional_by_id")
            .in("p_professional_id", Types.NUMERIC)
            .cursor("o_professional", PROFESIONAL_MAPPER)
            .build();

    static final ProcedureSpec GET_PROFESSIONAL_BY_USER_ID =
            ProcedureSpec.procedure(PACKAGE, "get_professional_by_user_id")
                    .in("p_user_id", Types.NUMERIC)
                    .cursor("o_professional", PROFESIONAL_MAPPER)
                    .build();

    static final ProcedureSpec COUNT_BY_MEDICAL_REGISTRY =
            ProcedureSpec.function(PACKAGE, "count_by_medical_registry", "result", Types.NUMERIC)
                    .in("p_medical_registry", Types.VARCHAR)
                    .build();

    static final ProcedureSpec COUNT_OTHER_BY_MEDICAL_REGISTRY =
            ProcedureSpec.function(PACKAGE, "count_other_by_medical_registry", "result", Types.NUMERIC)
                    .in("p_medical_registry", Types.VARCHAR)
                    .in("p_public_id", Types.VARCHAR)
                    .build();

    static final ProcedureSpec COUNT_BY_DOCUMENT =
            ProcedureSpec.function(PACKAGE, "count_by_document", "result", Types.NUMERIC)
                    .in("p_document_type", Types.VARCHAR)
                    .in("p_document_number", Types.VARCHAR)
                    .build();

    static final ProcedureSpec COUNT_OTHER_BY_DOCUMENT =
            ProcedureSpec.function(PACKAGE, "count_other_by_document", "result", Types.NUMERIC)
                    .in("p_document_type", Types.VARCHAR)
                    .in("p_document_number", Types.VARCHAR)
                    .in("p_public_id", Types.VARCHAR)
                    .build();

    static final ProcedureSpec LIST_PROFESSIONALS = ProcedureSpec.procedure(PACKAGE, "list_professionals")
            .in("p_specialty_public_id", Types.VARCHAR)
            .in("p_status", Types.VARCHAR)
            .in("p_offset", Types.NUMERIC)
            .in("p_size", Types.NUMERIC)
            .cursor("o_rows", RESPONSE_MAPPER)
            .build();

    static final ProcedureSpec COUNT_PROFESSIONALS =
            ProcedureSpec.function(PACKAGE, "count_professionals", "result", Types.NUMERIC)
                    .in("p_specialty_public_id", Types.VARCHAR)
                    .in("p_status", Types.VARCHAR)
                    .build();

    private final PackageGateway gateway;

    public ProfesionalRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public Long crear(Profesional profesional) {
        Map<String, Object> out = gateway.execute(CREATE_PROFESSIONAL, new MapSqlParameterSource()
                .addValue("p_user_id", profesional.usuarioId(), Types.NUMERIC)
                .addValue("p_public_id", profesional.publicId(), Types.VARCHAR)
                .addValue("p_specialty_id", profesional.especialidadId(), Types.NUMERIC)
                .addValue("p_document_type", profesional.tipoDocumento() != null ? profesional.tipoDocumento() : "CC", Types.VARCHAR)
                .addValue("p_document_number", profesional.numeroDocumento(), Types.VARCHAR)
                .addValue("p_medical_registry", profesional.registroMedico(), Types.VARCHAR)
                .addValue("p_first_names", profesional.nombres(), Types.VARCHAR)
                .addValue("p_last_names", profesional.apellidos(), Types.VARCHAR)
                .addValue("p_phone", profesional.telefono(), Types.VARCHAR));
        Number key = (Number) out.get("o_professional_id");
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para el profesional.");
        }
        return key.longValue();
    }

    public void actualizar(Profesional profesional) {
        gateway.execute(UPDATE_PROFESSIONAL, new MapSqlParameterSource()
                .addValue("p_public_id", profesional.publicId(), Types.VARCHAR)
                .addValue("p_specialty_id", profesional.especialidadId(), Types.NUMERIC)
                .addValue("p_first_names", profesional.nombres(), Types.VARCHAR)
                .addValue("p_last_names", profesional.apellidos(), Types.VARCHAR)
                .addValue("p_phone", profesional.telefono(), Types.VARCHAR));
    }

    public Optional<Profesional> buscarPorPublicId(String publicId) {
        return first(GET_PROFESSIONAL_BY_PUBLIC_ID, new MapSqlParameterSource().addValue("p_public_id", publicId, Types.VARCHAR));
    }

    public Optional<Profesional> buscarPorId(Long id) {
        return first(GET_PROFESSIONAL_BY_ID, new MapSqlParameterSource().addValue("p_professional_id", id, Types.NUMERIC));
    }

    public Optional<Profesional> buscarPorUsuarioId(Long usuarioId) {
        return first(GET_PROFESSIONAL_BY_USER_ID, new MapSqlParameterSource().addValue("p_user_id", usuarioId, Types.NUMERIC));
    }

    public boolean existePorRegistroMedico(String registroMedico) {
        return positive(gateway.callFunction(
                COUNT_BY_MEDICAL_REGISTRY,
                new MapSqlParameterSource().addValue("p_medical_registry", registroMedico, Types.VARCHAR)));
    }

    public boolean existePorRegistroMedicoYNoPublicId(String registroMedico, String publicId) {
        return positive(gateway.callFunction(COUNT_OTHER_BY_MEDICAL_REGISTRY, new MapSqlParameterSource()
                .addValue("p_medical_registry", registroMedico, Types.VARCHAR)
                .addValue("p_public_id", publicId, Types.VARCHAR)));
    }

    public boolean existePorDocumento(String tipoDocumento, String numeroDocumento) {
        if (tipoDocumento == null || numeroDocumento == null) return false;
        return positive(gateway.callFunction(COUNT_BY_DOCUMENT, new MapSqlParameterSource()
                .addValue("p_document_type", tipoDocumento, Types.VARCHAR)
                .addValue("p_document_number", numeroDocumento, Types.VARCHAR)));
    }

    public boolean existePorDocumentoYNoPublicId(String tipoDocumento, String numeroDocumento, String publicId) {
        if (tipoDocumento == null || numeroDocumento == null) return false;
        return positive(gateway.callFunction(COUNT_OTHER_BY_DOCUMENT, new MapSqlParameterSource()
                .addValue("p_document_type", tipoDocumento, Types.VARCHAR)
                .addValue("p_document_number", numeroDocumento, Types.VARCHAR)
                .addValue("p_public_id", publicId, Types.VARCHAR)));
    }

    public List<ProfesionalResponse> listar(int page, int size, String especialidadPublicId, String estado) {
        int offset = Math.max(0, page) * Math.max(1, size);
        int limit = Math.max(1, size);
        MapSqlParameterSource params = filters(especialidadPublicId, estado)
                .addValue("p_offset", offset, Types.NUMERIC)
                .addValue("p_size", limit, Types.NUMERIC);
        return gateway.list(LIST_PROFESSIONALS, params, "o_rows");
    }

    public int contar(String especialidadPublicId, String estado) {
        Number count = gateway.callFunction(COUNT_PROFESSIONALS, filters(especialidadPublicId, estado));
        return count != null ? count.intValue() : 0;
    }

    /** Filtros opcionales: en blanco no filtra; la especialidad se recorta y el estado se pasa a mayúsculas. */
    private static MapSqlParameterSource filters(String especialidadPublicId, String estado) {
        return new MapSqlParameterSource()
                .addValue("p_specialty_public_id",
                        especialidadPublicId != null && !especialidadPublicId.isBlank() ? especialidadPublicId.trim() : null,
                        Types.VARCHAR)
                .addValue("p_status",
                        estado != null && !estado.isBlank() ? estado.trim().toUpperCase() : null,
                        Types.VARCHAR);
    }

    private Optional<Profesional> first(ProcedureSpec spec, MapSqlParameterSource params) {
        List<Profesional> resultados = gateway.list(spec, params, "o_professional");
        return resultados.stream().findFirst();
    }

    private static boolean positive(Object count) {
        return count != null && ((Number) count).longValue() > 0;
    }
}
