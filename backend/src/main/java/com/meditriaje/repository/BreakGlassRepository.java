package com.meditriaje.repository;

import com.meditriaje.dto.clinical.BreakGlassAccessResponse;
import com.meditriaje.model.BreakGlassAccess;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio para registros de acceso de emergencia Break-Glass (ADR-017). Inserciones inmutables mediante
 * {@code PKG_AUDIT}; no contiene SQL.
 */
@Repository
public class BreakGlassRepository {

    static final String PACKAGE = "PKG_AUDIT";

    static final RowMapper<BreakGlassAccess> ENTITY_MAPPER = (rs, rowNum) -> {
        Timestamp expTs = rs.getTimestamp("FECHA_EXPIRACION");
        Timestamp crTs = rs.getTimestamp("CREATED_AT");
        return new BreakGlassAccess(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("PROFESIONAL_ID"),
                rs.getLong("PACIENTE_ID"),
                rs.getString("MOTIVO"),
                expTs != null ? expTs.toInstant() : null,
                crTs != null ? crTs.toInstant() : null
        );
    };

    /** La columna ACTIVO la calcula el paquete comparando la expiración con el instante recibido. */
    static final RowMapper<BreakGlassAccessResponse> RESPONSE_MAPPER = (rs, rowNum) -> {
        Timestamp expTs = rs.getTimestamp("FECHA_EXPIRACION");
        Timestamp crTs = rs.getTimestamp("CREATED_AT");
        return new BreakGlassAccessResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("PROF_PUB_ID"),
                rs.getString("PROF_NOMBRE") != null ? rs.getString("PROF_NOMBRE").trim() : "",
                rs.getString("PAC_PUB_ID"),
                rs.getString("PAC_NOMBRE") != null ? rs.getString("PAC_NOMBRE").trim() : "",
                rs.getString("MOTIVO"),
                expTs != null ? expTs.toInstant() : null,
                crTs != null ? crTs.toInstant() : null,
                rs.getInt("ACTIVO") == 1
        );
    };

    static final ProcedureSpec CREATE_BREAK_GLASS_ACCESS =
            ProcedureSpec.procedure(PACKAGE, "create_break_glass_access")
                    .in("p_public_id", Types.VARCHAR)
                    .in("p_professional_id", Types.NUMERIC)
                    .in("p_patient_id", Types.NUMERIC)
                    .in("p_reason", Types.VARCHAR)
                    .in("p_expires_at", Types.TIMESTAMP)
                    .in("p_created_at", Types.TIMESTAMP)
                    .out("o_access_id", Types.NUMERIC)
                    .build();

    static final ProcedureSpec COUNT_ACTIVE_BREAK_GLASS =
            ProcedureSpec.function(PACKAGE, "count_active_break_glass", "result", Types.NUMERIC)
                    .in("p_professional_id", Types.NUMERIC)
                    .in("p_patient_id", Types.NUMERIC)
                    .in("p_now", Types.TIMESTAMP)
                    .build();

    static final ProcedureSpec GET_BREAK_GLASS_BY_PUBLIC_ID =
            ProcedureSpec.procedure(PACKAGE, "get_break_glass_by_public_id")
                    .in("p_public_id", Types.VARCHAR)
                    .cursor("o_access", ENTITY_MAPPER)
                    .build();

    static final ProcedureSpec GET_BREAK_GLASS_DETAIL = ProcedureSpec.procedure(PACKAGE, "get_break_glass_detail")
            .in("p_public_id", Types.VARCHAR)
            .in("p_now", Types.TIMESTAMP)
            .cursor("o_access", RESPONSE_MAPPER)
            .build();

    static final ProcedureSpec LIST_ACTIVE_BREAK_GLASS = ProcedureSpec.procedure(PACKAGE, "list_active_break_glass")
            .in("p_professional_id", Types.NUMERIC)
            .in("p_now", Types.TIMESTAMP)
            .cursor("o_rows", RESPONSE_MAPPER)
            .build();

    private final PackageGateway gateway;

    public BreakGlassRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    /**
     * Registra un nuevo evento de acceso excepcional Break-Glass.
     *
     * @param acceso Entidad inmutable con los datos del acceso
     * @return Identificador numérico generado en base de datos
     */
    public Long recordAccess(BreakGlassAccess acceso) {
        Objects.requireNonNull(acceso, "acceso no puede ser nulo");
        Map<String, Object> out = gateway.execute(CREATE_BREAK_GLASS_ACCESS, new MapSqlParameterSource()
                .addValue("p_public_id", acceso.publicId())
                .addValue("p_professional_id", acceso.professionalId())
                .addValue("p_patient_id", acceso.patientId())
                .addValue("p_reason", acceso.reason())
                .addValue("p_expires_at", Timestamp.from(acceso.expirationDate()))
                .addValue("p_created_at",
                        Timestamp.from(acceso.createdAt() != null ? acceso.createdAt() : Instant.now())));
        Number key = (Number) out.get("o_access_id");
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID generado para ACCESO_BREAK_GLASS");
        }
        return key.longValue();
    }

    /**
     * Verifica si existe una autorización Break-Glass activa y no expirada entre el profesional y el paciente.
     */
    public boolean existsActiveAccess(Long profesionalId, Long pacienteId, Instant ahora) {
        if (profesionalId == null || pacienteId == null || ahora == null) {
            return false;
        }
        Number count = gateway.callFunction(COUNT_ACTIVE_BREAK_GLASS, new MapSqlParameterSource()
                .addValue("p_professional_id", profesionalId)
                .addValue("p_patient_id", pacienteId)
                .addValue("p_now", Timestamp.from(ahora)));
        return count != null && count.longValue() > 0;
    }

    /**
     * Recupera la entidad por su identificador público UUID.
     */
    public Optional<BreakGlassAccess> findEntityByPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        return gateway.single(
                GET_BREAK_GLASS_BY_PUBLIC_ID, new MapSqlParameterSource("p_public_id", publicId), "o_access");
    }

    /**
     * Recupera el DTO de respuesta con nombres de profesional y paciente.
     */
    public Optional<BreakGlassAccessResponse> findByPublicId(String publicId, Instant ahora) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        return gateway.single(GET_BREAK_GLASS_DETAIL, new MapSqlParameterSource()
                .addValue("p_public_id", publicId, Types.VARCHAR)
                .addValue("p_now", ahora != null ? Timestamp.from(ahora) : null, Types.TIMESTAMP), "o_access");
    }

    /**
     * Lista las autorizaciones Break-Glass actualmente activas (no expiradas) para un profesional.
     */
    public List<BreakGlassAccessResponse> listActiveByProfessional(Long profesionalId, Instant ahora) {
        if (profesionalId == null || ahora == null) {
            return List.of();
        }
        return gateway.list(LIST_ACTIVE_BREAK_GLASS, new MapSqlParameterSource()
                .addValue("p_professional_id", profesionalId)
                .addValue("p_now", Timestamp.from(ahora)), "o_rows");
    }
}
