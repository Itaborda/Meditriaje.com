package com.meditriaje.repository;

import com.meditriaje.model.SeguimientoIntrahospitalarioQr;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio para la entidad {@code SEGUIMIENTO_INTRAHOSPITALARIO_QR} (Fase O, ADR-028).
 * Delega en {@code PKG_OPERATIONS} mediante {@link PackageGateway} sin SQL.
 */
@Repository
public class SeguimientoQrRepository {

    static final String PACKAGE = "PKG_OPERATIONS";

    static final RowMapper<SeguimientoIntrahospitalarioQr> ENTITY_MAPPER = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        Timestamp tsExp = rs.getTimestamp("EXPIRA_AT");
        return new SeguimientoIntrahospitalarioQr(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("EPISODIO_ID"),
                rs.getString("CODIGO_QR_TOKEN"),
                rs.getString("UBICACION_ACTUAL_TEXTO"),
                rs.getString("ESTADO"),
                tsCreated != null ? tsCreated.toInstant() : null,
                tsExp != null ? tsExp.toInstant() : null
        );
    };

    static final ProcedureSpec CREATE_INPATIENT_QR = ProcedureSpec.procedure(PACKAGE, "create_inpatient_qr")
            .in("p_public_id", Types.VARCHAR)
            .in("p_episode_id", Types.NUMERIC)
            .in("p_token", Types.VARCHAR)
            .in("p_location_text", Types.VARCHAR)
            .in("p_status", Types.VARCHAR)
            .in("p_expires_at", Types.TIMESTAMP)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_INPATIENT_QR_BY_ID = ProcedureSpec.procedure(PACKAGE, "get_inpatient_qr_by_id")
            .in("p_id", Types.NUMERIC)
            .cursor("o_qr", ENTITY_MAPPER)
            .build();

    static final ProcedureSpec GET_INPATIENT_QR_BY_TOKEN =
            ProcedureSpec.procedure(PACKAGE, "get_inpatient_qr_by_token")
                    .in("p_token", Types.VARCHAR)
                    .cursor("o_qr", ENTITY_MAPPER)
                    .build();

    static final ProcedureSpec GET_ACTIVE_INPATIENT_QR_BY_EPISODE =
            ProcedureSpec.procedure(PACKAGE, "get_active_inpatient_qr_by_episode")
                    .in("p_episode_id", Types.NUMERIC)
                    .cursor("o_qr", ENTITY_MAPPER)
                    .build();

    static final ProcedureSpec UPDATE_INPATIENT_QR_LOCATION =
            ProcedureSpec.procedure(PACKAGE, "update_inpatient_qr_location")
                    .in("p_id", Types.NUMERIC)
                    .in("p_location_text", Types.VARCHAR)
                    .build();

    private final PackageGateway gateway;

    public SeguimientoQrRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public SeguimientoIntrahospitalarioQr guardar(SeguimientoIntrahospitalarioQr q) {
        Objects.requireNonNull(q, "seguimiento QR no puede ser nulo");
        Map<String, Object> out = gateway.execute(CREATE_INPATIENT_QR, new MapSqlParameterSource()
                .addValue("p_public_id", q.publicId(), Types.VARCHAR)
                .addValue("p_episode_id", q.episodioId(), Types.NUMERIC)
                .addValue("p_token", q.codigoQrToken(), Types.VARCHAR)
                .addValue("p_location_text", q.ubicacionActualTexto(), Types.VARCHAR)
                .addValue("p_status", q.estado() != null ? q.estado() : "ACTIVO", Types.VARCHAR)
                .addValue("p_expires_at", q.expiraAt() != null ? Timestamp.from(q.expiraAt()) : null, Types.TIMESTAMP));
        Number key = (Number) out.get("o_id");
        Long id = key != null ? key.longValue() : null;
        return buscarPorId(id).orElseThrow(() -> new IllegalStateException("Error al recuperar token QR guardado"));
    }

    public Optional<SeguimientoIntrahospitalarioQr> buscarPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return gateway.single(
                GET_INPATIENT_QR_BY_ID,
                new MapSqlParameterSource().addValue("p_id", id, Types.NUMERIC),
                "o_qr");
    }

    public Optional<SeguimientoIntrahospitalarioQr> buscarPorToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return gateway.single(
                GET_INPATIENT_QR_BY_TOKEN,
                new MapSqlParameterSource().addValue("p_token", token, Types.VARCHAR),
                "o_qr");
    }

    public Optional<SeguimientoIntrahospitalarioQr> buscarActivoPorEpisodioId(Long episodioId) {
        return gateway.single(
                GET_ACTIVE_INPATIENT_QR_BY_EPISODE,
                new MapSqlParameterSource().addValue("p_episode_id", episodioId, Types.NUMERIC),
                "o_qr");
    }

    public void actualizarUbicacion(Long id, String nuevaUbicacion) {
        gateway.execute(UPDATE_INPATIENT_QR_LOCATION, new MapSqlParameterSource()
                .addValue("p_id", id, Types.NUMERIC)
                .addValue("p_location_text", nuevaUbicacion, Types.VARCHAR));
    }
}
