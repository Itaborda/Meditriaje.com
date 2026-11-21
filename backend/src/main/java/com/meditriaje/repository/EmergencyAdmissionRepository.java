package com.meditriaje.repository;

import com.meditriaje.model.EmergencyAdmission;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Ingresos a urgencias ({@code INGRESO_URGENCIA}, Fase U, ADR-022, U02) a través de {@code PKG_EMERGENCY}.
 * No contiene SQL.
 */
@Repository
public class EmergencyAdmissionRepository {

    static final String PACKAGE = "PKG_EMERGENCY";

    static final RowMapper<EmergencyAdmission> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        return new EmergencyAdmission(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("EPISODIO_ID"),
                rs.getString("MEDIO_LLEGADA"),
                rs.getString("ACOMPANANTE_NOMBRE"),
                rs.getString("ACOMPANANTE_CONTACTO"),
                rs.getString("MOTIVO_RESUMIDO"),
                rs.getString("OBSERVACIONES"),
                rs.getLong("REGISTRADO_POR_USUARIO_ID"),
                tsCreated != null ? tsCreated.toInstant() : null);
    };

    static final ProcedureSpec CREATE_ADMISSION = ProcedureSpec.procedure(PACKAGE, "create_admission")
            .in("p_public_id", Types.VARCHAR)
            .in("p_episode_id", Types.NUMERIC)
            .in("p_arrival_mode", Types.VARCHAR)
            .in("p_companion_name", Types.VARCHAR)
            .in("p_companion_contact", Types.VARCHAR)
            .in("p_reason", Types.VARCHAR)
            .in("p_observations", Types.VARCHAR)
            .in("p_registered_by_user_id", Types.NUMERIC)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_ADMISSION_BY_EPISODE =
            ProcedureSpec.procedure(PACKAGE, "get_admission_by_episode")
                    .in("p_episode_id", Types.NUMERIC)
                    .cursor("o_row", ROW_MAPPER)
                    .build();

    private final PackageGateway gateway;

    public EmergencyAdmissionRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public EmergencyAdmission save(EmergencyAdmission ingreso) {
        Map<String, Object> out = gateway.execute(CREATE_ADMISSION, new MapSqlParameterSource()
                .addValue("p_public_id", ingreso.publicId(), Types.VARCHAR)
                .addValue("p_episode_id", ingreso.episodeId(), Types.NUMERIC)
                .addValue("p_arrival_mode", ingreso.arrivalMode(), Types.VARCHAR)
                .addValue("p_companion_name", ingreso.companionName(), Types.VARCHAR)
                .addValue("p_companion_contact", ingreso.companionContact(), Types.VARCHAR)
                .addValue("p_reason", ingreso.briefReason(), Types.VARCHAR)
                .addValue("p_observations", ingreso.notes(), Types.VARCHAR)
                .addValue("p_registered_by_user_id", ingreso.registeredByUserId(), Types.NUMERIC));

        Number id = (Number) out.get("o_id");
        Long generatedId = id != null ? id.longValue() : null;

        return new EmergencyAdmission(
                generatedId,
                ingreso.publicId(),
                ingreso.episodeId(),
                ingreso.arrivalMode(),
                ingreso.companionName(),
                ingreso.companionContact(),
                ingreso.briefReason(),
                ingreso.notes(),
                ingreso.registeredByUserId(),
                Instant.now());
    }

    public Optional<EmergencyAdmission> findByEpisodeId(Long episodioId) {
        List<EmergencyAdmission> rows = gateway.list(
                GET_ADMISSION_BY_EPISODE, new MapSqlParameterSource("p_episode_id", episodioId), "o_row");
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
