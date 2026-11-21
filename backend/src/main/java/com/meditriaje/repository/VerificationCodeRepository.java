package com.meditriaje.repository;

import com.meditriaje.model.VerificationCode;
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
 * Repositorio para la entidad {@code CODIGO_VERIFICACION} (F2.1, ADR-014). Almacena y valida códigos de
 * recuperación y verificación mediante las rutinas de {@code PKG_IDENTITY}; no contiene SQL.
 */
@Repository
public class VerificationCodeRepository {

    static final String PACKAGE = "PKG_IDENTITY";

    static final RowMapper<VerificationCode> CODIGO_MAPPER = (rs, rowNum) -> {
        Timestamp tsExp = rs.getTimestamp("FECHA_EXPIRACION");
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        return new VerificationCode(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("USUARIO_ID"),
                rs.getString("TIPO"),
                rs.getString("CODIGO_HASH"),
                tsExp != null ? tsExp.toInstant() : null,
                rs.getInt("INTENTOS_FALLIDOS"),
                rs.getInt("MAX_INTENTOS"),
                rs.getInt("USADO") == 1,
                tsCreated != null ? tsCreated.toInstant() : null
        );
    };

    static final ProcedureSpec CREATE_VERIFICATION_CODE =
            ProcedureSpec.procedure(PACKAGE, "create_verification_code")
                    .in("p_public_id", Types.VARCHAR)
                    .in("p_user_id", Types.NUMERIC)
                    .in("p_type", Types.VARCHAR)
                    .in("p_code_hash", Types.VARCHAR)
                    .in("p_expires_at", Types.TIMESTAMP)
                    .in("p_failed_attempts", Types.NUMERIC)
                    .in("p_max_attempts", Types.NUMERIC)
                    .in("p_used", Types.NUMERIC)
                    .out("o_code_id", Types.NUMERIC)
                    .build();

    static final ProcedureSpec GET_VERIFICATION_CODE_BY_ID =
            ProcedureSpec.procedure(PACKAGE, "get_verification_code_by_id")
                    .in("p_code_id", Types.NUMERIC)
                    .cursor("o_code", CODIGO_MAPPER)
                    .build();

    static final ProcedureSpec GET_LATEST_PENDING_CODE = ProcedureSpec.procedure(PACKAGE, "get_latest_pending_code")
            .in("p_user_id", Types.NUMERIC)
            .in("p_type", Types.VARCHAR)
            .cursor("o_code", CODIGO_MAPPER)
            .build();

    static final ProcedureSpec INCREMENT_CODE_ATTEMPTS = ProcedureSpec.procedure(PACKAGE, "increment_code_attempts")
            .in("p_code_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec MARK_CODE_USED = ProcedureSpec.procedure(PACKAGE, "mark_code_used")
            .in("p_code_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec INVALIDATE_PENDING_CODES =
            ProcedureSpec.procedure(PACKAGE, "invalidate_pending_codes")
                    .in("p_user_id", Types.NUMERIC)
                    .in("p_type", Types.VARCHAR)
                    .build();

    private final PackageGateway gateway;

    public VerificationCodeRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public Long create(VerificationCode codigo) {
        Map<String, Object> out = gateway.execute(CREATE_VERIFICATION_CODE, new MapSqlParameterSource()
                .addValue("p_public_id", codigo.publicId())
                .addValue("p_user_id", codigo.userId())
                .addValue("p_type", codigo.type())
                .addValue("p_code_hash", codigo.codeHash())
                .addValue("p_expires_at", Timestamp.from(codigo.expirationDate()))
                .addValue("p_failed_attempts", codigo.failedAttempts())
                .addValue("p_max_attempts", codigo.maxAttempts())
                .addValue("p_used", codigo.used() ? 1 : 0));
        Number key = (Number) out.get("o_code_id");
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para el codigo de verificacion.");
        }
        return key.longValue();
    }

    public Optional<VerificationCode> findById(Long id) {
        List<VerificationCode> resultados =
                gateway.list(GET_VERIFICATION_CODE_BY_ID, new MapSqlParameterSource("p_code_id", id), "o_code");
        return resultados.stream().findFirst();
    }

    public Optional<VerificationCode> findLatestPendingByUserAndType(Long usuarioId, String tipo) {
        List<VerificationCode> resultados = gateway.list(GET_LATEST_PENDING_CODE, new MapSqlParameterSource()
                .addValue("p_user_id", usuarioId)
                .addValue("p_type", tipo), "o_code");
        return resultados.stream().findFirst();
    }

    public void incrementAttempts(Long id) {
        gateway.execute(INCREMENT_CODE_ATTEMPTS, new MapSqlParameterSource("p_code_id", id));
    }

    public void markAsUsed(Long id) {
        gateway.execute(MARK_CODE_USED, new MapSqlParameterSource("p_code_id", id));
    }

    public void invalidatePreviousCodes(Long usuarioId, String tipo) {
        gateway.execute(INVALIDATE_PENDING_CODES, new MapSqlParameterSource()
                .addValue("p_user_id", usuarioId)
                .addValue("p_type", tipo));
    }
}
