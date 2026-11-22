package com.meditriaje.repository;

import com.meditriaje.model.User;
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
 * Repositorio para la entidad {@code USUARIO} y sus asignaciones de roles. No contiene SQL: delega en las
 * rutinas de {@code PKG_IDENTITY} a través de {@link PackageGateway}.
 */
@Repository
public class UserRepository {

    static final String PACKAGE = "PKG_IDENTITY";

    static final RowMapper<User> USUARIO_MAPPER = (rs, rowNum) -> {
        Timestamp tsBloqueado = rs.getTimestamp("BLOQUEADO_HASTA");
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        Timestamp tsMfaConfig = rs.getTimestamp("MFA_CONFIGURADO_AT");
        return new User(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getString("EMAIL"),
                rs.getString("PASSWORD_HASH"),
                rs.getString("ESTADO"),
                rs.getInt("INTENTOS_FALLIDOS"),
                tsBloqueado != null ? tsBloqueado.toInstant() : null,
                tsCreated != null ? tsCreated.toInstant() : null,
                rs.getInt("DEBE_CAMBIAR_PASSWORD") == 1,
                rs.getInt("MFA_HABILITADO") == 1,
                rs.getString("MFA_SECRET"),
                tsMfaConfig != null ? tsMfaConfig.toInstant() : null
        );
    };

    static final RowMapper<String> ROLE_NAME_MAPPER = (rs, rowNum) -> rs.getString("NOMBRE");

    static final ProcedureSpec COUNT_USERS_BY_EMAIL =
            ProcedureSpec.function(PACKAGE, "count_users_by_email", "result", Types.NUMERIC)
                    .in("p_email", Types.VARCHAR)
                    .build();

    static final ProcedureSpec CREATE_USER = ProcedureSpec.procedure(PACKAGE, "create_user")
            .in("p_public_id", Types.VARCHAR)
            .in("p_email", Types.VARCHAR)
            .in("p_password_hash", Types.VARCHAR)
            .in("p_must_change_password", Types.NUMERIC)
            .out("o_user_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_USER_BY_EMAIL = ProcedureSpec.procedure(PACKAGE, "get_user_by_email")
            .in("p_email", Types.VARCHAR)
            .cursor("o_user", USUARIO_MAPPER)
            .build();

    static final ProcedureSpec GET_USER_BY_ID = ProcedureSpec.procedure(PACKAGE, "get_user_by_id")
            .in("p_user_id", Types.NUMERIC)
            .cursor("o_user", USUARIO_MAPPER)
            .build();

    static final ProcedureSpec GET_USER_BY_PUBLIC_ID = ProcedureSpec.procedure(PACKAGE, "get_user_by_public_id")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_user", USUARIO_MAPPER)
            .build();

    static final ProcedureSpec LIST_USER_ROLES = ProcedureSpec.procedure(PACKAGE, "list_user_roles")
            .in("p_user_id", Types.NUMERIC)
            .cursor("o_rows", ROLE_NAME_MAPPER)
            .build();

    static final ProcedureSpec UPDATE_FAILED_ATTEMPTS = ProcedureSpec.procedure(PACKAGE, "update_failed_attempts")
            .in("p_user_id", Types.NUMERIC)
            .in("p_attempts", Types.NUMERIC)
            .in("p_locked_until", Types.TIMESTAMP)
            .in("p_status", Types.VARCHAR)
            .build();

    static final ProcedureSpec RESET_FAILED_ATTEMPTS = ProcedureSpec.procedure(PACKAGE, "reset_failed_attempts")
            .in("p_user_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_ROLE_ID_BY_NAME =
            ProcedureSpec.function(PACKAGE, "get_role_id_by_name", "result", Types.NUMERIC)
                    .in("p_name", Types.VARCHAR)
                    .build();

    static final ProcedureSpec ASSIGN_ROLE = ProcedureSpec.procedure(PACKAGE, "assign_role")
            .in("p_user_id", Types.NUMERIC)
            .in("p_role_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec UPDATE_PASSWORD = ProcedureSpec.procedure(PACKAGE, "update_password")
            .in("p_user_id", Types.NUMERIC)
            .in("p_password_hash", Types.VARCHAR)
            .in("p_must_change_password", Types.NUMERIC)
            .build();

    static final ProcedureSpec UPDATE_STATUS = ProcedureSpec.procedure(PACKAGE, "update_status")
            .in("p_user_id", Types.NUMERIC)
            .in("p_status", Types.VARCHAR)
            .build();

    static final ProcedureSpec SAVE_MFA_SECRET = ProcedureSpec.procedure(PACKAGE, "save_mfa_secret")
            .in("p_user_id", Types.NUMERIC)
            .in("p_mfa_secret", Types.VARCHAR)
            .build();

    static final ProcedureSpec ENABLE_MFA = ProcedureSpec.procedure(PACKAGE, "enable_mfa")
            .in("p_user_id", Types.NUMERIC)
            .in("p_mfa_secret", Types.VARCHAR)
            .build();

    static final ProcedureSpec DISABLE_MFA = ProcedureSpec.procedure(PACKAGE, "disable_mfa")
            .in("p_user_id", Types.NUMERIC)
            .build();

    private final PackageGateway gateway;

    public UserRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public boolean existsByEmail(String email) {
        Number count = gateway.callFunction(COUNT_USERS_BY_EMAIL, new MapSqlParameterSource("p_email", email));
        return count != null && count.longValue() > 0;
    }

    public Long create(String publicId, String email, String passwordHash) {
        return create(publicId, email, passwordHash, false);
    }

    public Long create(String publicId, String email, String passwordHash, boolean debeCambiarPassword) {
        Map<String, Object> out = gateway.execute(CREATE_USER, new MapSqlParameterSource()
                .addValue("p_public_id", publicId)
                .addValue("p_email", email)
                .addValue("p_password_hash", passwordHash)
                .addValue("p_must_change_password", debeCambiarPassword ? 1 : 0));
        Number key = (Number) out.get("o_user_id");
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para el usuario.");
        }
        return key.longValue();
    }

    public Optional<User> findByEmail(String email) {
        List<User> resultados =
                gateway.list(GET_USER_BY_EMAIL, new MapSqlParameterSource("p_email", email), "o_user");
        return resultados.stream().findFirst();
    }

    public Optional<User> findById(Long id) {
        List<User> resultados =
                gateway.list(GET_USER_BY_ID, new MapSqlParameterSource("p_user_id", id), "o_user");
        return resultados.stream().findFirst();
    }

    public Optional<User> findByPublicId(String publicId) {
        List<User> resultados =
                gateway.list(GET_USER_BY_PUBLIC_ID, new MapSqlParameterSource("p_public_id", publicId), "o_user");
        return resultados.stream().findFirst();
    }

    public List<String> getRoles(Long usuarioId) {
        return gateway.list(LIST_USER_ROLES, new MapSqlParameterSource("p_user_id", usuarioId), "o_rows");
    }

    public void updateFailedAttempts(Long usuarioId, int intentos, Instant bloqueadoHasta, String estado) {
        gateway.execute(UPDATE_FAILED_ATTEMPTS, new MapSqlParameterSource()
                .addValue("p_user_id", usuarioId)
                .addValue("p_attempts", intentos)
                .addValue("p_locked_until", bloqueadoHasta != null ? Timestamp.from(bloqueadoHasta) : null)
                .addValue("p_status", estado));
    }

    public void resetAttempts(Long usuarioId) {
        gateway.execute(RESET_FAILED_ATTEMPTS, new MapSqlParameterSource("p_user_id", usuarioId));
    }

    public Optional<Long> findRoleIdByName(String nombre) {
        Number id = gateway.callFunction(GET_ROLE_ID_BY_NAME, new MapSqlParameterSource("p_name", nombre));
        return Optional.ofNullable(id).map(Number::longValue);
    }

    public void assignRole(Long usuarioId, Long rolId) {
        gateway.execute(ASSIGN_ROLE, new MapSqlParameterSource()
                .addValue("p_user_id", usuarioId)
                .addValue("p_role_id", rolId));
    }

    public void updatePassword(Long usuarioId, String passwordHash, boolean debeCambiarPassword) {
        gateway.execute(UPDATE_PASSWORD, new MapSqlParameterSource()
                .addValue("p_user_id", usuarioId)
                .addValue("p_password_hash", passwordHash)
                .addValue("p_must_change_password", debeCambiarPassword ? 1 : 0));
    }

    public void updateStatus(Long usuarioId, String estado) {
        gateway.execute(UPDATE_STATUS, new MapSqlParameterSource()
                .addValue("p_user_id", usuarioId)
                .addValue("p_status", estado));
    }

    public void saveMfaSecret(Long usuarioId, String mfaSecret) {
        gateway.execute(SAVE_MFA_SECRET, new MapSqlParameterSource()
                .addValue("p_user_id", usuarioId)
                .addValue("p_mfa_secret", mfaSecret));
    }

    public void enableMfa(Long usuarioId, String mfaSecret) {
        gateway.execute(ENABLE_MFA, new MapSqlParameterSource()
                .addValue("p_user_id", usuarioId)
                .addValue("p_mfa_secret", mfaSecret));
    }

    public void disableMfa(Long usuarioId) {
        gateway.execute(DISABLE_MFA, new MapSqlParameterSource("p_user_id", usuarioId));
    }
}
