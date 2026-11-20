package com.meditriaje.repository;

import com.meditriaje.model.RefreshToken;
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
 * Repositorio para la gestión y persistencia de refresh tokens rotativos (ADR-002). No contiene SQL: delega en
 * {@code PKG_IDENTITY} a través de {@link PackageGateway}.
 */
@Repository
public class RefreshTokenRepository {

    static final String PACKAGE = "PKG_IDENTITY";

    static final RowMapper<RefreshToken> REFRESH_TOKEN_MAPPER = (rs, rowNum) -> {
        Timestamp tsExp = rs.getTimestamp("EXPIRACION");
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        return new RefreshToken(
                rs.getLong("ID"),
                rs.getLong("USUARIO_ID"),
                rs.getString("TOKEN_HASH"),
                tsExp != null ? tsExp.toInstant() : null,
                rs.getInt("REVOCADO") == 1,
                tsCreated != null ? tsCreated.toInstant() : null
        );
    };

    static final ProcedureSpec CREATE_REFRESH_TOKEN = ProcedureSpec.procedure(PACKAGE, "create_refresh_token")
            .in("p_user_id", Types.NUMERIC)
            .in("p_token_hash", Types.VARCHAR)
            .in("p_expires_at", Types.TIMESTAMP)
            .out("o_token_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_REFRESH_TOKEN_BY_HASH =
            ProcedureSpec.procedure(PACKAGE, "get_refresh_token_by_hash")
                    .in("p_token_hash", Types.VARCHAR)
                    .cursor("o_token", REFRESH_TOKEN_MAPPER)
                    .build();

    static final ProcedureSpec REVOKE_REFRESH_TOKEN = ProcedureSpec.procedure(PACKAGE, "revoke_refresh_token")
            .in("p_token_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec REVOKE_REFRESH_TOKEN_BY_HASH =
            ProcedureSpec.procedure(PACKAGE, "revoke_refresh_token_by_hash")
                    .in("p_token_hash", Types.VARCHAR)
                    .build();

    static final ProcedureSpec REVOKE_USER_REFRESH_TOKENS =
            ProcedureSpec.procedure(PACKAGE, "revoke_user_refresh_tokens")
                    .in("p_user_id", Types.NUMERIC)
                    .build();

    private final PackageGateway gateway;

    public RefreshTokenRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public Long crear(Long usuarioId, String tokenHash, Instant expiracion) {
        Map<String, Object> out = gateway.execute(CREATE_REFRESH_TOKEN, new MapSqlParameterSource()
                .addValue("p_user_id", usuarioId)
                .addValue("p_token_hash", tokenHash)
                .addValue("p_expires_at", Timestamp.from(expiracion)));
        Number key = (Number) out.get("o_token_id");
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para el refresh token.");
        }
        return key.longValue();
    }

    public Optional<RefreshToken> buscarPorTokenHash(String tokenHash) {
        List<RefreshToken> resultados = gateway.list(
                GET_REFRESH_TOKEN_BY_HASH, new MapSqlParameterSource("p_token_hash", tokenHash), "o_token");
        return resultados.stream().findFirst();
    }

    public void revocar(Long tokenId) {
        gateway.execute(REVOKE_REFRESH_TOKEN, new MapSqlParameterSource("p_token_id", tokenId));
    }

    public void revocarPorTokenHash(String tokenHash) {
        gateway.execute(REVOKE_REFRESH_TOKEN_BY_HASH, new MapSqlParameterSource("p_token_hash", tokenHash));
    }

    public void revocarTodosPorUsuario(Long usuarioId) {
        gateway.execute(REVOKE_USER_REFRESH_TOKENS, new MapSqlParameterSource("p_user_id", usuarioId));
    }
}
