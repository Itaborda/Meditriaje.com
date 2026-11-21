package com.meditriaje.repository;

import com.meditriaje.dto.pharmacy.DispensationDetailResponse;
import com.meditriaje.dto.pharmacy.DispensationResponse;
import com.meditriaje.dto.pharmacy.PrescriptionDispensationResponse;
import com.meditriaje.dto.pharmacy.MedicationBalanceDto;
import com.meditriaje.model.Dispensation;
import com.meditriaje.model.DispensationDetail;
import com.meditriaje.model.PrescriptionDispensationStatus;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Repositorio de dispensaciones farmacéuticas y control de saldos de recetas médicas a través de
 * {@code PKG_PHARMACY} (F2.4, ADR-016). No contiene SQL: declara las rutinas del paquete y delega en
 * {@link PackageGateway}.
 */
@Repository
public class DispensationRepository {

    static final RowMapper<Dispensation> DISPENSATION_MAPPER = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("CREATED_AT");
        return new Dispensation(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("RECETA_ID"),
                1L,
                rs.getLong("USUARIO_ID"),
                rs.getString("OBSERVACIONES"),
                ts != null ? ts.toInstant() : null);
    };

    record DispensationHeaderRow(Long dispensationId, DispensationResponse base) {}

    static final RowMapper<DispensationHeaderRow> DISPENSATION_VIEW_MAPPER = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("CREATED_AT");
        return new DispensationHeaderRow(rs.getLong("DISPENSACION_ID"), new DispensationResponse(
                rs.getString("DISPENSACION_PUBLIC_ID"),
                rs.getString("RECETA_PUBLIC_ID"),
                rs.getString("SEDE_PUBLIC_ID"),
                rs.getString("SEDE_NOMBRE"),
                rs.getString("DISPENSADOR_PUBLIC_ID"),
                rs.getString("DISPENSADOR_NOMBRE"),
                rs.getString("OBSERVACIONES"),
                ts != null ? ts.toInstant() : null,
                List.of()));
    };

    static final RowMapper<DispensationDetailResponse> DISPENSATION_ITEM_MAPPER = (rs, rowNum) -> {
        Date fv = rs.getDate("FECHA_VENCIMIENTO_LOTE");
        Timestamp ts = rs.getTimestamp("CREATED_AT");
        return new DispensationDetailResponse(
                rs.getString("MEDICAMENTO_PUBLIC_ID"),
                rs.getString("MEDICAMENTO_CODIGO"),
                rs.getString("NOMBRE_COMERCIAL"),
                rs.getInt("CANTIDAD_ENTREGADA"),
                rs.getString("LOTE"),
                fv != null ? fv.toLocalDate() : null,
                ts != null ? ts.toInstant() : null);
    };

    record DispensedTotalRow(Long prescriptionDetailId, int totalDelivered) {}

    static final RowMapper<DispensedTotalRow> DISPENSED_TOTAL_MAPPER = (rs, rowNum) ->
            new DispensedTotalRow(rs.getLong("RECETA_DETALLE_ID"), rs.getInt("TOTAL_ENTREGADO"));

    static final RowMapper<MedicationBalanceDto> BALANCE_MAPPER = (rs, rowNum) -> {
        int prescrita = rs.getInt("CANTIDAD_PRESCRITA");
        int dispensada = rs.getInt("CANTIDAD_DISPENSADA");
        int saldo = Math.max(0, prescrita - dispensada);

        String estado;
        if (dispensada == 0) {
            estado = "PENDIENTE";
        } else if (saldo <= 0) {
            estado = "DISPENSADA_TOTAL";
        } else {
            estado = "DISPENSADA_PARCIAL";
        }

        return new MedicationBalanceDto(
                rs.getString("MEDICAMENTO_PUBLIC_ID"),
                rs.getString("MEDICAMENTO_CODIGO"),
                rs.getString("SNAPSHOT_NOMBRE"),
                rs.getString("SNAPSHOT_PRINCIPIO_ACTIVO"),
                rs.getString("SNAPSHOT_PRESENTACION"),
                rs.getString("SNAPSHOT_CONCENTRACION"),
                rs.getString("DOSIS"),
                rs.getString("FRECUENCIA"),
                rs.getInt("DURACION_DIAS"),
                prescrita,
                dispensada,
                saldo,
                estado,
                rs.getString("INDICACIONES"));
    };

    /** Información del ítem prescrito para validaciones de correspondencia y saldo en servicio. */
    public record PrescribedItemInfo(
            Long prescriptionDetailId,
            String medicationPublicId,
            String medicationName,
            int prescribedQuantity) {}

    static final RowMapper<PrescribedItemInfo> PRESCRIBED_ITEM_MAPPER = (rs, rowNum) -> new PrescribedItemInfo(
            rs.getLong("RECETA_DETALLE_ID"),
            rs.getString("MEDICAMENTO_PUBLIC_ID"),
            rs.getString("SNAPSHOT_NOMBRE"),
            rs.getInt("CANTIDAD_PRESCRITA"));

    record DispensingHeaderRow(
            Long prescriptionId,
            String prescriptionPublicId,
            String attentionPublicId,
            String patientPublicId,
            String patientDocument,
            String patientName,
            String professionalPublicId,
            String professionalName,
            String specialtyName,
            int validityDays,
            Instant issuedAt) {}

    static final RowMapper<DispensingHeaderRow> DISPENSING_PRESCRIPTION_MAPPER = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        return new DispensingHeaderRow(
                rs.getLong("RECETA_ID"),
                rs.getString("RECETA_PUBLIC_ID"),
                rs.getString("ATENCION_PUBLIC_ID"),
                rs.getString("PACIENTE_PUBLIC_ID"),
                rs.getString("PACIENTE_DOCUMENTO"),
                rs.getString("PACIENTE_NOMBRE"),
                rs.getString("PROFESIONAL_PUBLIC_ID"),
                rs.getString("PROFESIONAL_NOMBRE"),
                rs.getString("ESPECIALIDAD_NOMBRE"),
                rs.getInt("VIGENCIA_DIAS"),
                tsCreated != null ? tsCreated.toInstant() : Instant.now());
    };

    static final ProcedureSpec CREATE_DISPENSATION = ProcedureSpec.procedure(
                    MedicationRepository.PACKAGE, "create_dispensation")
            .in("p_public_id", Types.VARCHAR)
            .in("p_prescription_id", Types.NUMERIC)
            .in("p_user_id", Types.NUMERIC)
            .in("p_notes", Types.VARCHAR)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec CREATE_DISPENSATION_ITEM = ProcedureSpec.procedure(
                    MedicationRepository.PACKAGE, "create_dispensation_item")
            .in("p_dispensation_id", Types.NUMERIC)
            .in("p_prescription_item_id", Types.NUMERIC)
            .in("p_delivered_quantity", Types.NUMERIC)
            .in("p_batch", Types.VARCHAR)
            .in("p_batch_expiry_date", Types.DATE)
            .build();

    static final ProcedureSpec GET_DISPENSATION_BY_PUBLIC_ID = ProcedureSpec.procedure(
                    MedicationRepository.PACKAGE, "get_dispensation_by_public_id")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_row", DISPENSATION_MAPPER)
            .build();

    static final ProcedureSpec GET_DISPENSATION_VIEW_BY_PUBLIC_ID = ProcedureSpec.procedure(
                    MedicationRepository.PACKAGE, "get_dispensation_view_by_public_id")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_row", DISPENSATION_VIEW_MAPPER)
            .build();

    static final ProcedureSpec LIST_DISPENSATION_ITEMS = ProcedureSpec.procedure(
                    MedicationRepository.PACKAGE, "list_dispensation_items")
            .in("p_dispensation_id", Types.NUMERIC)
            .cursor("o_rows", DISPENSATION_ITEM_MAPPER)
            .build();

    static final ProcedureSpec LIST_DISPENSATIONS_BY_PRESCRIPTION = ProcedureSpec.procedure(
                    MedicationRepository.PACKAGE, "list_dispensations_by_prescription")
            .in("p_prescription_id", Types.NUMERIC)
            .cursor("o_rows", DISPENSATION_VIEW_MAPPER)
            .build();

    static final ProcedureSpec LIST_DISPENSED_TOTALS = ProcedureSpec.procedure(
                    MedicationRepository.PACKAGE, "list_dispensed_totals")
            .in("p_prescription_id", Types.NUMERIC)
            .cursor("o_rows", DISPENSED_TOTAL_MAPPER)
            .build();

    static final ProcedureSpec LIST_PRESCRIPTION_BALANCES = ProcedureSpec.procedure(
                    MedicationRepository.PACKAGE, "list_prescription_balances")
            .in("p_prescription_id", Types.NUMERIC)
            .cursor("o_rows", BALANCE_MAPPER)
            .build();

    static final ProcedureSpec LIST_PRESCRIBED_ITEMS = ProcedureSpec.procedure(
                    MedicationRepository.PACKAGE, "list_prescribed_items")
            .in("p_prescription_id", Types.NUMERIC)
            .cursor("o_rows", PRESCRIBED_ITEM_MAPPER)
            .build();

    static final ProcedureSpec GET_DISPENSING_PRESCRIPTION = ProcedureSpec.procedure(
                    MedicationRepository.PACKAGE, "get_dispensing_prescription")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_row", DISPENSING_PRESCRIPTION_MAPPER)
            .build();

    static final ProcedureSpec SEARCH_DISPENSING_PRESCRIPTIONS = ProcedureSpec.procedure(
                    MedicationRepository.PACKAGE, "search_dispensing_prescriptions")
            .in("p_term", Types.VARCHAR)
            .in("p_pattern", Types.VARCHAR)
            .in("p_id_pattern", Types.VARCHAR)
            .in("p_offset", Types.NUMERIC)
            .in("p_size", Types.NUMERIC)
            .cursor("o_rows", DISPENSING_PRESCRIPTION_MAPPER)
            .build();

    static final ProcedureSpec COUNT_DISPENSING_PRESCRIPTIONS = ProcedureSpec.function(
                    MedicationRepository.PACKAGE, "count_dispensing_prescriptions", "result", Types.NUMERIC)
            .in("p_term", Types.VARCHAR)
            .in("p_pattern", Types.VARCHAR)
            .in("p_id_pattern", Types.VARCHAR)
            .build();

    private final PackageGateway gateway;

    public DispensationRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    /**
     * Persiste una nueva cabecera de dispensación farmacéutica. Inmutable tras su inserción (ADR-016).
     * La sede única del hospital se resuelve en {@code PKG_PHARMACY} vía {@code PKG_COMMON}.
     */
    public Long createDispensation(Dispensation dispensacion) {
        Objects.requireNonNull(dispensacion, "dispensacion no puede ser nula");

        Map<String, Object> out = gateway.execute(CREATE_DISPENSATION, new MapSqlParameterSource()
                .addValue("p_public_id", dispensacion.publicId())
                .addValue("p_prescription_id", Objects.requireNonNull(dispensacion.prescriptionId()))
                .addValue("p_user_id", Objects.requireNonNull(dispensacion.userId()))
                .addValue("p_notes", dispensacion.notes(), Types.VARCHAR));

        Number key = (Number) out.get("o_id");
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para la dispensacion.");
        }
        return key.longValue();
    }

    /**
     * Guarda los ítems entregados en DISPENSACION_DETALLE con lote y vencimiento.
     */
    public void saveDetails(Long dispensacionId, List<DispensationDetail> detalles) {
        if (detalles == null || detalles.isEmpty()) {
            return;
        }

        for (DispensationDetail d : detalles) {
            gateway.execute(CREATE_DISPENSATION_ITEM, new MapSqlParameterSource()
                    .addValue("p_dispensation_id", dispensacionId != null ? dispensacionId : d.dispensationId())
                    .addValue("p_prescription_item_id", d.prescriptionDetailId())
                    .addValue("p_delivered_quantity", d.deliveredQuantity())
                    .addValue("p_batch", d.batch(), Types.VARCHAR)
                    .addValue("p_batch_expiry_date",
                            d.batchExpirationDate() != null ? Date.valueOf(d.batchExpirationDate()) : null,
                            Types.DATE));
        }
    }

    public Optional<Dispensation> findEntityByPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        return gateway.single(GET_DISPENSATION_BY_PUBLIC_ID,
                new MapSqlParameterSource("p_public_id", publicId.trim()), "o_row");
    }

    public Optional<DispensationResponse> findByPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        return gateway.<DispensationHeaderRow>single(GET_DISPENSATION_VIEW_BY_PUBLIC_ID,
                        new MapSqlParameterSource("p_public_id", publicId.trim()), "o_row")
                .map(this::completeDispensation);
    }

    public List<DispensationDetailResponse> findDetailsByDispensationId(Long dispensacionId) {
        if (dispensacionId == null) {
            return List.of();
        }
        return gateway.list(LIST_DISPENSATION_ITEMS,
                new MapSqlParameterSource("p_dispensation_id", dispensacionId), "o_rows");
    }

    public List<DispensationResponse> listByPrescriptionId(Long prescriptionId) {
        if (prescriptionId == null) {
            return List.of();
        }
        List<DispensationHeaderRow> rows = gateway.list(LIST_DISPENSATIONS_BY_PRESCRIPTION,
                new MapSqlParameterSource("p_prescription_id", prescriptionId), "o_rows");
        return rows.stream().map(this::completeDispensation).toList();
    }

    public Map<Long, Integer> getDispensedTotalsByPrescriptionId(Long prescriptionId) {
        if (prescriptionId == null) {
            return Map.of();
        }
        List<DispensedTotalRow> rows = gateway.list(LIST_DISPENSED_TOTALS,
                new MapSqlParameterSource("p_prescription_id", prescriptionId), "o_rows");
        Map<Long, Integer> totales = new HashMap<>();
        for (DispensedTotalRow row : rows) {
            totales.put(row.prescriptionDetailId(), row.totalDelivered());
        }
        return totales;
    }

    public List<MedicationBalanceDto> getBalancesByPrescriptionId(Long prescriptionId) {
        if (prescriptionId == null) {
            return List.of();
        }
        return gateway.list(LIST_PRESCRIPTION_BALANCES,
                new MapSqlParameterSource("p_prescription_id", prescriptionId), "o_rows");
    }

    public List<PrescribedItemInfo> findPrescriptionItems(Long prescriptionId) {
        if (prescriptionId == null) {
            return List.of();
        }
        return gateway.list(LIST_PRESCRIBED_ITEMS,
                new MapSqlParameterSource("p_prescription_id", prescriptionId), "o_rows");
    }

    public Optional<PrescriptionDispensationResponse> findPrescriptionDispensationByPublicId(String prescriptionPublicId) {
        if (prescriptionPublicId == null || prescriptionPublicId.isBlank()) {
            return Optional.empty();
        }
        return gateway.<DispensingHeaderRow>single(GET_DISPENSING_PRESCRIPTION,
                        new MapSqlParameterSource("p_public_id", prescriptionPublicId.trim()), "o_row")
                .map(this::buildPrescriptionDispensationResponse);
    }

    public List<PrescriptionDispensationResponse> searchPrescriptionDispensations(String query, int page, int size) {
        int offset = Math.max(0, page) * Math.max(1, size);
        int limit = Math.max(1, size);
        SearchCriteria criteria = SearchCriteria.from(query);

        List<DispensingHeaderRow> rows = gateway.list(SEARCH_DISPENSING_PRESCRIPTIONS, new MapSqlParameterSource()
                .addValue("p_term", criteria.term(), Types.VARCHAR)
                .addValue("p_pattern", criteria.pattern(), Types.VARCHAR)
                .addValue("p_id_pattern", criteria.idPattern(), Types.VARCHAR)
                .addValue("p_offset", offset)
                .addValue("p_size", limit), "o_rows");

        return rows.stream().map(this::buildPrescriptionDispensationResponse).toList();
    }

    public int countPrescriptionDispensations(String query) {
        SearchCriteria criteria = SearchCriteria.from(query);
        Number count = gateway.callFunction(COUNT_DISPENSING_PRESCRIPTIONS, new MapSqlParameterSource()
                .addValue("p_term", criteria.term(), Types.VARCHAR)
                .addValue("p_pattern", criteria.pattern(), Types.VARCHAR)
                .addValue("p_id_pattern", criteria.idPattern(), Types.VARCHAR));
        return count != null ? count.intValue() : 0;
    }

    private DispensationResponse completeDispensation(DispensationHeaderRow row) {
        DispensationResponse b = row.base();
        List<DispensationDetailResponse> detalles = findDetailsByDispensationId(row.dispensationId());
        return new DispensationResponse(
                b.publicId(),
                b.prescriptionPublicId(),
                b.sitePublicId(),
                b.siteName(),
                b.dispenserPublicId(),
                b.dispenserName(),
                b.notes(),
                b.createdAt(),
                detalles);
    }

    private PrescriptionDispensationResponse buildPrescriptionDispensationResponse(DispensingHeaderRow row) {
        Instant issuedAt = row.issuedAt();
        Instant fechaVencimiento = issuedAt.plus(row.validityDays(), ChronoUnit.DAYS);
        boolean vencida = Instant.now().isAfter(fechaVencimiento);

        String cleanUuid = row.prescriptionPublicId().replace("-", "");
        String codigoReclamacion = "REC-" + (cleanUuid.length() >= 8
                ? cleanUuid.substring(0, 8).toUpperCase(Locale.ROOT)
                : cleanUuid.toUpperCase(Locale.ROOT));

        List<MedicationBalanceDto> items = getBalancesByPrescriptionId(row.prescriptionId());
        List<DispensationResponse> entregasPrevias = listByPrescriptionId(row.prescriptionId());

        PrescriptionDispensationStatus estado;
        if (items.isEmpty()) {
            estado = PrescriptionDispensationStatus.PENDIENTE;
        } else {
            boolean todasEntregadas = items.stream().allMatch(item -> item.pendingBalance() == 0);
            boolean algunaEntregada = items.stream().anyMatch(item -> item.dispensedQuantity() > 0);

            if (todasEntregadas) {
                estado = PrescriptionDispensationStatus.DISPENSADA_TOTAL;
            } else if (algunaEntregada) {
                estado = PrescriptionDispensationStatus.DISPENSADA_PARCIAL;
            } else {
                estado = PrescriptionDispensationStatus.PENDIENTE;
            }
        }

        return new PrescriptionDispensationResponse(
                row.prescriptionPublicId(),
                codigoReclamacion,
                row.attentionPublicId(),
                row.patientPublicId(),
                row.patientDocument(),
                row.patientName(),
                row.professionalPublicId(),
                row.professionalName(),
                row.specialtyName(),
                row.validityDays(),
                issuedAt,
                fechaVencimiento,
                vencida,
                estado,
                items,
                entregasPrevias);
    }

    private record SearchCriteria(String term, String pattern, String idPattern) {
        static SearchCriteria from(String query) {
            if (query == null || query.isBlank()) {
                return new SearchCriteria(null, null, null);
            }
            String trimmed = query.trim();
            String normalizedId = trimmed.toUpperCase(Locale.ROOT).startsWith("REC-")
                    ? trimmed.substring(4)
                    : trimmed;
            return new SearchCriteria(trimmed, "%" + trimmed + "%", "%" + normalizedId + "%");
        }
    }
}
