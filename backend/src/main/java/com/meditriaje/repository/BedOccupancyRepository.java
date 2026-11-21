package com.meditriaje.repository;

import com.meditriaje.model.BedOccupancy;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/** Ocupación de camas a través de {@code PKG_HOSPITAL}. No contiene SQL. */
@Repository
public class BedOccupancyRepository {

    static final String PACKAGE = "PKG_HOSPITAL";

    static final RowMapper<BedOccupancy> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp tsInicio = rs.getTimestamp("INICIO_AT");
        Timestamp tsFin = rs.getTimestamp("FIN_AT");
        Timestamp tsCreado = rs.getTimestamp("CREADO_AT");
        return new BedOccupancy(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("EPISODIO_ID"),
                rs.getLong("CAMA_ID"),
                tsInicio != null ? tsInicio.toInstant() : null,
                tsFin != null ? tsFin.toInstant() : null,
                rs.getString("ESTADO"),
                rs.getLong("ASIGNADO_POR_USUARIO_ID"),
                rs.getString("MOTIVO_ASIGNACION"),
                tsCreado != null ? tsCreado.toInstant() : null);
    };

    static final ProcedureSpec CREATE_OCCUPANCY = ProcedureSpec.procedure(PACKAGE, "create_occupancy")
            .in("p_public_id", Types.VARCHAR)
            .in("p_episode_id", Types.NUMERIC)
            .in("p_bed_id", Types.NUMERIC)
            .in("p_start_at", Types.TIMESTAMP)
            .in("p_status", Types.VARCHAR)
            .in("p_assigned_by_user_id", Types.NUMERIC)
            .in("p_reason", Types.VARCHAR)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_OCCUPANCY_BY_ID = ProcedureSpec.procedure(PACKAGE, "get_occupancy_by_id")
            .in("p_id", Types.NUMERIC)
            .cursor("o_row", ROW_MAPPER)
            .build();

    static final ProcedureSpec GET_ACTIVE_OCCUPANCY_BY_BED =
            ProcedureSpec.procedure(PACKAGE, "get_active_occupancy_by_bed")
                    .in("p_bed_id", Types.NUMERIC)
                    .cursor("o_row", ROW_MAPPER)
                    .build();

    static final ProcedureSpec GET_ACTIVE_OCCUPANCY_BY_EPISODE =
            ProcedureSpec.procedure(PACKAGE, "get_active_occupancy_by_episode")
                    .in("p_episode_id", Types.NUMERIC)
                    .cursor("o_row", ROW_MAPPER)
                    .build();

    static final ProcedureSpec FINISH_OCCUPANCY = ProcedureSpec.procedure(PACKAGE, "finish_occupancy")
            .in("p_occupancy_id", Types.NUMERIC)
            .in("p_end_at", Types.TIMESTAMP)
            .build();

    private final PackageGateway gateway;

    public BedOccupancyRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    /**
     * Registra la ocupación. Si un índice único de ocupación activa la rechaza, el paquete devuelve el ID nulo y
     * aquí se lanza {@link DuplicateKeyException} (una {@code DataIntegrityViolationException}), igual que antes,
     * para que el servicio aplique su compensación.
     */
    public BedOccupancy save(BedOccupancy ocupacion) {
        Map<String, Object> out = gateway.execute(CREATE_OCCUPANCY, new MapSqlParameterSource()
                .addValue("p_public_id", ocupacion.publicId(), Types.VARCHAR)
                .addValue("p_episode_id", ocupacion.episodeId(), Types.NUMERIC)
                .addValue("p_bed_id", ocupacion.bedId(), Types.NUMERIC)
                .addValue("p_start_at", Timestamp.from(ocupacion.startedAt()), Types.TIMESTAMP)
                .addValue("p_status", ocupacion.status() != null ? ocupacion.status() : "ACTIVA", Types.VARCHAR)
                .addValue("p_assigned_by_user_id", ocupacion.assignedByUserId(), Types.NUMERIC)
                .addValue("p_reason", ocupacion.assignmentReason(), Types.VARCHAR));
        Number key = (Number) out.get("o_id");
        if (key == null) {
            throw new DuplicateKeyException("La cama o el episodio ya tienen una ocupación activa.");
        }
        return findById(key.longValue())
                .orElseThrow(() -> new IllegalStateException("Error al recuperar ocupación recién guardada"));
    }

    public Optional<BedOccupancy> findById(Long id) {
        if (id == null) return Optional.empty();
        return first(gateway.list(GET_OCCUPANCY_BY_ID, new MapSqlParameterSource("p_id", id), "o_row"));
    }

    public Optional<BedOccupancy> findActiveByBedId(Long camaId) {
        return first(gateway.list(GET_ACTIVE_OCCUPANCY_BY_BED, new MapSqlParameterSource("p_bed_id", camaId), "o_row"));
    }

    public Optional<BedOccupancy> findActiveByEpisodeId(Long episodioId) {
        return first(gateway.list(
                GET_ACTIVE_OCCUPANCY_BY_EPISODE, new MapSqlParameterSource("p_episode_id", episodioId), "o_row"));
    }

    public void finishOccupancy(Long ocupacionId, Instant finAt) {
        gateway.execute(FINISH_OCCUPANCY, new MapSqlParameterSource()
                .addValue("p_occupancy_id", ocupacionId)
                .addValue("p_end_at", Timestamp.from(finAt)));
    }

    private static <T> Optional<T> first(List<T> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
