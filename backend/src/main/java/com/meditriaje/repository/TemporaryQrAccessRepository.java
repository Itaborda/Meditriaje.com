package com.meditriaje.repository;

import com.meditriaje.model.TemporaryQrAccess;
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
 * Repositorio para la entidad {@code ACCESO_TEMPORAL_QR} (ADR-010, F2.3, SEC-002).
 * Maneja persistencia de tokens hasheados, control atómico de lecturas, intentos fallidos de PIN y revocación
 * delegando en {@code PKG_OPERATIONS} mediante {@link PackageGateway} sin SQL.
 */
@Repository
public class TemporaryQrAccessRepository {

    static final String PACKAGE = "PKG_OPERATIONS";

    static final RowMapper<TemporaryQrAccess> ENTITY_MAPPER = (rs, rowNum) -> {
        Timestamp tsExpira = rs.getTimestamp("EXPIRA_AT");
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        Timestamp tsUpdated = rs.getTimestamp("UPDATED_AT");

        return new TemporaryQrAccess(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("PACIENTE_ID"),
                rs.getString("TOKEN_HASH"),
                rs.getString("PIN_HASH"),
                rs.getInt("INCLUIR_ALERGIAS") == 1,
                rs.getInt("INCLUIR_MEDICAMENTOS") == 1,
                rs.getInt("INCLUIR_ATENCIONES") == 1,
                rs.getInt("INCLUIR_CONTACTO") == 1,
                rs.getInt("MAX_ACCESOS"),
                rs.getInt("ACCESOS_REALIZADOS"),
                rs.getInt("REVOCADO") == 1,
                rs.getInt("INTENTOS_PIN_FALLIDOS"),
                tsExpira != null ? tsExpira.toInstant() : null,
                tsCreated != null ? tsCreated.toInstant() : null,
                tsUpdated != null ? tsUpdated.toInstant() : null
        );
    };

    static final ProcedureSpec CREATE_QR_ACCESS = ProcedureSpec.procedure(PACKAGE, "create_qr_access")
            .in("p_public_id", Types.VARCHAR)
            .in("p_patient_id", Types.NUMERIC)
            .in("p_token_hash", Types.VARCHAR)
            .in("p_pin_hash", Types.VARCHAR)
            .in("p_include_allergies", Types.NUMERIC)
            .in("p_include_medications", Types.NUMERIC)
            .in("p_include_attentions", Types.NUMERIC)
            .in("p_include_contact", Types.NUMERIC)
            .in("p_max_accesses", Types.NUMERIC)
            .in("p_accesses_made", Types.NUMERIC)
            .in("p_revoked", Types.NUMERIC)
            .in("p_failed_pin_attempts", Types.NUMERIC)
            .in("p_expires_at", Types.TIMESTAMP)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_QR_ACCESS_BY_TOKEN_HASH =
            ProcedureSpec.procedure(PACKAGE, "get_qr_access_by_token_hash")
                    .in("p_token_hash", Types.VARCHAR)
                    .cursor("o_access", ENTITY_MAPPER)
                    .build();

    static final ProcedureSpec GET_QR_ACCESS_BY_PUBLIC_ID =
            ProcedureSpec.procedure(PACKAGE, "get_qr_access_by_public_id")
                    .in("p_public_id", Types.VARCHAR)
                    .cursor("o_access", ENTITY_MAPPER)
                    .build();

    static final ProcedureSpec LIST_QR_ACCESSES_BY_PATIENT =
            ProcedureSpec.procedure(PACKAGE, "list_qr_accesses_by_patient")
                    .in("p_patient_id", Types.NUMERIC)
                    .cursor("o_rows", ENTITY_MAPPER)
                    .build();

    static final ProcedureSpec RECORD_QR_ACCESS = ProcedureSpec.procedure(PACKAGE, "record_qr_access")
            .in("p_id", Types.NUMERIC)
            .in("p_now", Types.TIMESTAMP)
            .out("o_updated", Types.NUMERIC)
            .build();

    static final ProcedureSpec RECORD_FAILED_QR_PIN = ProcedureSpec.procedure(PACKAGE, "record_failed_qr_pin")
            .in("p_id", Types.NUMERIC)
            .out("o_updated", Types.NUMERIC)
            .build();

    static final ProcedureSpec REVOKE_QR_ACCESS = ProcedureSpec.procedure(PACKAGE, "revoke_qr_access")
            .in("p_public_id", Types.VARCHAR)
            .in("p_patient_id", Types.NUMERIC)
            .out("o_updated", Types.NUMERIC)
            .build();

    private final PackageGateway gateway;

    public TemporaryQrAccessRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    /**
     * Persiste un nuevo token de acceso temporal QR.
     *
     * @param acceso Entidad a persistir
     * @return ID numérico generado
     */
    public Long create(TemporaryQrAccess acceso) {
        Objects.requireNonNull(acceso, "acceso no puede ser nulo");
        Map<String, Object> out = gateway.execute(CREATE_QR_ACCESS, new MapSqlParameterSource()
                .addValue("p_public_id", acceso.publicId(), Types.VARCHAR)
                .addValue("p_patient_id", acceso.patientId(), Types.NUMERIC)
                .addValue("p_token_hash", acceso.tokenHash(), Types.VARCHAR)
                .addValue("p_pin_hash", acceso.pinHash(), Types.VARCHAR)
                .addValue("p_include_allergies", acceso.includeAllergies() ? 1 : 0, Types.NUMERIC)
                .addValue("p_include_medications", acceso.includeMedications() ? 1 : 0, Types.NUMERIC)
                .addValue("p_include_attentions", acceso.includeAttentions() ? 1 : 0, Types.NUMERIC)
                .addValue("p_include_contact", acceso.includeContact() ? 1 : 0, Types.NUMERIC)
                .addValue("p_max_accesses", acceso.maxAccesses(), Types.NUMERIC)
                .addValue("p_accesses_made", acceso.accessCount(), Types.NUMERIC)
                .addValue("p_revoked", acceso.revoked() ? 1 : 0, Types.NUMERIC)
                .addValue("p_failed_pin_attempts", acceso.failedPinAttempts(), Types.NUMERIC)
                .addValue("p_expires_at", Timestamp.from(acceso.expiresAt()), Types.TIMESTAMP));
        Number key = (Number) out.get("o_id");
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener la clave primaria generada para ACCESO_TEMPORAL_QR");
        }
        return key.longValue();
    }

    /**
     * Busca un acceso temporal por su token hash SHA-256.
     */
    public Optional<TemporaryQrAccess> findByTokenHash(String tokenHash) {
        return gateway.single(
                GET_QR_ACCESS_BY_TOKEN_HASH,
                new MapSqlParameterSource().addValue("p_token_hash", tokenHash, Types.VARCHAR),
                "o_access");
    }

    /**
     * Busca un acceso temporal por su UUID público.
     */
    public Optional<TemporaryQrAccess> findByPublicId(String publicId) {
        return gateway.single(
                GET_QR_ACCESS_BY_PUBLIC_ID,
                new MapSqlParameterSource().addValue("p_public_id", publicId, Types.VARCHAR),
                "o_access");
    }

    /**
     * Lista los accesos temporales generados por un paciente específico, ordenados cronológicamente descendente.
     */
    public List<TemporaryQrAccess> listByPatientId(Long pacienteId) {
        return gateway.list(
                LIST_QR_ACCESSES_BY_PATIENT,
                new MapSqlParameterSource().addValue("p_patient_id", pacienteId, Types.NUMERIC),
                "o_rows");
    }

    /**
     * Registra un acceso/lectura incrementando de manera atómica el contador {@code ACCESOS_REALIZADOS}.
     * Solo tiene éxito si el token no está revocado, no ha expirado y no ha alcanzado el límite máximo.
     *
     * @param id Identificador numérico del acceso
     * @param now Instante actual de lectura
     * @return 1 si se incrementó exitosamente, 0 si ya no era válido
     */
    public int recordAccess(Long id, Instant now) {
        Map<String, Object> out = gateway.execute(RECORD_QR_ACCESS, new MapSqlParameterSource()
                .addValue("p_id", id, Types.NUMERIC)
                .addValue("p_now", now != null ? Timestamp.from(now) : null, Types.TIMESTAMP));
        return updatedCount(out);
    }

    /**
     * Registra un intento fallido de validación de PIN (SEC-002):
     * - Incrementa INTENTOS_PIN_FALLIDOS.
     * - Los intentos fallidos cuentan para el máximo de accesos (incrementa ACCESOS_REALIZADOS).
     * - Si alcanza 3 intentos fallidos, revoca de inmediato el token (REVOCADO = 1).
     *
     * @param id Identificador numérico del acceso
     * @return Filas actualizadas
     */
    public int recordFailedPin(Long id) {
        Map<String, Object> out = gateway.execute(
                RECORD_FAILED_QR_PIN,
                new MapSqlParameterSource().addValue("p_id", id, Types.NUMERIC));
        return updatedCount(out);
    }

    /**
     * Revoca un token de acceso temporal por solicitud del paciente.
     *
     * @param publicId Identificador público del acceso
     * @param pacienteId ID numérico del paciente dueño
     * @return 1 si se revocó, 0 si no existía o ya estaba revocado
     */
    public int revoke(String publicId, Long pacienteId) {
        Map<String, Object> out = gateway.execute(REVOKE_QR_ACCESS, new MapSqlParameterSource()
                .addValue("p_public_id", publicId, Types.VARCHAR)
                .addValue("p_patient_id", pacienteId, Types.NUMERIC));
        return updatedCount(out);
    }

    private static int updatedCount(Map<String, Object> out) {
        Number updated = (Number) out.get("o_updated");
        return updated != null ? updated.intValue() : 0;
    }
}
