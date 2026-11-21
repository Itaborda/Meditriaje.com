package com.meditriaje.repository;

import com.meditriaje.model.EpsImportDetail;
import com.meditriaje.model.EpsImportBatch;
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
 * Repositorio de {@code LOTE_IMPORTACION_EPS} y {@code DETALLE_IMPORTACION_EPS} a través de
 * {@code PKG_AFFILIATIONS} (Fase A, ADR-025). No contiene SQL: declara las rutinas del paquete y delega en
 * {@link PackageGateway}.
 */
@Repository
public class EpsImportBatchRepository {

    static final RowMapper<EpsImportBatch> BATCH_MAPPER = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        Timestamp tsProc = rs.getTimestamp("PROCESADO_AT");
        return new EpsImportBatch(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getString("NOMBRE_ARCHIVO"),
                rs.getString("HASH_SHA256"),
                rs.getLong("EPS_ID"),
                rs.getInt("TOTAL_FILAS"),
                rs.getInt("FILAS_VALIDAS"),
                rs.getInt("FILAS_FALLIDAS"),
                rs.getString("ESTADO"),
                rs.getLong("SUBIDO_POR_USUARIO_ID"),
                tsCreated != null ? tsCreated.toInstant() : null,
                tsProc != null ? tsProc.toInstant() : null);
    };

    static final RowMapper<EpsImportDetail> ITEM_MAPPER = (rs, rowNum) -> new EpsImportDetail(
            rs.getLong("ID"),
            rs.getLong("LOTE_ID"),
            rs.getInt("NUMERO_FILA"),
            rs.getString("TIPO_DOCUMENTO"),
            rs.getString("NUMERO_DOCUMENTO"),
            rs.getString("NOMBRES"),
            rs.getString("APELLIDOS"),
            rs.getString("REGIMEN"),
            rs.getString("TIPO_AFILIADO"),
            rs.getString("ESTADO_FILA"),
            rs.getString("ERROR_MOTIVO"));

    static final ProcedureSpec CREATE_IMPORT_BATCH = ProcedureSpec.procedure(
                    EpsEntityRepository.PACKAGE, "create_import_batch")
            .in("p_public_id", Types.VARCHAR)
            .in("p_file_name", Types.VARCHAR)
            .in("p_sha256_hash", Types.VARCHAR)
            .in("p_eps_id", Types.NUMERIC)
            .in("p_total_rows", Types.NUMERIC)
            .in("p_valid_rows", Types.NUMERIC)
            .in("p_failed_rows", Types.NUMERIC)
            .in("p_status", Types.VARCHAR)
            .in("p_uploaded_by_user_id", Types.NUMERIC)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_IMPORT_BATCH_BY_ID = ProcedureSpec.procedure(
                    EpsEntityRepository.PACKAGE, "get_import_batch_by_id")
            .in("p_id", Types.NUMERIC)
            .cursor("o_row", BATCH_MAPPER)
            .build();

    static final ProcedureSpec GET_IMPORT_BATCH_BY_PUBLIC_ID = ProcedureSpec.procedure(
                    EpsEntityRepository.PACKAGE, "get_import_batch_by_public_id")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_row", BATCH_MAPPER)
            .build();

    static final ProcedureSpec CREATE_IMPORT_BATCH_ITEM = ProcedureSpec.procedure(
                    EpsEntityRepository.PACKAGE, "create_import_batch_item")
            .in("p_batch_id", Types.NUMERIC)
            .in("p_row_number", Types.NUMERIC)
            .in("p_document_type", Types.VARCHAR)
            .in("p_document_number", Types.VARCHAR)
            .in("p_first_names", Types.VARCHAR)
            .in("p_last_names", Types.VARCHAR)
            .in("p_regime", Types.VARCHAR)
            .in("p_affiliate_type", Types.VARCHAR)
            .in("p_row_status", Types.VARCHAR)
            .in("p_error_reason", Types.VARCHAR)
            .build();

    static final ProcedureSpec UPDATE_IMPORT_BATCH_TOTALS = ProcedureSpec.procedure(
                    EpsEntityRepository.PACKAGE, "update_import_batch_totals")
            .in("p_batch_id", Types.NUMERIC)
            .in("p_total", Types.NUMERIC)
            .in("p_valid", Types.NUMERIC)
            .in("p_failed", Types.NUMERIC)
            .build();

    static final ProcedureSpec UPDATE_IMPORT_BATCH_STATUS = ProcedureSpec.procedure(
                    EpsEntityRepository.PACKAGE, "update_import_batch_status")
            .in("p_batch_id", Types.NUMERIC)
            .in("p_status", Types.VARCHAR)
            .in("p_processed_at", Types.TIMESTAMP)
            .build();

    static final ProcedureSpec LIST_IMPORT_BATCH_ITEMS = ProcedureSpec.procedure(
                    EpsEntityRepository.PACKAGE, "list_import_batch_items")
            .in("p_batch_id", Types.NUMERIC)
            .in("p_limit", Types.NUMERIC)
            .cursor("o_rows", ITEM_MAPPER)
            .build();

    static final ProcedureSpec LIST_VALID_IMPORT_BATCH_ITEMS = ProcedureSpec.procedure(
                    EpsEntityRepository.PACKAGE, "list_valid_import_batch_items")
            .in("p_batch_id", Types.NUMERIC)
            .cursor("o_rows", ITEM_MAPPER)
            .build();

    private final PackageGateway gateway;

    public EpsImportBatchRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public EpsImportBatch saveBatch(EpsImportBatch lote) {
        Objects.requireNonNull(lote, "lote no puede ser nulo");
        Map<String, Object> out = gateway.execute(CREATE_IMPORT_BATCH, new MapSqlParameterSource()
                .addValue("p_public_id", lote.publicId())
                .addValue("p_file_name", lote.fileName())
                .addValue("p_sha256_hash", lote.hashSha256())
                .addValue("p_eps_id", Objects.requireNonNull(lote.epsId()))
                .addValue("p_total_rows", lote.totalRows())
                .addValue("p_valid_rows", lote.validRows())
                .addValue("p_failed_rows", lote.failedRows())
                .addValue("p_status", lote.status(), Types.VARCHAR)
                .addValue("p_uploaded_by_user_id", Objects.requireNonNull(lote.uploadedByUserId())));

        Number key = (Number) out.get("o_id");
        Long id = key != null ? key.longValue() : null;
        return findById(id).orElseThrow(() -> new IllegalStateException("Error al recuperar lote guardado"));
    }

    public Optional<EpsImportBatch> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return gateway.single(GET_IMPORT_BATCH_BY_ID, new MapSqlParameterSource("p_id", id), "o_row");
    }

    public Optional<EpsImportBatch> findByPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        return gateway.single(GET_IMPORT_BATCH_BY_PUBLIC_ID,
                new MapSqlParameterSource("p_public_id", publicId), "o_row");
    }

    public void saveBatchDetails(Long loteId, List<EpsImportDetail> detalles) {
        if (detalles == null || detalles.isEmpty()) {
            return;
        }
        for (EpsImportDetail d : detalles) {
            gateway.execute(CREATE_IMPORT_BATCH_ITEM, new MapSqlParameterSource()
                    .addValue("p_batch_id", loteId)
                    .addValue("p_row_number", d.rowNumber())
                    .addValue("p_document_type", d.documentType(), Types.VARCHAR)
                    .addValue("p_document_number", d.documentNumber(), Types.VARCHAR)
                    .addValue("p_first_names", d.firstNames(), Types.VARCHAR)
                    .addValue("p_last_names", d.lastNames(), Types.VARCHAR)
                    .addValue("p_regime", d.regime(), Types.VARCHAR)
                    .addValue("p_affiliate_type", d.affiliateType(), Types.VARCHAR)
                    .addValue("p_row_status", d.rowStatus())
                    .addValue("p_error_reason", d.errorReason(), Types.VARCHAR));
        }
    }

    public void updateTotals(Long loteId, int total, int validas, int fallidas) {
        gateway.execute(UPDATE_IMPORT_BATCH_TOTALS, new MapSqlParameterSource()
                .addValue("p_batch_id", loteId)
                .addValue("p_total", total)
                .addValue("p_valid", validas)
                .addValue("p_failed", fallidas));
    }

    public void changeBatchStatus(Long loteId, String nuevoEstado, Instant procesadoAt) {
        gateway.execute(UPDATE_IMPORT_BATCH_STATUS, new MapSqlParameterSource()
                .addValue("p_batch_id", loteId)
                .addValue("p_status", nuevoEstado)
                .addValue("p_processed_at", procesadoAt != null ? Timestamp.from(procesadoAt) : null, Types.TIMESTAMP));
    }

    public List<EpsImportDetail> listDetails(Long loteId, int limite) {
        return gateway.list(LIST_IMPORT_BATCH_ITEMS, new MapSqlParameterSource()
                .addValue("p_batch_id", loteId)
                .addValue("p_limit", limite), "o_rows");
    }

    public List<EpsImportDetail> listValidRows(Long loteId) {
        return gateway.list(LIST_VALID_IMPORT_BATCH_ITEMS,
                new MapSqlParameterSource("p_batch_id", loteId), "o_rows");
    }
}
