package com.meditriaje.repository;

import com.meditriaje.dto.affiliation.AusenciaResponse;
import com.meditriaje.model.AusenciaMedica;
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

/** Ausencias médicas ({@code AUSENCIA_MEDICA}) a través de {@code PKG_SCHEDULING}. No contiene SQL. */
@Repository
public class AusenciaMedicaRepository {

    static final String PACKAGE = "PKG_SCHEDULING";

    static final RowMapper<AusenciaMedica> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp tsIni = rs.getTimestamp("FECHA_INICIO");
        Timestamp tsFin = rs.getTimestamp("FECHA_FIN");
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        return new AusenciaMedica(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("PROFESIONAL_ID"),
                tsIni != null ? tsIni.toInstant() : null,
                tsFin != null ? tsFin.toInstant() : null,
                rs.getString("MOTIVO"),
                rs.getString("ESTADO"),
                rs.getLong("REGISTRADO_POR_USUARIO_ID"),
                tsCreated != null ? tsCreated.toInstant() : null);
    };

    static final RowMapper<AusenciaResponse> RESPONSE_MAPPER = (rs, rowNum) -> {
        Timestamp tsIni = rs.getTimestamp("FECHA_INICIO");
        Timestamp tsFin = rs.getTimestamp("FECHA_FIN");
        return new AusenciaResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("PROF_PUB_ID"),
                rs.getString("PROF_NOMBRE") + " " + rs.getString("PROF_APELLIDO"),
                tsIni != null ? tsIni.toInstant() : null,
                tsFin != null ? tsFin.toInstant() : null,
                rs.getString("MOTIVO"),
                rs.getString("ESTADO"));
    };

    static final ProcedureSpec CREATE_ABSENCE = ProcedureSpec.procedure(PACKAGE, "create_absence")
            .in("p_public_id", Types.VARCHAR)
            .in("p_professional_id", Types.NUMERIC)
            .in("p_start_at", Types.TIMESTAMP)
            .in("p_end_at", Types.TIMESTAMP)
            .in("p_reason", Types.VARCHAR)
            .in("p_status", Types.VARCHAR)
            .in("p_registered_by_user_id", Types.NUMERIC)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_ABSENCE_BY_ID = ProcedureSpec.procedure(PACKAGE, "get_absence_by_id")
            .in("p_id", Types.NUMERIC)
            .cursor("o_row", ROW_MAPPER)
            .build();

    static final ProcedureSpec COUNT_OVERLAPPING_ABSENCES =
            ProcedureSpec.function(PACKAGE, "count_overlapping_absences", "result", Types.NUMERIC)
                    .in("p_professional_id", Types.NUMERIC)
                    .in("p_start_at", Types.TIMESTAMP)
                    .in("p_end_at", Types.TIMESTAMP)
                    .build();

    static final ProcedureSpec LIST_ABSENCES_BY_PROFESSIONAL =
            ProcedureSpec.procedure(PACKAGE, "list_absences_by_professional")
                    .in("p_professional_id", Types.NUMERIC)
                    .cursor("o_rows", RESPONSE_MAPPER)
                    .build();

    private final PackageGateway gateway;

    public AusenciaMedicaRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public AusenciaMedica guardar(AusenciaMedica a) {
        Map<String, Object> out = gateway.execute(CREATE_ABSENCE, new MapSqlParameterSource()
                .addValue("p_public_id", a.publicId(), Types.VARCHAR)
                .addValue("p_professional_id", a.profesionalId(), Types.NUMERIC)
                .addValue("p_start_at", Timestamp.from(a.fechaInicio()), Types.TIMESTAMP)
                .addValue("p_end_at", Timestamp.from(a.fechaFin()), Types.TIMESTAMP)
                .addValue("p_reason", a.motivo(), Types.VARCHAR)
                .addValue("p_status", a.estado() != null ? a.estado() : "ACTIVA", Types.VARCHAR)
                .addValue("p_registered_by_user_id", a.registradoPorUsuarioId(), Types.NUMERIC));

        Number key = (Number) out.get("o_id");
        Long id = key != null ? key.longValue() : null;
        return buscarPorId(id).orElseThrow(() -> new IllegalStateException("Error al recuperar ausencia guardada"));
    }

    public Optional<AusenciaMedica> buscarPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        List<AusenciaMedica> list = gateway.list(
                GET_ABSENCE_BY_ID, new MapSqlParameterSource().addValue("p_id", id, Types.NUMERIC), "o_row");
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public boolean existeTraslapeAusencia(Long profesionalId, Instant inicio, Instant fin) {
        Number count = gateway.callFunction(
                COUNT_OVERLAPPING_ABSENCES,
                new MapSqlParameterSource()
                        .addValue("p_professional_id", profesionalId, Types.NUMERIC)
                        .addValue("p_start_at", inicio != null ? Timestamp.from(inicio) : null, Types.TIMESTAMP)
                        .addValue("p_end_at", fin != null ? Timestamp.from(fin) : null, Types.TIMESTAMP));
        return count != null && count.intValue() > 0;
    }

    public List<AusenciaResponse> listarPorProfesionalId(Long profesionalId) {
        return gateway.list(
                LIST_ABSENCES_BY_PROFESSIONAL,
                new MapSqlParameterSource().addValue("p_professional_id", profesionalId, Types.NUMERIC),
                "o_rows");
    }
}
