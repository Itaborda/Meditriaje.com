package com.meditriaje.repository;

import com.meditriaje.dto.report.DistributionItemDto;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Repositorio para métricas e indicadores operativos agregados (F2.6, RF-30, ADR-018).
 * Delega en {@code PKG_REPORTS} mediante {@link PackageGateway} sin contener SQL y sin exponer
 * datos clínicos identificables (ADR-007).
 */
@Repository
public class ReportRepository {

    static final String PACKAGE = "PKG_REPORTS";

    record CountRow(String key, long total) {}

    record DistributionRow(String publicId, String label, long total) {}

    record DispensationStatusRow(long pendingCount, long partialCount, long totalCount) {}

    static final RowMapper<CountRow> APPOINTMENT_STATUS_MAPPER =
            (rs, n) -> new CountRow(rs.getString("ESTADO"), rs.getLong("TOTAL"));

    static final RowMapper<CountRow> TRIAGE_LEVEL_MAPPER =
            (rs, n) -> new CountRow(rs.getString("NIVEL_PRIORIDAD"), rs.getLong("TOTAL"));

    static final RowMapper<DistributionRow> NAMED_DISTRIBUTION_MAPPER =
            (rs, n) -> new DistributionRow(rs.getString("PUBLIC_ID"), rs.getString("NOMBRE"), rs.getLong("TOTAL"));

    static final RowMapper<DistributionRow> ROUTE_DISTRIBUTION_MAPPER = (rs, n) -> {
        String ruta = rs.getString("RUTA_SUGERIDA");
        return new DistributionRow(ruta, ruta, rs.getLong("TOTAL"));
    };

    static final RowMapper<DispensationStatusRow> DISPENSATION_STATUS_MAPPER = (rs, n) -> new DispensationStatusRow(
            rs.getLong("PENDIENTES"),
            rs.getLong("PARCIALES"),
            rs.getLong("TOTALES")
    );

    static final ProcedureSpec COUNT_APPOINTMENTS_BY_STATUS =
            ProcedureSpec.procedure(PACKAGE, "count_appointments_by_status")
                    .in("p_from", Types.TIMESTAMP)
                    .in("p_to", Types.TIMESTAMP)
                    .cursor("o_rows", APPOINTMENT_STATUS_MAPPER)
                    .build();

    static final ProcedureSpec LIST_APPOINTMENTS_BY_SPECIALTY =
            ProcedureSpec.procedure(PACKAGE, "list_appointments_by_specialty")
                    .in("p_from", Types.TIMESTAMP)
                    .in("p_to", Types.TIMESTAMP)
                    .cursor("o_rows", NAMED_DISTRIBUTION_MAPPER)
                    .build();

    static final ProcedureSpec LIST_APPOINTMENTS_BY_SITE =
            ProcedureSpec.procedure(PACKAGE, "list_appointments_by_site")
                    .in("p_from", Types.TIMESTAMP)
                    .in("p_to", Types.TIMESTAMP)
                    .cursor("o_rows", NAMED_DISTRIBUTION_MAPPER)
                    .build();

    static final ProcedureSpec COUNT_TRIAGES_BY_LEVEL =
            ProcedureSpec.procedure(PACKAGE, "count_triages_by_level")
                    .in("p_from", Types.TIMESTAMP)
                    .in("p_to", Types.TIMESTAMP)
                    .cursor("o_rows", TRIAGE_LEVEL_MAPPER)
                    .build();

    static final ProcedureSpec COUNT_EMERGENCY_TRIAGES =
            ProcedureSpec.function(PACKAGE, "count_emergency_triages", "result", Types.NUMERIC)
                    .in("p_from", Types.TIMESTAMP)
                    .in("p_to", Types.TIMESTAMP)
                    .build();

    static final ProcedureSpec LIST_TRIAGES_BY_ROUTE =
            ProcedureSpec.procedure(PACKAGE, "list_triages_by_route")
                    .in("p_from", Types.TIMESTAMP)
                    .in("p_to", Types.TIMESTAMP)
                    .cursor("o_rows", ROUTE_DISTRIBUTION_MAPPER)
                    .build();

    static final ProcedureSpec COUNT_PRESCRIPTIONS =
            ProcedureSpec.function(PACKAGE, "count_prescriptions", "result", Types.NUMERIC)
                    .in("p_from", Types.TIMESTAMP)
                    .in("p_to", Types.TIMESTAMP)
                    .build();

    static final ProcedureSpec SUM_DISPENSED_UNITS =
            ProcedureSpec.function(PACKAGE, "sum_dispensed_units", "result", Types.NUMERIC)
                    .in("p_from", Types.TIMESTAMP)
                    .in("p_to", Types.TIMESTAMP)
                    .build();

    static final ProcedureSpec COUNT_DISPENSATION_STATUSES =
            ProcedureSpec.procedure(PACKAGE, "count_dispensation_statuses")
                    .in("p_from", Types.TIMESTAMP)
                    .in("p_to", Types.TIMESTAMP)
                    .cursor("o_rows", DISPENSATION_STATUS_MAPPER)
                    .build();

    static final ProcedureSpec COUNT_BREAK_GLASS_ACTIVATIONS =
            ProcedureSpec.function(PACKAGE, "count_break_glass_activations", "result", Types.NUMERIC)
                    .in("p_from", Types.TIMESTAMP)
                    .in("p_to", Types.TIMESTAMP)
                    .build();

    static final ProcedureSpec COUNT_BREAK_GLASS_ACTIVE =
            ProcedureSpec.function(PACKAGE, "count_break_glass_active", "result", Types.NUMERIC)
                    .in("p_now", Types.TIMESTAMP)
                    .build();

    static final ProcedureSpec LIST_BREAK_GLASS_BY_SPECIALTY =
            ProcedureSpec.procedure(PACKAGE, "list_break_glass_by_specialty")
                    .in("p_from", Types.TIMESTAMP)
                    .in("p_to", Types.TIMESTAMP)
                    .cursor("o_rows", NAMED_DISTRIBUTION_MAPPER)
                    .build();

    private final PackageGateway gateway;

    public ReportRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    /**
     * Cuenta citas agrupadas por su estado en la ventana temporal especificada.
     */
    public Map<String, Long> countAppointmentsByStatus(Instant fechaDesde, Instant fechaHasta) {
        return toCountMap(gateway.list(COUNT_APPOINTMENTS_BY_STATUS, window(fechaDesde, fechaHasta), "o_rows"));
    }

    /**
     * Distribución de citas por especialidad médica.
     */
    public List<DistributionItemDto> getAppointmentsBySpecialty(Instant fechaDesde, Instant fechaHasta) {
        return toDistribution(gateway.list(LIST_APPOINTMENTS_BY_SPECIALTY, window(fechaDesde, fechaHasta), "o_rows"));
    }

    /**
     * Distribución de citas por sede hospitalaria.
     */
    public List<DistributionItemDto> getAppointmentsBySite(Instant fechaDesde, Instant fechaHasta) {
        return toDistribution(gateway.list(LIST_APPOINTMENTS_BY_SITE, window(fechaDesde, fechaHasta), "o_rows"));
    }

    /**
     * Cuenta triajes agrupados por nivel de prioridad (I..V).
     */
    public Map<String, Long> countTriagesByLevel(Instant fechaDesde, Instant fechaHasta) {
        return toCountMap(gateway.list(COUNT_TRIAGES_BY_LEVEL, window(fechaDesde, fechaHasta), "o_rows"));
    }

    /**
     * Cuenta total de triajes clasificados como corte de emergencia.
     */
    public long countEmergencyTriages(Instant fechaDesde, Instant fechaHasta) {
        Number total = gateway.callFunction(COUNT_EMERGENCY_TRIAGES, window(fechaDesde, fechaHasta));
        return total != null ? total.longValue() : 0L;
    }

    /**
     * Distribución de triajes por ruta sugerida.
     */
    public List<DistributionItemDto> getTriagesByRoute(Instant fechaDesde, Instant fechaHasta) {
        return toDistribution(gateway.list(LIST_TRIAGES_BY_ROUTE, window(fechaDesde, fechaHasta), "o_rows"));
    }

    /**
     * Total de recetas emitidas en la ventana temporal.
     */
    public long countIssuedPrescriptions(Instant fechaDesde, Instant fechaHasta) {
        Number total = gateway.callFunction(COUNT_PRESCRIPTIONS, window(fechaDesde, fechaHasta));
        return total != null ? total.longValue() : 0L;
    }

    /**
     * Total de unidades farmacológicas entregadas en farmacia.
     */
    public long countDispensedUnits(Instant fechaDesde, Instant fechaHasta) {
        Number total = gateway.callFunction(SUM_DISPENSED_UNITS, window(fechaDesde, fechaHasta));
        return total != null ? total.longValue() : 0L;
    }

    /**
     * Cuenta recetas clasificadas por su estado de dispensación en la ventana temporal.
     */
    public Map<String, Long> countPrescriptionsByDispensationStatus(Instant fechaDesde, Instant fechaHasta) {
        Map<String, Long> resultados = new HashMap<>();
        resultados.put("PENDIENTE", 0L);
        resultados.put("DISPENSADA_PARCIAL", 0L);
        resultados.put("DISPENSADA_TOTAL", 0L);

        gateway.<DispensationStatusRow>single(COUNT_DISPENSATION_STATUSES, window(fechaDesde, fechaHasta), "o_rows")
                .ifPresent(row -> {
                    resultados.put("PENDIENTE", row.pendingCount());
                    resultados.put("DISPENSADA_PARCIAL", row.partialCount());
                    resultados.put("DISPENSADA_TOTAL", row.totalCount());
                });

        return resultados;
    }

    /**
     * Total de activaciones de Break-Glass en la ventana temporal.
     */
    public long countBreakGlassActivations(Instant fechaDesde, Instant fechaHasta) {
        Number total = gateway.callFunction(COUNT_BREAK_GLASS_ACTIVATIONS, window(fechaDesde, fechaHasta));
        return total != null ? total.longValue() : 0L;
    }

    /**
     * Total de accesos Break-Glass actualmente activos (no expirados).
     */
    public long countActiveBreakGlass(Instant ahora) {
        Objects.requireNonNull(ahora, "ahora no puede ser nulo");
        Number total = gateway.callFunction(
                COUNT_BREAK_GLASS_ACTIVE,
                new MapSqlParameterSource().addValue("p_now", Timestamp.from(ahora), Types.TIMESTAMP));
        return total != null ? total.longValue() : 0L;
    }

    /**
     * Distribución de activaciones Break-Glass por especialidad del médico solicitante.
     */
    public List<DistributionItemDto> getBreakGlassBySpecialty(Instant fechaDesde, Instant fechaHasta) {
        return toDistribution(
                gateway.list(LIST_BREAK_GLASS_BY_SPECIALTY, window(fechaDesde, fechaHasta), "o_rows"));
    }

    private static MapSqlParameterSource window(Instant fechaDesde, Instant fechaHasta) {
        return new MapSqlParameterSource()
                .addValue("p_from", fechaDesde != null ? Timestamp.from(fechaDesde) : null, Types.TIMESTAMP)
                .addValue("p_to", fechaHasta != null ? Timestamp.from(fechaHasta) : null, Types.TIMESTAMP);
    }

    private static Map<String, Long> toCountMap(List<CountRow> rows) {
        Map<String, Long> resultados = new HashMap<>();
        for (CountRow row : rows) {
            resultados.put(row.key(), row.total());
        }
        return resultados;
    }

    private static List<DistributionItemDto> toDistribution(List<DistributionRow> rows) {
        long granTotal = 0;
        for (DistributionRow row : rows) {
            granTotal += row.total();
        }
        List<DistributionItemDto> items = new ArrayList<>(rows.size());
        for (DistributionRow row : rows) {
            double pct = granTotal > 0 ? (row.total() * 100.0 / granTotal) : 0.0;
            items.add(new DistributionItemDto(
                    row.publicId(),
                    row.label(),
                    row.total(),
                    Math.round(pct * 100.0) / 100.0
            ));
        }
        return items;
    }
}
