package com.meditriaje.repository;

import com.meditriaje.model.IdentidadProvisional;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Repositorio de {@code IDENTIDAD_PROVISIONAL} (Fase U, ADR-023, U03) a través de {@code PKG_PATIENTS}. No
 * contiene SQL.
 */
@Repository
public class IdentidadProvisionalRepository {

    static final RowMapper<IdentidadProvisional> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp tsConf = rs.getTimestamp("FECHA_CONFIRMACION");
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        Integer edad = getNullableInt(rs, "EDAD_APARENTE");
        Long pacId = getNullableLong(rs, "VINCULADO_PACIENTE_ID");
        Long confPor = getNullableLong(rs, "CONFIRMADO_POR_USUARIO_ID");

        return new IdentidadProvisional(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("EPISODIO_ID"),
                rs.getString("CODIGO_PROVISIONAL"),
                rs.getString("DESCRIPCION_FISICA"),
                edad,
                rs.getString("GENERO_APARENTE"),
                rs.getString("CONDICION_LLEGADA"),
                rs.getString("ESTADO"),
                pacId,
                confPor,
                tsConf != null ? tsConf.toInstant() : null,
                tsCreated != null ? tsCreated.toInstant() : null);
    };

    static final ProcedureSpec CREATE_PROVISIONAL_IDENTITY = ProcedureSpec.procedure(
                    PacienteRepository.PACKAGE, "create_provisional_identity")
            .in("p_public_id", Types.VARCHAR)
            .in("p_episode_id", Types.NUMERIC)
            .in("p_provisional_code", Types.VARCHAR)
            .in("p_physical_description", Types.VARCHAR)
            .in("p_apparent_age", Types.NUMERIC)
            .in("p_apparent_gender", Types.VARCHAR)
            .in("p_arrival_condition", Types.VARCHAR)
            .in("p_status", Types.VARCHAR)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_BY_EPISODE = ProcedureSpec.procedure(
                    PacienteRepository.PACKAGE, "get_provisional_identity_by_episode")
            .in("p_episode_id", Types.NUMERIC)
            .cursor("o_row", ROW_MAPPER)
            .build();

    static final ProcedureSpec GET_BY_CODE = ProcedureSpec.procedure(
                    PacienteRepository.PACKAGE, "get_provisional_identity_by_code")
            .in("p_provisional_code", Types.VARCHAR)
            .cursor("o_row", ROW_MAPPER)
            .build();

    static final ProcedureSpec RECONCILE = ProcedureSpec.procedure(
                    PacienteRepository.PACKAGE, "reconcile_provisional_identity")
            .in("p_id", Types.NUMERIC)
            .in("p_patient_id", Types.NUMERIC)
            .in("p_confirmed_by_user_id", Types.NUMERIC)
            .in("p_confirmed_at", Types.TIMESTAMP)
            .out("o_affected", Types.NUMERIC)
            .build();

    private final PackageGateway gateway;

    public IdentidadProvisionalRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public IdentidadProvisional guardar(IdentidadProvisional identidad) {
        Map<String, Object> out = gateway.execute(CREATE_PROVISIONAL_IDENTITY, new MapSqlParameterSource()
                .addValue("p_public_id", identidad.publicId())
                .addValue("p_episode_id", Objects.requireNonNull(identidad.episodioId()))
                .addValue("p_provisional_code", identidad.codigoProvisional())
                .addValue("p_physical_description", identidad.descripcionFisica(), Types.VARCHAR)
                .addValue("p_apparent_age", identidad.edadAparente(), Types.NUMERIC)
                .addValue("p_apparent_gender", identidad.generoAparente(), Types.VARCHAR)
                .addValue("p_arrival_condition", identidad.condicionLlegada(), Types.VARCHAR)
                .addValue("p_status", identidad.estado()));

        Number id = (Number) out.get("o_id");
        Long generatedId = id != null ? id.longValue() : null;

        return new IdentidadProvisional(
                generatedId,
                identidad.publicId(),
                identidad.episodioId(),
                identidad.codigoProvisional(),
                identidad.descripcionFisica(),
                identidad.edadAparente(),
                identidad.generoAparente(),
                identidad.condicionLlegada(),
                identidad.estado(),
                null,
                null,
                null,
                Instant.now());
    }

    public Optional<IdentidadProvisional> buscarPorEpisodioId(Long episodioId) {
        return gateway.single(GET_BY_EPISODE, new MapSqlParameterSource("p_episode_id", episodioId), "o_row");
    }

    public Optional<IdentidadProvisional> buscarPorCodigo(String codigoProvisional) {
        return gateway.single(GET_BY_CODE, new MapSqlParameterSource("p_provisional_code", codigoProvisional), "o_row");
    }

    public int reconciliarIdentidad(Long id, Long pacienteId, Long confirmadoPorUsuarioId, Instant fechaConfirmacion) {
        Map<String, Object> out = gateway.execute(RECONCILE, new MapSqlParameterSource()
                .addValue("p_id", id)
                .addValue("p_patient_id", pacienteId)
                .addValue("p_confirmed_by_user_id", confirmadoPorUsuarioId)
                .addValue("p_confirmed_at",
                        Timestamp.from(fechaConfirmacion != null ? fechaConfirmacion : Instant.now())));
        return ((Number) out.get("o_affected")).intValue();
    }

    private static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
