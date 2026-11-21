package com.meditriaje.repository;

import com.meditriaje.dto.operational.AlertaOperativaResponse;
import com.meditriaje.model.AlertaOperativa;
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
 * Repositorio para la entidad {@code ALERTA_OPERATIVA} (Fase O, ADR-027).
 * Delega en {@code PKG_OPERATIONS} mediante {@link PackageGateway} y no contiene SQL.
 */
@Repository
public class AlertaOperativaRepository {

    static final String PACKAGE = "PKG_OPERATIONS";

    static final RowMapper<AlertaOperativa> ENTITY_MAPPER = (rs, rowNum) -> {
        Timestamp tsRec = rs.getTimestamp("RECONOCIDO_AT");
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        Long recUser = rs.getObject("RECONOCIDO_POR_USUARIO_ID") != null
                ? rs.getLong("RECONOCIDO_POR_USUARIO_ID") : null;
        return new AlertaOperativa(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("SEDE_ID"),
                rs.getString("TIPO_ALERTA"),
                rs.getString("NIVEL_SEVERIDAD"),
                rs.getString("MENSAJE"),
                rs.getString("ESTADO"),
                recUser,
                tsRec != null ? tsRec.toInstant() : null,
                rs.getString("MOTIVO_RECONOCIMIENTO"),
                tsCreated != null ? tsCreated.toInstant() : null
        );
    };

    static final RowMapper<AlertaOperativaResponse> RESPONSE_MAPPER = (rs, rowNum) -> {
        Timestamp tsRec = rs.getTimestamp("RECONOCIDO_AT");
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        return new AlertaOperativaResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("SEDE_PUB_ID"),
                rs.getString("SEDE_NOMBRE"),
                rs.getString("TIPO_ALERTA"),
                rs.getString("NIVEL_SEVERIDAD"),
                rs.getString("MENSAJE"),
                rs.getString("ESTADO"),
                rs.getString("RECONOCIDO_POR_EMAIL"),
                tsRec != null ? tsRec.toInstant() : null,
                rs.getString("MOTIVO_RECONOCIMIENTO"),
                tsCreated != null ? tsCreated.toInstant() : null
        );
    };

    static final ProcedureSpec CREATE_OPERATIONAL_ALERT =
            ProcedureSpec.procedure(PACKAGE, "create_operational_alert")
                    .in("p_public_id", Types.VARCHAR)
                    .in("p_alert_type", Types.VARCHAR)
                    .in("p_severity", Types.VARCHAR)
                    .in("p_message", Types.VARCHAR)
                    .in("p_status", Types.VARCHAR)
                    .out("o_id", Types.NUMERIC)
                    .build();

    static final ProcedureSpec GET_OPERATIONAL_ALERT_BY_ID =
            ProcedureSpec.procedure(PACKAGE, "get_operational_alert_by_id")
                    .in("p_id", Types.NUMERIC)
                    .cursor("o_alert", ENTITY_MAPPER)
                    .build();

    static final ProcedureSpec GET_OPERATIONAL_ALERT_BY_PUBLIC_ID =
            ProcedureSpec.procedure(PACKAGE, "get_operational_alert_by_public_id")
                    .in("p_public_id", Types.VARCHAR)
                    .cursor("o_alert", ENTITY_MAPPER)
                    .build();

    static final ProcedureSpec LIST_OPERATIONAL_ALERTS =
            ProcedureSpec.procedure(PACKAGE, "list_operational_alerts")
                    .in("p_site_id", Types.NUMERIC)
                    .in("p_status", Types.VARCHAR)
                    .cursor("o_rows", RESPONSE_MAPPER)
                    .build();

    static final ProcedureSpec ACKNOWLEDGE_OPERATIONAL_ALERT =
            ProcedureSpec.procedure(PACKAGE, "acknowledge_operational_alert")
                    .in("p_id", Types.NUMERIC)
                    .in("p_user_id", Types.NUMERIC)
                    .in("p_acknowledged_at", Types.TIMESTAMP)
                    .in("p_reason", Types.VARCHAR)
                    .build();

    private final PackageGateway gateway;

    public AlertaOperativaRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public AlertaOperativa guardar(AlertaOperativa a) {
        Objects.requireNonNull(a, "alerta no puede ser nula");
        Map<String, Object> out = gateway.execute(CREATE_OPERATIONAL_ALERT, new MapSqlParameterSource()
                .addValue("p_public_id", a.publicId(), Types.VARCHAR)
                .addValue("p_alert_type", a.tipoAlerta(), Types.VARCHAR)
                .addValue("p_severity", a.nivelSeveridad(), Types.VARCHAR)
                .addValue("p_message", a.mensaje(), Types.VARCHAR)
                .addValue("p_status", a.estado() != null ? a.estado() : "ACTIVA", Types.VARCHAR));
        Number key = (Number) out.get("o_id");
        Long id = key != null ? key.longValue() : null;
        return buscarPorId(id).orElseThrow(() -> new IllegalStateException("Error al recuperar alerta guardada"));
    }

    public Optional<AlertaOperativa> buscarPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return gateway.single(
                GET_OPERATIONAL_ALERT_BY_ID,
                new MapSqlParameterSource().addValue("p_id", id, Types.NUMERIC),
                "o_alert");
    }

    public Optional<AlertaOperativa> buscarPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        return gateway.single(
                GET_OPERATIONAL_ALERT_BY_PUBLIC_ID,
                new MapSqlParameterSource().addValue("p_public_id", publicId, Types.VARCHAR),
                "o_alert");
    }

    public List<AlertaOperativaResponse> listarPorSede(Long sedeId, String estado) {
        String status = (estado != null && !estado.isBlank()) ? estado : null;
        return gateway.list(LIST_OPERATIONAL_ALERTS, new MapSqlParameterSource()
                .addValue("p_site_id", sedeId, Types.NUMERIC)
                .addValue("p_status", status, Types.VARCHAR), "o_rows");
    }

    public void reconocerAlerta(Long id, Long usuarioId, Instant ahora, String motivo) {
        gateway.execute(ACKNOWLEDGE_OPERATIONAL_ALERT, new MapSqlParameterSource()
                .addValue("p_id", id, Types.NUMERIC)
                .addValue("p_user_id", usuarioId, Types.NUMERIC)
                .addValue("p_acknowledged_at", ahora != null ? Timestamp.from(ahora) : null, Types.TIMESTAMP)
                .addValue("p_reason", motivo, Types.VARCHAR));
    }
}
