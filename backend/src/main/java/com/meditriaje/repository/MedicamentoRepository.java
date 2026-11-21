package com.meditriaje.repository;

import com.meditriaje.dto.prescription.MedicamentoResponse;
import com.meditriaje.model.Medicamento;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Repositorio del catálogo maestro de medicamentos {@code MEDICAMENTO} a través de {@code PKG_PHARMACY}
 * (ADR-001, ADR-012, HU-08). No contiene SQL: declara las rutinas del paquete y delega en {@link PackageGateway}.
 */
@Repository
public class MedicamentoRepository {

    static final String PACKAGE = "PKG_PHARMACY";

    static final RowMapper<Medicamento> MEDICATION_MAPPER = (rs, rowNum) -> new Medicamento(
            rs.getLong("ID"),
            rs.getString("PUBLIC_ID"),
            rs.getString("CODIGO"),
            rs.getString("NOMBRE_COMERCIAL"),
            rs.getString("PRINCIPIO_ACTIVO"),
            rs.getString("PRESENTACION"),
            rs.getString("CONCENTRACION"),
            rs.getString("ESTADO"));

    static final RowMapper<MedicamentoResponse> ACTIVE_MAPPER = (rs, rowNum) -> new MedicamentoResponse(
            rs.getString("PUBLIC_ID"),
            rs.getString("CODIGO"),
            rs.getString("NOMBRE_COMERCIAL"),
            rs.getString("PRINCIPIO_ACTIVO"),
            rs.getString("PRESENTACION"),
            rs.getString("CONCENTRACION"),
            rs.getString("ESTADO"));

    static final ProcedureSpec GET_MEDICATION_BY_PUBLIC_ID = ProcedureSpec.procedure(
                    PACKAGE, "get_medication_by_public_id")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_row", MEDICATION_MAPPER)
            .build();

    static final ProcedureSpec GET_MEDICATION_BY_ID = ProcedureSpec.procedure(PACKAGE, "get_medication_by_id")
            .in("p_id", Types.NUMERIC)
            .cursor("o_row", MEDICATION_MAPPER)
            .build();

    static final ProcedureSpec LIST_ACTIVE_MEDICATIONS = ProcedureSpec.procedure(PACKAGE, "list_active_medications")
            .in("p_pattern", Types.VARCHAR)
            .in("p_offset", Types.NUMERIC)
            .in("p_size", Types.NUMERIC)
            .cursor("o_rows", ACTIVE_MAPPER)
            .build();

    static final ProcedureSpec COUNT_ACTIVE_MEDICATIONS = ProcedureSpec.function(
                    PACKAGE, "count_active_medications", "result", Types.NUMERIC)
            .in("p_pattern", Types.VARCHAR)
            .build();

    private final PackageGateway gateway;

    public MedicamentoRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public Optional<Medicamento> buscarPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        return gateway.single(GET_MEDICATION_BY_PUBLIC_ID,
                new MapSqlParameterSource("p_public_id", publicId.trim()), "o_row");
    }

    public Optional<Medicamento> buscarPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return gateway.single(GET_MEDICATION_BY_ID, new MapSqlParameterSource("p_id", id), "o_row");
    }

    public List<MedicamentoResponse> listarActivos(String query, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);
        int offset = safePage * safeSize;

        return gateway.list(LIST_ACTIVE_MEDICATIONS, new MapSqlParameterSource()
                .addValue("p_pattern", toPattern(query), Types.VARCHAR)
                .addValue("p_offset", offset)
                .addValue("p_size", safeSize), "o_rows");
    }

    public int contarActivos(String query) {
        Number total = gateway.callFunction(COUNT_ACTIVE_MEDICATIONS,
                new MapSqlParameterSource().addValue("p_pattern", toPattern(query), Types.VARCHAR));
        return total != null ? total.intValue() : 0;
    }

    private static String toPattern(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return "%" + query.trim().toUpperCase(Locale.ROOT) + "%";
    }
}
