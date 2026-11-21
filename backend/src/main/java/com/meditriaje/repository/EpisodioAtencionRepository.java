package com.meditriaje.repository;

import com.meditriaje.dto.emergency.EpisodioUrgenciaResponse;
import com.meditriaje.dto.emergency.ItemColaUrgenciaResponse;
import com.meditriaje.model.EpisodioAtencion;
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
 * Episodios de atención ({@code EPISODIO_ATENCION}, Fase U, ADR-022, U02, U05) a través de {@code PKG_EMERGENCY}.
 * No contiene SQL.
 */
@Repository
public class EpisodioAtencionRepository {

    static final String PACKAGE = "PKG_EMERGENCY";

    static final RowMapper<EpisodioAtencion> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp tsIngreso = rs.getTimestamp("INGRESO_AT");
        Timestamp tsEgreso = rs.getTimestamp("EGRESO_AT");
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        Timestamp tsUpdated = rs.getTimestamp("UPDATED_AT");
        long pacId = rs.getLong("PACIENTE_ID");
        Long pacienteId = rs.wasNull() ? null : pacId;

        return new EpisodioAtencion(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                pacienteId,
                rs.getString("TIPO"),
                1L,
                rs.getString("ESTADO"),
                tsIngreso != null ? tsIngreso.toInstant() : null,
                tsEgreso != null ? tsEgreso.toInstant() : null,
                rs.getString("MOTIVO_INGRESO"),
                tsCreated != null ? tsCreated.toInstant() : null,
                tsUpdated != null ? tsUpdated.toInstant() : null);
    };

    static final RowMapper<EpisodioUrgenciaResponse> DETAIL_MAPPER = (rs, rowNum) -> {
        Timestamp tsIngreso = rs.getTimestamp("INGRESO_AT");
        Timestamp tsEgreso = rs.getTimestamp("EGRESO_AT");
        Timestamp tsCreado = rs.getTimestamp("CREADO_AT");
        String codProv = rs.getString("CODIGO_PROVISIONAL");

        return new EpisodioUrgenciaResponse(
                rs.getString("EPISODIO_PUB_ID"),
                rs.getString("PACIENTE_PUB_ID"),
                rs.getString("PACIENTE_NOMBRE"),
                rs.getString("SEDE_PUB_ID"),
                rs.getString("SEDE_NOMBRE"),
                rs.getString("TIPO"),
                rs.getString("ESTADO"),
                codProv != null,
                codProv,
                rs.getString("IDENTIDAD_ESTADO"),
                rs.getString("MEDIO_LLEGADA"),
                rs.getString("MOTIVO_RESUMIDO"),
                rs.getString("NIVEL_TRIAJE") != null ? rs.getString("NIVEL_TRIAJE") : "PENDIENTE_VALORACION",
                rs.getString("MEDICO_TRATANTE_NOMBRE"),
                tsIngreso != null ? tsIngreso.toInstant() : null,
                tsEgreso != null ? tsEgreso.toInstant() : null,
                tsCreado != null ? tsCreado.toInstant() : null);
    };

    static final RowMapper<ItemColaUrgenciaResponse> QUEUE_MAPPER = (rs, rowNum) -> {
        Timestamp tsIngreso = rs.getTimestamp("INGRESO_AT");
        long minutos = rs.getLong("MINUTOS_ESPERA");
        if (minutos < 0) minutos = 0;

        return new ItemColaUrgenciaResponse(
                rs.getString("EPISODIO_PUB_ID"),
                rs.getString("PACIENTE_PUB_ID"),
                rs.getString("IDENTIFICADOR_VISIBLE"),
                rs.getString("CODIGO_PROVISIONAL"),
                rs.getString("SEDE_PUB_ID"),
                rs.getString("SEDE_NOMBRE"),
                rs.getString("TIPO"),
                rs.getString("ESTADO"),
                rs.getString("NIVEL_TRIAJE"),
                rs.getString("MOTIVO_LLEGADA"),
                minutos,
                tsIngreso != null ? tsIngreso.toInstant() : null,
                rs.getString("MEDICO_TRATANTE_NOMBRE"));
    };

    static final ProcedureSpec CREATE_EPISODE = ProcedureSpec.procedure(PACKAGE, "create_episode")
            .in("p_public_id", Types.VARCHAR)
            .in("p_patient_id", Types.NUMERIC)
            .in("p_type", Types.VARCHAR)
            .in("p_status", Types.VARCHAR)
            .in("p_admitted_at", Types.TIMESTAMP)
            .in("p_reason", Types.VARCHAR)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_EPISODE_BY_ID = ProcedureSpec.procedure(PACKAGE, "get_episode_by_id")
            .in("p_id", Types.NUMERIC)
            .cursor("o_row", ROW_MAPPER)
            .build();

    static final ProcedureSpec GET_EPISODE_BY_PUBLIC_ID =
            ProcedureSpec.procedure(PACKAGE, "get_episode_by_public_id")
                    .in("p_public_id", Types.VARCHAR)
                    .cursor("o_row", ROW_MAPPER)
                    .build();

    static final ProcedureSpec UPDATE_EPISODE_STATUS = ProcedureSpec.procedure(PACKAGE, "update_episode_status")
            .in("p_id", Types.NUMERIC)
            .in("p_status", Types.VARCHAR)
            .out("o_updated", Types.NUMERIC)
            .build();

    static final ProcedureSpec LINK_PATIENT = ProcedureSpec.procedure(PACKAGE, "link_patient")
            .in("p_id", Types.NUMERIC)
            .in("p_patient_id", Types.NUMERIC)
            .out("o_updated", Types.NUMERIC)
            .build();

    static final ProcedureSpec CLOSE_EPISODE = ProcedureSpec.procedure(PACKAGE, "close_episode")
            .in("p_id", Types.NUMERIC)
            .in("p_discharged_at", Types.TIMESTAMP)
            .out("o_updated", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_EPISODE_DETAIL = ProcedureSpec.procedure(PACKAGE, "get_episode_detail")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_row", DETAIL_MAPPER)
            .build();

    static final ProcedureSpec LIST_EMERGENCY_QUEUE = ProcedureSpec.procedure(PACKAGE, "list_emergency_queue")
            .in("p_site_id", Types.NUMERIC)
            .in("p_status", Types.VARCHAR)
            .cursor("o_rows", QUEUE_MAPPER)
            .build();

    private final PackageGateway gateway;

    public EpisodioAtencionRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    /** Registra el episodio en la única sede del hospital (la resuelve el paquete). */
    public EpisodioAtencion guardar(EpisodioAtencion episodio) {
        Instant ingresoAt = episodio.ingresoAt() != null ? episodio.ingresoAt() : Instant.now();
        Map<String, Object> out = gateway.execute(CREATE_EPISODE, new MapSqlParameterSource()
                .addValue("p_public_id", episodio.publicId(), Types.VARCHAR)
                .addValue("p_patient_id", episodio.pacienteId(), Types.NUMERIC)
                .addValue("p_type", episodio.tipo(), Types.VARCHAR)
                .addValue("p_status", episodio.estado(), Types.VARCHAR)
                .addValue("p_admitted_at", Timestamp.from(ingresoAt), Types.TIMESTAMP)
                .addValue("p_reason", episodio.motivoIngreso(), Types.VARCHAR));

        Number id = (Number) out.get("o_id");
        Long generatedId = id != null ? id.longValue() : null;

        return new EpisodioAtencion(
                generatedId,
                episodio.publicId(),
                episodio.pacienteId(),
                episodio.tipo(),
                episodio.sedeId(),
                episodio.estado(),
                episodio.ingresoAt() != null ? episodio.ingresoAt() : Instant.now(),
                episodio.egresoAt(),
                episodio.motivoIngreso(),
                Instant.now(),
                Instant.now());
    }

    public Optional<EpisodioAtencion> buscarPorId(Long id) {
        return first(gateway.list(GET_EPISODE_BY_ID, new MapSqlParameterSource("p_id", id), "o_row"));
    }

    public Optional<EpisodioAtencion> buscarPorPublicId(String publicId) {
        return first(gateway.list(GET_EPISODE_BY_PUBLIC_ID, new MapSqlParameterSource("p_public_id", publicId), "o_row"));
    }

    public int actualizarEstado(Long id, String nuevoEstado) {
        return updated(gateway.execute(UPDATE_EPISODE_STATUS, new MapSqlParameterSource()
                .addValue("p_id", id)
                .addValue("p_status", nuevoEstado)));
    }

    public int vincularPaciente(Long id, Long pacienteId) {
        return updated(gateway.execute(LINK_PATIENT, new MapSqlParameterSource()
                .addValue("p_id", id, Types.NUMERIC)
                .addValue("p_patient_id", pacienteId, Types.NUMERIC)));
    }

    public int cerrarEpisodio(Long id, Instant egresoAt) {
        return updated(gateway.execute(CLOSE_EPISODE, new MapSqlParameterSource()
                .addValue("p_id", id)
                .addValue("p_discharged_at", Timestamp.from(egresoAt != null ? egresoAt : Instant.now()))));
    }

    /**
     * Consulta detallada del episodio con joins a paciente, sede, triaje actual y equipo asistencial.
     */
    public Optional<EpisodioUrgenciaResponse> buscarDetallePorPublicId(String publicId) {
        return first(gateway.list(GET_EPISODE_DETAIL, new MapSqlParameterSource("p_public_id", publicId), "o_row"));
    }

    /**
     * Consulta la cola priorizada de urgencias por sede (U05).
     * Orden: Prioridad I > II > III > IV > V > PENDIENTE_VALORACION, y luego por menor tiempo de llegada.
     * Ambos filtros son opcionales (nulo o en blanco = sin filtro).
     */
    public List<ItemColaUrgenciaResponse> listarColaUrgenciaPorSede(Long sedeId, String estadoFiltro) {
        String estado = estadoFiltro != null && !estadoFiltro.isBlank() ? estadoFiltro : null;
        return gateway.list(LIST_EMERGENCY_QUEUE, new MapSqlParameterSource()
                .addValue("p_site_id", sedeId, Types.NUMERIC)
                .addValue("p_status", estado, Types.VARCHAR), "o_rows");
    }

    private static int updated(Map<String, Object> out) {
        Number rows = (Number) out.get("o_updated");
        return rows != null ? rows.intValue() : 0;
    }

    private static <T> Optional<T> first(List<T> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
