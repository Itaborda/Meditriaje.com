package com.meditriaje.repository;

import com.meditriaje.model.MfaBackupCode;
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

/**
 * Repositorio para códigos de respaldo de un solo uso en MFA (ADR-014, F2.1.4). No contiene SQL: delega en
 * {@code PKG_IDENTITY} a través de {@link PackageGateway}.
 */
@Repository
public class MfaBackupCodeRepository {

    static final String PACKAGE = "PKG_IDENTITY";

    static final RowMapper<MfaBackupCode> BACKUP_CODE_MAPPER = (rs, rowNum) -> {
        Timestamp tsUsado = rs.getTimestamp("USADO_AT");
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        return new MfaBackupCode(
                rs.getLong("ID"),
                rs.getLong("USUARIO_ID"),
                rs.getString("CODE_HASH"),
                rs.getInt("USADO") == 1,
                tsUsado != null ? tsUsado.toInstant() : null,
                tsCreated != null ? tsCreated.toInstant() : null
        );
    };

    static final ProcedureSpec ADD_MFA_BACKUP_CODE = ProcedureSpec.procedure(PACKAGE, "add_mfa_backup_code")
            .in("p_user_id", Types.NUMERIC)
            .in("p_code_hash", Types.VARCHAR)
            .build();

    static final ProcedureSpec DELETE_MFA_BACKUP_CODES = ProcedureSpec.procedure(PACKAGE, "delete_mfa_backup_codes")
            .in("p_user_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec CONSUME_MFA_BACKUP_CODE = ProcedureSpec.procedure(PACKAGE, "consume_mfa_backup_code")
            .in("p_user_id", Types.NUMERIC)
            .in("p_code_hash", Types.VARCHAR)
            .out("o_updated", Types.NUMERIC)
            .build();

    static final ProcedureSpec COUNT_AVAILABLE_BACKUP_CODES =
            ProcedureSpec.function(PACKAGE, "count_available_backup_codes", "result", Types.NUMERIC)
                    .in("p_user_id", Types.NUMERIC)
                    .build();

    static final ProcedureSpec LIST_MFA_BACKUP_CODES = ProcedureSpec.procedure(PACKAGE, "list_mfa_backup_codes")
            .in("p_user_id", Types.NUMERIC)
            .cursor("o_rows", BACKUP_CODE_MAPPER)
            .build();

    private final PackageGateway gateway;

    public MfaBackupCodeRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    /** Registra los códigos del lote; todos comparten la transacción del servicio que llama. */
    public void guardarLote(Long usuarioId, List<String> hashes) {
        if (hashes == null || hashes.isEmpty()) {
            return;
        }
        for (String hash : hashes) {
            gateway.execute(ADD_MFA_BACKUP_CODE, new MapSqlParameterSource()
                    .addValue("p_user_id", usuarioId)
                    .addValue("p_code_hash", hash));
        }
    }

    public void eliminarPorUsuario(Long usuarioId) {
        gateway.execute(DELETE_MFA_BACKUP_CODES, new MapSqlParameterSource("p_user_id", usuarioId));
    }

    public boolean consumirCodigo(Long usuarioId, String codeHash) {
        Map<String, Object> out = gateway.execute(CONSUME_MFA_BACKUP_CODE, new MapSqlParameterSource()
                .addValue("p_user_id", usuarioId)
                .addValue("p_code_hash", codeHash));
        Number filasAfectadas = (Number) out.get("o_updated");
        return filasAfectadas != null && filasAfectadas.intValue() > 0;
    }

    public int contarDisponibles(Long usuarioId) {
        Number count = gateway.callFunction(
                COUNT_AVAILABLE_BACKUP_CODES, new MapSqlParameterSource("p_user_id", usuarioId));
        return count != null ? count.intValue() : 0;
    }

    public List<MfaBackupCode> listarPorUsuario(Long usuarioId) {
        return gateway.list(LIST_MFA_BACKUP_CODES, new MapSqlParameterSource("p_user_id", usuarioId), "o_rows");
    }
}
