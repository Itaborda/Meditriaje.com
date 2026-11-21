package com.meditriaje.repository;

import com.meditriaje.dto.allergy.AlergiaResponse;
import com.meditriaje.model.Alergia;
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
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Repositorio de la entidad {@code ALERGIA} (ADR-008, V008, V016, T4) a través de {@code PKG_PATIENTS}. No
 * contiene SQL: declara las rutinas del paquete y delega la llamada en {@link PackageGateway}.
 */
@Repository
public class AlergiaRepository {

    static final RowMapper<Alergia> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        Timestamp tsUpdated = rs.getTimestamp("UPDATED_AT");
        Timestamp tsInact = rs.getTimestamp("FECHA_INACTIVACION");
        Long regPor = getNullableLong(rs, "REGISTRADA_POR_USUARIO_ID");
        Long inactPor = getNullableLong(rs, "INACTIVADA_POR_USUARIO_ID");
        Long atencionId = getNullableLong(rs, "ATENCION_ID");

        return new Alergia(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("PACIENTE_ID"),
                rs.getString("SUSTANCIA"),
                rs.getString("REACCION"),
                rs.getString("SEVERIDAD"),
                rs.getString("ESTADO"),
                rs.getString("ORIGEN"),
                regPor,
                atencionId,
                tsInact != null ? tsInact.toInstant() : null,
                inactPor,
                rs.getString("MOTIVO_INACTIVACION"),
                tsCreated != null ? tsCreated.toInstant() : null,
                tsUpdated != null ? tsUpdated.toInstant() : null);
    };

    static final RowMapper<AlergiaResponse> RESPONSE_MAPPER = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        Timestamp tsInact = rs.getTimestamp("FECHA_INACTIVACION");
        String origen = rs.getString("ORIGEN");

        return new AlergiaResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("PACIENTE_PUBLIC_ID"),
                rs.getString("SUSTANCIA"),
                rs.getString("REACCION"),
                rs.getString("SEVERIDAD"),
                rs.getString("ESTADO"),
                origen,
                "PACIENTE".equalsIgnoreCase(origen),
                rs.getString("ATENCION_PUBLIC_ID"),
                tsCreated != null ? tsCreated.toInstant() : null,
                tsInact != null ? tsInact.toInstant() : null,
                rs.getString("MOTIVO_INACTIVACION"));
    };

    static final ProcedureSpec LIST_BY_PATIENT_ID = ProcedureSpec.procedure(
                    PacienteRepository.PACKAGE, "list_allergies_by_patient_id")
            .in("p_patient_id", Types.NUMERIC)
            .in("p_only_active", Types.NUMERIC)
            .cursor("o_rows", ROW_MAPPER)
            .build();

    static final ProcedureSpec LIST_BY_PATIENT_PUBLIC_ID = ProcedureSpec.procedure(
                    PacienteRepository.PACKAGE, "list_allergies_by_patient_public_id")
            .in("p_patient_public_id", Types.VARCHAR)
            .in("p_include_inactive", Types.NUMERIC)
            .cursor("o_rows", RESPONSE_MAPPER)
            .build();

    static final ProcedureSpec GET_BY_PUBLIC_ID = ProcedureSpec.procedure(
                    PacienteRepository.PACKAGE, "get_allergy_by_public_id")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_row", ROW_MAPPER)
            .build();

    static final ProcedureSpec GET_VIEW_BY_PUBLIC_ID = ProcedureSpec.procedure(
                    PacienteRepository.PACKAGE, "get_allergy_view_by_public_id")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_row", RESPONSE_MAPPER)
            .build();

    static final ProcedureSpec COUNT_ACTIVE_BY_SUBSTANCE = ProcedureSpec.function(
                    PacienteRepository.PACKAGE, "count_active_allergies_by_substance", "result", Types.NUMERIC)
            .in("p_patient_id", Types.NUMERIC)
            .in("p_substance", Types.VARCHAR)
            .build();

    static final ProcedureSpec CREATE_ALLERGY = ProcedureSpec.procedure(PacienteRepository.PACKAGE, "create_allergy")
            .in("p_public_id", Types.VARCHAR)
            .in("p_patient_id", Types.NUMERIC)
            .in("p_substance", Types.VARCHAR)
            .in("p_reaction", Types.VARCHAR)
            .in("p_severity", Types.VARCHAR)
            .in("p_status", Types.VARCHAR)
            .in("p_origin", Types.VARCHAR)
            .in("p_registered_by_user_id", Types.NUMERIC)
            .in("p_encounter_id", Types.NUMERIC)
            .in("p_created_at", Types.TIMESTAMP)
            .in("p_updated_at", Types.TIMESTAMP)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec INACTIVATE_ALLERGY = ProcedureSpec.procedure(
                    PacienteRepository.PACKAGE, "inactivate_allergy")
            .in("p_id", Types.NUMERIC)
            .in("p_inactivated_by_user_id", Types.NUMERIC)
            .in("p_reason", Types.VARCHAR)
            .in("p_inactivated_at", Types.TIMESTAMP)
            .out("o_affected", Types.NUMERIC)
            .build();

    private final PackageGateway gateway;

    public AlergiaRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    /** Lista alergias de un paciente por su ID numérico interno (solo activas por defecto). */
    public List<Alergia> listarPorPacienteId(Long pacienteId) {
        return listarPorPacienteId(pacienteId, true);
    }

    /** Lista alergias de un paciente por su ID numérico interno. */
    public List<Alergia> listarPorPacienteId(Long pacienteId, boolean soloActivas) {
        if (pacienteId == null) {
            return List.of();
        }
        return gateway.list(LIST_BY_PATIENT_ID, new MapSqlParameterSource()
                .addValue("p_patient_id", pacienteId)
                .addValue("p_only_active", soloActivas ? 1 : 0), "o_rows");
    }

    /** Lista alergias en formato DTO para un paciente identificado por su UUID público. */
    public List<AlergiaResponse> listarPorPacientePublicId(String pacientePublicId, boolean incluirInactivas) {
        if (pacientePublicId == null || pacientePublicId.isBlank()) {
            return List.of();
        }
        return gateway.list(LIST_BY_PATIENT_PUBLIC_ID, new MapSqlParameterSource()
                .addValue("p_patient_public_id", pacientePublicId)
                .addValue("p_include_inactive", incluirInactivas ? 1 : 0), "o_rows");
    }

    /** Busca la entidad Alergia por su UUID público. */
    public Optional<Alergia> buscarEntidadPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        return gateway.single(GET_BY_PUBLIC_ID, new MapSqlParameterSource("p_public_id", publicId), "o_row");
    }

    /** Busca la alergia y retorna su DTO AlergiaResponse. */
    public Optional<AlergiaResponse> buscarPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        return gateway.single(GET_VIEW_BY_PUBLIC_ID, new MapSqlParameterSource("p_public_id", publicId), "o_row");
    }

    /** Verifica si existe una alergia ACTIVA para el paciente y la sustancia dada. */
    public boolean existeActivaPorSustancia(Long pacienteId, String sustancia) {
        if (pacienteId == null || sustancia == null || sustancia.isBlank()) {
            return false;
        }
        Number count = gateway.callFunction(COUNT_ACTIVE_BY_SUBSTANCE, new MapSqlParameterSource()
                .addValue("p_patient_id", pacienteId)
                .addValue("p_substance", sustancia));
        return count != null && count.longValue() > 0;
    }

    /** Registra una nueva alergia para un paciente. */
    public Long crear(Alergia alergia) {
        Objects.requireNonNull(alergia, "alergia no puede ser nula");
        String publicId = alergia.publicId() != null && !alergia.publicId().isBlank()
                ? alergia.publicId()
                : UUID.randomUUID().toString();
        String estado = alergia.estado() != null ? alergia.estado() : "ACTIVA";
        String origen = alergia.origen() != null ? alergia.origen() : "PROFESIONAL";
        Instant createdAt = alergia.createdAt() != null ? alergia.createdAt() : Instant.now();
        Instant updatedAt = alergia.updatedAt() != null ? alergia.updatedAt() : createdAt;

        Map<String, Object> out = gateway.execute(CREATE_ALLERGY, new MapSqlParameterSource()
                .addValue("p_public_id", publicId)
                .addValue("p_patient_id", Objects.requireNonNull(alergia.pacienteId()))
                .addValue("p_substance", alergia.sustancia().trim())
                .addValue("p_reaction", alergia.reaccion() != null ? alergia.reaccion().trim() : null, Types.VARCHAR)
                .addValue("p_severity", alergia.severidad())
                .addValue("p_status", estado)
                .addValue("p_origin", origen)
                .addValue("p_registered_by_user_id", alergia.registradaPorUsuarioId(), Types.NUMERIC)
                .addValue("p_encounter_id", alergia.atencionId(), Types.NUMERIC)
                .addValue("p_created_at", Timestamp.from(createdAt))
                .addValue("p_updated_at", Timestamp.from(updatedAt)));

        Number key = (Number) out.get("o_id");
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para ALERGIA.");
        }
        return key.longValue();
    }

    /** Inactiva una alergia activa estableciendo su motivo, usuario y fecha de inactivación. */
    public int inactivar(Long alergiaId, Long inactivadaPorUsuarioId, String motivo, Instant fechaInactivacion) {
        Objects.requireNonNull(alergiaId, "alergiaId no puede ser nulo");
        Objects.requireNonNull(inactivadaPorUsuarioId, "inactivadaPorUsuarioId no puede ser nulo");
        Objects.requireNonNull(motivo, "motivo no puede ser nulo");

        Instant fecha = fechaInactivacion != null ? fechaInactivacion : Instant.now();

        Map<String, Object> out = gateway.execute(INACTIVATE_ALLERGY, new MapSqlParameterSource()
                .addValue("p_id", alergiaId)
                .addValue("p_inactivated_by_user_id", inactivadaPorUsuarioId)
                .addValue("p_reason", motivo.trim())
                .addValue("p_inactivated_at", Timestamp.from(fecha)));
        return ((Number) out.get("o_affected")).intValue();
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
