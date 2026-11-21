package com.meditriaje.repository;

import com.meditriaje.dto.prescription.RecetaDetalleResponse;
import com.meditriaje.dto.prescription.RecetaResponse;
import com.meditriaje.model.Receta;
import com.meditriaje.model.RecetaDetalle;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Repositorio de {@code RECETA} y {@code RECETA_DETALLE} a través de {@code PKG_PHARMACY}
 * (ADR-001, ADR-007, ADR-008, HU-08). No contiene SQL: declara las rutinas del paquete y delega en
 * {@link PackageGateway}.
 */
@Repository
public class RecetaRepository {

    static final RowMapper<Receta> PRESCRIPTION_MAPPER = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("CREATED_AT");
        return new Receta(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("ATENCION_ID"),
                rs.getLong("PACIENTE_ID"),
                rs.getLong("PROFESIONAL_ID"),
                rs.getInt("VIGENCIA_DIAS"),
                ts != null ? ts.toInstant() : null);
    };

    /** Cabecera de vista sin detalles; se completan tras cerrar el cursor. */
    record PrescriptionHeaderRow(Long prescriptionId, RecetaResponse base) {}

    static final RowMapper<PrescriptionHeaderRow> HEADER_MAPPER = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        return new PrescriptionHeaderRow(rs.getLong("RECETA_ID"), new RecetaResponse(
                rs.getString("RECETA_PUBLIC_ID"),
                rs.getString("ATENCION_PUBLIC_ID"),
                rs.getString("PACIENTE_PUBLIC_ID"),
                rs.getString("PACIENTE_NOMBRE"),
                rs.getString("PROFESIONAL_PUBLIC_ID"),
                rs.getString("PROFESIONAL_NOMBRE"),
                rs.getString("ESPECIALIDAD_NOMBRE"),
                rs.getInt("VIGENCIA_DIAS"),
                tsCreated != null ? tsCreated.toInstant() : null,
                List.of()));
    };

    static final RowMapper<RecetaDetalleResponse> ITEM_MAPPER = (rs, rowNum) -> new RecetaDetalleResponse(
            rs.getString("MEDICAMENTO_PUBLIC_ID"),
            rs.getString("MEDICAMENTO_CODIGO"),
            rs.getString("SNAPSHOT_NOMBRE"),
            rs.getString("SNAPSHOT_PRINCIPIO_ACTIVO"),
            rs.getString("SNAPSHOT_PRESENTACION"),
            rs.getString("SNAPSHOT_CONCENTRACION"),
            rs.getString("DOSIS"),
            rs.getString("FRECUENCIA"),
            rs.getInt("DURACION_DIAS"),
            rs.getInt("CANTIDAD"),
            rs.getString("INDICACIONES"));

    static final ProcedureSpec CREATE_PRESCRIPTION = ProcedureSpec.procedure(
                    MedicamentoRepository.PACKAGE, "create_prescription")
            .in("p_public_id", Types.VARCHAR)
            .in("p_encounter_id", Types.NUMERIC)
            .in("p_patient_id", Types.NUMERIC)
            .in("p_professional_id", Types.NUMERIC)
            .in("p_validity_days", Types.NUMERIC)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec CREATE_PRESCRIPTION_ITEM = ProcedureSpec.procedure(
                    MedicamentoRepository.PACKAGE, "create_prescription_item")
            .in("p_prescription_id", Types.NUMERIC)
            .in("p_medication_id", Types.NUMERIC)
            .in("p_snapshot_name", Types.VARCHAR)
            .in("p_snapshot_active_ingredient", Types.VARCHAR)
            .in("p_snapshot_presentation", Types.VARCHAR)
            .in("p_snapshot_concentration", Types.VARCHAR)
            .in("p_dose", Types.VARCHAR)
            .in("p_frequency", Types.VARCHAR)
            .in("p_duration_days", Types.NUMERIC)
            .in("p_quantity", Types.NUMERIC)
            .in("p_instructions", Types.VARCHAR)
            .build();

    static final ProcedureSpec GET_PRESCRIPTION_VIEW_BY_PUBLIC_ID = ProcedureSpec.procedure(
                    MedicamentoRepository.PACKAGE, "get_prescription_view_by_public_id")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_row", HEADER_MAPPER)
            .build();

    static final ProcedureSpec GET_PRESCRIPTION_BY_PUBLIC_ID = ProcedureSpec.procedure(
                    MedicamentoRepository.PACKAGE, "get_prescription_by_public_id")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_row", PRESCRIPTION_MAPPER)
            .build();

    static final ProcedureSpec LIST_PRESCRIPTION_ITEMS = ProcedureSpec.procedure(
                    MedicamentoRepository.PACKAGE, "list_prescription_items")
            .in("p_prescription_id", Types.NUMERIC)
            .cursor("o_rows", ITEM_MAPPER)
            .build();

    static final ProcedureSpec COUNT_PRESCRIPTIONS_BY_ENCOUNTER = ProcedureSpec.function(
                    MedicamentoRepository.PACKAGE, "count_prescriptions_by_encounter", "result", Types.NUMERIC)
            .in("p_encounter_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec LIST_PRESCRIPTIONS_BY_PATIENT = ProcedureSpec.procedure(
                    MedicamentoRepository.PACKAGE, "list_prescriptions_by_patient")
            .in("p_patient_id", Types.NUMERIC)
            .in("p_offset", Types.NUMERIC)
            .in("p_size", Types.NUMERIC)
            .cursor("o_rows", HEADER_MAPPER)
            .build();

    static final ProcedureSpec COUNT_PRESCRIPTIONS_BY_PATIENT = ProcedureSpec.function(
                    MedicamentoRepository.PACKAGE, "count_prescriptions_by_patient", "result", Types.NUMERIC)
            .in("p_patient_id", Types.NUMERIC)
            .build();

    private final PackageGateway gateway;

    public RecetaRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    /**
     * Persiste una nueva receta médica. Inmutable desde su inserción (ADR-008).
     *
     * @param receta Entidad receta a persistir
     * @return Identificador numérico generado en base de datos
     */
    public Long crearReceta(Receta receta) {
        Objects.requireNonNull(receta, "receta no puede ser nula");

        Map<String, Object> out = gateway.execute(CREATE_PRESCRIPTION, new MapSqlParameterSource()
                .addValue("p_public_id", receta.publicId())
                .addValue("p_encounter_id", Objects.requireNonNull(receta.atencionId()))
                .addValue("p_patient_id", Objects.requireNonNull(receta.pacienteId()))
                .addValue("p_professional_id", Objects.requireNonNull(receta.profesionalId()))
                .addValue("p_validity_days", receta.vigenciaDias() > 0 ? receta.vigenciaDias() : 30));

        Number key = (Number) out.get("o_id");
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para la receta.");
        }
        return key.longValue();
    }

    /**
     * Guarda los ítems prescritos en RECETA_DETALLE congelando los snapshots de fármaco.
     *
     * @param recetaId Identificador interno de la receta cabecera
     * @param detalles Lista de detalles con datos históricos
     */
    public void guardarDetalles(Long recetaId, List<RecetaDetalle> detalles) {
        if (detalles == null || detalles.isEmpty()) {
            return;
        }

        for (RecetaDetalle d : detalles) {
            gateway.execute(CREATE_PRESCRIPTION_ITEM, new MapSqlParameterSource()
                    .addValue("p_prescription_id", recetaId != null ? recetaId : d.recetaId())
                    .addValue("p_medication_id", d.medicamentoId())
                    .addValue("p_snapshot_name", d.snapshotNombre())
                    .addValue("p_snapshot_active_ingredient", d.snapshotPrincipioActivo())
                    .addValue("p_snapshot_presentation", d.snapshotPresentacion())
                    .addValue("p_snapshot_concentration", d.snapshotConcentracion())
                    .addValue("p_dose", d.dosis())
                    .addValue("p_frequency", d.frecuencia())
                    .addValue("p_duration_days", d.duracionDias())
                    .addValue("p_quantity", d.cantidad())
                    .addValue("p_instructions", d.indicaciones(), Types.VARCHAR));
        }
    }

    /**
     * Consulta detallada de una receta médica con JOINs relacionales y detalles de prescripción (HU-08).
     *
     * @param publicId UUID público de la receta
     * @return RecetaResponse con detalles si existe
     */
    public Optional<RecetaResponse> buscarPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        return gateway.<PrescriptionHeaderRow>single(GET_PRESCRIPTION_VIEW_BY_PUBLIC_ID,
                        new MapSqlParameterSource("p_public_id", publicId.trim()), "o_row")
                .map(this::complete);
    }

    /**
     * Consulta la entidad interna inmutable {@link Receta} por su UUID público.
     *
     * @param publicId UUID público de la receta
     * @return Entidad Receta si existe
     */
    public Optional<Receta> buscarEntidadPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        return gateway.single(GET_PRESCRIPTION_BY_PUBLIC_ID,
                new MapSqlParameterSource("p_public_id", publicId.trim()), "o_row");
    }

    /**
     * Consulta los detalles de una receta médica asegurando la inmutabilidad histórica
     * a través de los campos SNAPSHOT_*.
     *
     * @param recetaId Identificador interno de la receta
     * @return Lista de detalles con datos históricos
     */
    public List<RecetaDetalleResponse> buscarDetallesPorRecetaId(Long recetaId) {
        if (recetaId == null) {
            return List.of();
        }
        return gateway.list(LIST_PRESCRIPTION_ITEMS,
                new MapSqlParameterSource("p_prescription_id", recetaId), "o_rows");
    }

    /**
     * Verifica si ya existe una receta médica emitida para una atención determinada.
     *
     * @param atencionId Identificador interno de la atención
     * @return true si existe al menos una receta para la atención
     */
    public boolean existePorAtencionId(Long atencionId) {
        if (atencionId == null) {
            return false;
        }
        Number count = gateway.callFunction(COUNT_PRESCRIPTIONS_BY_ENCOUNTER,
                new MapSqlParameterSource("p_encounter_id", atencionId));
        return count != null && count.longValue() > 0;
    }

    /**
     * Consulta paginada de recetas médicas emitidas para un paciente específico (HU-08, HU-09).
     *
     * @param pacienteId Identificador interno del paciente
     * @param page Número de página (0-indexed)
     * @param size Cantidad de elementos por página
     * @return Lista de recetas médicas del paciente con sus respectivos detalles
     */
    public List<RecetaResponse> listarPorPacienteId(Long pacienteId, int page, int size) {
        if (pacienteId == null) {
            return List.of();
        }

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int offset = safePage * safeSize;

        List<PrescriptionHeaderRow> rows = gateway.list(LIST_PRESCRIPTIONS_BY_PATIENT, new MapSqlParameterSource()
                .addValue("p_patient_id", pacienteId)
                .addValue("p_offset", offset)
                .addValue("p_size", safeSize), "o_rows");
        return rows.stream().map(this::complete).toList();
    }

    /**
     * Cuenta el total de recetas emitidas para un paciente (HU-08, HU-09).
     *
     * @param pacienteId Identificador interno del paciente
     * @return Total de recetas
     */
    public int contarPorPacienteId(Long pacienteId) {
        if (pacienteId == null) {
            return 0;
        }
        Number count = gateway.callFunction(COUNT_PRESCRIPTIONS_BY_PATIENT,
                new MapSqlParameterSource("p_patient_id", pacienteId));
        return count != null ? count.intValue() : 0;
    }

    private RecetaResponse complete(PrescriptionHeaderRow row) {
        RecetaResponse b = row.base();
        List<RecetaDetalleResponse> detalles = buscarDetallesPorRecetaId(row.prescriptionId());
        return new RecetaResponse(
                b.publicId(),
                b.atencionPublicId(),
                b.pacientePublicId(),
                b.pacienteNombre(),
                b.profesionalPublicId(),
                b.profesionalNombre(),
                b.especialidadNombre(),
                b.vigenciaDias(),
                b.createdAt(),
                detalles);
    }
}
