package com.meditriaje.repository;

import com.meditriaje.dto.audit.RegistroAuditoriaResponse;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Repositorio para la tabla {@code AUDITORIA}; delega en {@code PKG_AUDIT} a través de {@link PackageGateway}
 * y no contiene SQL.
 *
 * <p><strong>Restricción de diseño (ADR-011, ADR-012):</strong>
 * Este repositorio es estrictamente <em>insert-only</em>. No define ni expone métodos
 * de actualización ni eliminación, garantizando la inmutabilidad de la bitácora desde la capa de datos.</p>
 */
@Repository
public class AuditoriaRepository {

    static final String PACKAGE = "PKG_AUDIT";

    static final RowMapper<RegistroAuditoriaResponse> EVENT_MAPPER = (rs, rowNum) -> new RegistroAuditoriaResponse(
            rs.getLong("ID"),
            rs.getString("USUARIO_EMAIL") != null ? rs.getString("USUARIO_EMAIL") : "Sistema / Anónimo",
            rs.getString("ACCION"),
            rs.getString("TIPO_RECURSO"),
            rs.getString("RECURSO_PUBLIC_ID"),
            rs.getString("RESULTADO"),
            rs.getString("IP_ORIGEN"),
            rs.getTimestamp("FECHA_HORA").toInstant()
    );

    static final ProcedureSpec INSERT_AUDIT_EVENT = ProcedureSpec.procedure(PACKAGE, "insert_audit_event")
            .in("p_user_id", Types.NUMERIC)
            .in("p_action", Types.VARCHAR)
            .in("p_resource_type", Types.VARCHAR)
            .in("p_resource_public_id", Types.VARCHAR)
            .in("p_result", Types.VARCHAR)
            .in("p_source_ip", Types.VARCHAR)
            .build();

    static final ProcedureSpec LIST_AUDIT_EVENTS = ProcedureSpec.procedure(PACKAGE, "list_audit_events")
            .in("p_from", Types.TIMESTAMP)
            .in("p_to", Types.TIMESTAMP)
            .in("p_action", Types.VARCHAR)
            .in("p_result", Types.VARCHAR)
            .in("p_offset", Types.NUMERIC)
            .in("p_size", Types.NUMERIC)
            .cursor("o_rows", EVENT_MAPPER)
            .build();

    static final ProcedureSpec COUNT_AUDIT_EVENTS =
            ProcedureSpec.function(PACKAGE, "count_audit_events", "result", Types.NUMERIC)
                    .in("p_from", Types.TIMESTAMP)
                    .in("p_to", Types.TIMESTAMP)
                    .in("p_action", Types.VARCHAR)
                    .in("p_result", Types.VARCHAR)
                    .build();

    private final PackageGateway gateway;

    public AuditoriaRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    /**
     * Inserta un nuevo registro de auditoría.
     *
     * @param evento Metadatos del evento a registrar.
     */
    public void registrar(EventoAuditoria evento) {
        Objects.requireNonNull(evento, "El evento de auditoria no puede ser nulo");
        gateway.execute(INSERT_AUDIT_EVENT, new MapSqlParameterSource()
                .addValue("p_user_id", evento.usuarioId(), Types.NUMERIC)
                .addValue("p_action", evento.accion().name(), Types.VARCHAR)
                .addValue("p_resource_type", evento.tipoRecurso(), Types.VARCHAR)
                .addValue("p_resource_public_id", evento.recursoPublicId(), Types.VARCHAR)
                .addValue("p_result", evento.resultado().name(), Types.VARCHAR)
                .addValue("p_source_ip", evento.ipOrigen(), Types.VARCHAR));
    }

    /**
     * Consulta paginada de eventos de auditoría para el visor de seguridad (ADR-019).
     */
    public List<RegistroAuditoriaResponse> listarEventos(
            Instant fechaDesde,
            Instant fechaHasta,
            String accion,
            String resultado,
            int page,
            int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);
        MapSqlParameterSource params = filters(fechaDesde, fechaHasta, accion, resultado)
                .addValue("p_offset", safePage * safeSize)
                .addValue("p_size", safeSize);
        return gateway.list(LIST_AUDIT_EVENTS, params, "o_rows");
    }

    /**
     * Conteo total de eventos para cálculo de paginación en el visor de auditoría (ADR-019).
     */
    public long contarEventos(
            Instant fechaDesde,
            Instant fechaHasta,
            String accion,
            String resultado
    ) {
        Number total = gateway.callFunction(COUNT_AUDIT_EVENTS, filters(fechaDesde, fechaHasta, accion, resultado));
        return total != null ? total.longValue() : 0L;
    }

    /** Filtros opcionales: un valor nulo o en blanco no filtra; acción y resultado se normalizan a mayúsculas. */
    private static MapSqlParameterSource filters(Instant fechaDesde, Instant fechaHasta, String accion, String resultado) {
        return new MapSqlParameterSource()
                .addValue("p_from", fechaDesde != null ? Timestamp.from(fechaDesde) : null, Types.TIMESTAMP)
                .addValue("p_to", fechaHasta != null ? Timestamp.from(fechaHasta) : null, Types.TIMESTAMP)
                .addValue("p_action", normalize(accion), Types.VARCHAR)
                .addValue("p_result", normalize(resultado), Types.VARCHAR);
    }

    private static String normalize(String value) {
        return value != null && !value.isBlank() ? value.trim().toUpperCase() : null;
    }
}
