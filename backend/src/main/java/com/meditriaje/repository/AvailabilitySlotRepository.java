package com.meditriaje.repository;

import com.meditriaje.dto.admin.SlotResponse;
import com.meditriaje.dto.availability.AvailabilitySlotResponse;
import com.meditriaje.model.AvailabilitySlot;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Repositorio para {@code DISPONIBILIDAD_SLOT} a través de {@code PKG_SCHEDULING}.
 * No contiene SQL.
 */
@Repository
public class AvailabilitySlotRepository {

    static final String PACKAGE = "PKG_SCHEDULING";

    static final RowMapper<AvailabilitySlotResponse> AVAILABLE_SLOT_MAPPER = (rs, rowNum) -> {
        Timestamp tsInicio = rs.getTimestamp("FECHA_HORA_INICIO");
        Timestamp tsFin = rs.getTimestamp("FECHA_HORA_FIN");
        Instant inicio = tsInicio != null ? tsInicio.toInstant() : null;
        Instant fin = tsFin != null ? tsFin.toInstant() : null;
        int duracion = (inicio != null && fin != null) ? (int) ChronoUnit.MINUTES.between(inicio, fin) : 0;

        return new AvailabilitySlotResponse(
                rs.getString("SLOT_PUBLIC_ID"),
                rs.getString("PROFESIONAL_PUBLIC_ID"),
                rs.getString("PROFESIONAL_NOMBRE"),
                rs.getString("ESPECIALIDAD_PUBLIC_ID"),
                rs.getString("ESPECIALIDAD_NOMBRE"),
                rs.getString("SEDE_PUBLIC_ID"),
                rs.getString("SEDE_NOMBRE"),
                rs.getString("SEDE_DIRECCION"),
                rs.getString("SEDE_CIUDAD"),
                inicio,
                fin,
                rs.getString("MODALIDAD"),
                duracion);
    };

    static final RowMapper<SlotResponse> SLOT_RESPONSE_MAPPER = (rs, rowNum) -> {
        Timestamp tsInicio = rs.getTimestamp("FECHA_HORA_INICIO");
        Timestamp tsFin = rs.getTimestamp("FECHA_HORA_FIN");
        return new SlotResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("PROFESIONAL_PUBLIC_ID"),
                rs.getString("PROFESIONAL_NOMBRE"),
                rs.getString("SEDE_PUBLIC_ID"),
                rs.getString("SEDE_NOMBRE"),
                rs.getString("ESPECIALIDAD_PUBLIC_ID"),
                rs.getString("ESPECIALIDAD_NOMBRE"),
                tsInicio != null ? tsInicio.toInstant() : null,
                tsFin != null ? tsFin.toInstant() : null,
                rs.getString("MODALIDAD"),
                rs.getString("ESTADO"));
    };

    static final RowMapper<AvailabilitySlot> SLOT_MAPPER = (rs, rowNum) -> {
        Timestamp tsInicio = rs.getTimestamp("FECHA_HORA_INICIO");
        Timestamp tsFin = rs.getTimestamp("FECHA_HORA_FIN");
        return new AvailabilitySlot(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("PROFESIONAL_ID"),
                1L,
                rs.getLong("ESPECIALIDAD_ID"),
                tsInicio != null ? tsInicio.toInstant() : null,
                tsFin != null ? tsFin.toInstant() : null,
                rs.getString("MODALIDAD"),
                rs.getString("ESTADO"));
    };

    static final ProcedureSpec CREATE_SLOT = ProcedureSpec.procedure(PACKAGE, "create_slot")
            .in("p_public_id", Types.VARCHAR)
            .in("p_professional_id", Types.NUMERIC)
            .in("p_specialty_id", Types.NUMERIC)
            .in("p_start_at", Types.TIMESTAMP)
            .in("p_end_at", Types.TIMESTAMP)
            .in("p_modality", Types.VARCHAR)
            .in("p_status", Types.VARCHAR)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_SLOT_DETAIL = ProcedureSpec.procedure(PACKAGE, "get_slot_detail")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_row", SLOT_RESPONSE_MAPPER)
            .build();

    static final ProcedureSpec GET_SLOT_BY_ID = ProcedureSpec.procedure(PACKAGE, "get_slot_by_id")
            .in("p_id", Types.NUMERIC)
            .cursor("o_row", SLOT_MAPPER)
            .build();

    static final ProcedureSpec GET_SLOT_BY_PUBLIC_ID = ProcedureSpec.procedure(PACKAGE, "get_slot_by_public_id")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_row", SLOT_MAPPER)
            .build();

    static final ProcedureSpec COUNT_OVERLAPPING_SLOTS =
            ProcedureSpec.function(PACKAGE, "count_overlapping_slots", "result", Types.NUMERIC)
                    .in("p_professional_id", Types.NUMERIC)
                    .in("p_start_at", Types.TIMESTAMP)
                    .in("p_end_at", Types.TIMESTAMP)
                    .build();

    static final ProcedureSpec UPDATE_SLOT_STATUS = ProcedureSpec.procedure(PACKAGE, "update_slot_status")
            .in("p_public_id", Types.VARCHAR)
            .in("p_status", Types.VARCHAR)
            .build();

    static final ProcedureSpec DELETE_FREE_SLOT = ProcedureSpec.procedure(PACKAGE, "delete_free_slot")
            .in("p_public_id", Types.VARCHAR)
            .out("o_deleted", Types.NUMERIC)
            .build();

    static final ProcedureSpec LIST_SLOTS = ProcedureSpec.procedure(PACKAGE, "list_slots")
            .in("p_professional_public_id", Types.VARCHAR)
            .in("p_site_public_id", Types.VARCHAR)
            .in("p_specialty_public_id", Types.VARCHAR)
            .in("p_from", Types.TIMESTAMP)
            .in("p_to", Types.TIMESTAMP)
            .in("p_status", Types.VARCHAR)
            .in("p_offset", Types.NUMERIC)
            .in("p_limit", Types.NUMERIC)
            .cursor("o_rows", SLOT_RESPONSE_MAPPER)
            .build();

    static final ProcedureSpec COUNT_SLOTS = ProcedureSpec.function(PACKAGE, "count_slots", "result", Types.NUMERIC)
            .in("p_professional_public_id", Types.VARCHAR)
            .in("p_site_public_id", Types.VARCHAR)
            .in("p_specialty_public_id", Types.VARCHAR)
            .in("p_from", Types.TIMESTAMP)
            .in("p_to", Types.TIMESTAMP)
            .in("p_status", Types.VARCHAR)
            .build();

    static final ProcedureSpec LIST_AVAILABLE_SLOTS = ProcedureSpec.procedure(PACKAGE, "list_available_slots")
            .in("p_specialty_public_id", Types.VARCHAR)
            .in("p_professional_public_id", Types.VARCHAR)
            .in("p_site_public_id", Types.VARCHAR)
            .in("p_modality", Types.VARCHAR)
            .in("p_from", Types.TIMESTAMP)
            .in("p_to", Types.TIMESTAMP)
            .in("p_offset", Types.NUMERIC)
            .in("p_limit", Types.NUMERIC)
            .cursor("o_rows", AVAILABLE_SLOT_MAPPER)
            .build();

    static final ProcedureSpec COUNT_AVAILABLE_SLOTS =
            ProcedureSpec.function(PACKAGE, "count_available_slots", "result", Types.NUMERIC)
                    .in("p_specialty_public_id", Types.VARCHAR)
                    .in("p_professional_public_id", Types.VARCHAR)
                    .in("p_site_public_id", Types.VARCHAR)
                    .in("p_modality", Types.VARCHAR)
                    .in("p_from", Types.TIMESTAMP)
                    .in("p_to", Types.TIMESTAMP)
                    .build();

    static final ProcedureSpec RESERVE_SLOT = ProcedureSpec.procedure(PACKAGE, "reserve_slot")
            .in("p_slot_id", Types.NUMERIC)
            .out("o_updated", Types.NUMERIC)
            .build();

    static final ProcedureSpec RELEASE_SLOT = ProcedureSpec.procedure(PACKAGE, "release_slot")
            .in("p_slot_id", Types.NUMERIC)
            .out("o_updated", Types.NUMERIC)
            .build();

    private final PackageGateway gateway;

    public AvailabilitySlotRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    /** Registra un slot en la única sede del hospital (la resuelve el paquete) y devuelve su ID. */
    public Long save(AvailabilitySlot slot) {
        Objects.requireNonNull(slot, "El slot no puede ser nulo");
        Map<String, Object> out = gateway.execute(CREATE_SLOT, new MapSqlParameterSource()
                .addValue("p_public_id", slot.publicId(), Types.VARCHAR)
                .addValue("p_professional_id", slot.professionalId(), Types.NUMERIC)
                .addValue("p_specialty_id", slot.specialtyId(), Types.NUMERIC)
                .addValue("p_start_at", Timestamp.from(slot.startDateTime()), Types.TIMESTAMP)
                .addValue("p_end_at", Timestamp.from(slot.endDateTime()), Types.TIMESTAMP)
                .addValue("p_modality", slot.modality(), Types.VARCHAR)
                .addValue("p_status", slot.status(), Types.VARCHAR));

        Number key = (Number) out.get("o_id");
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para el slot.");
        }
        return key.longValue();
    }

    /** Inserta cada slot dentro de la transacción activa del servicio invocante. */
    public void saveBatch(List<AvailabilitySlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return;
        }
        for (AvailabilitySlot slot : slots) {
            save(slot);
        }
    }

    public Optional<SlotResponse> findByPublicId(String publicId) {
        return first(gateway.list(
                GET_SLOT_DETAIL,
                new MapSqlParameterSource().addValue("p_public_id", publicId, Types.VARCHAR),
                "o_row"));
    }

    public Optional<AvailabilitySlot> findById(Long id) {
        return first(gateway.list(
                GET_SLOT_BY_ID,
                new MapSqlParameterSource().addValue("p_id", id, Types.NUMERIC),
                "o_row"));
    }

    public Optional<AvailabilitySlot> findEntityByPublicId(String publicId) {
        return first(gateway.list(
                GET_SLOT_BY_PUBLIC_ID,
                new MapSqlParameterSource().addValue("p_public_id", publicId, Types.VARCHAR),
                "o_row"));
    }

    public boolean existsOverlap(Long profesionalId, Instant inicio, Instant fin) {
        Number count = gateway.callFunction(
                COUNT_OVERLAPPING_SLOTS,
                new MapSqlParameterSource()
                        .addValue("p_professional_id", profesionalId, Types.NUMERIC)
                        .addValue("p_start_at", toTimestamp(inicio), Types.TIMESTAMP)
                        .addValue("p_end_at", toTimestamp(fin), Types.TIMESTAMP));
        return count != null && count.intValue() > 0;
    }

    public void changeStatus(String publicId, String nuevoEstado) {
        gateway.execute(UPDATE_SLOT_STATUS, new MapSqlParameterSource()
                .addValue("p_public_id", publicId, Types.VARCHAR)
                .addValue("p_status", nuevoEstado, Types.VARCHAR));
    }

    public int delete(String publicId) {
        Map<String, Object> out = gateway.execute(
                DELETE_FREE_SLOT, new MapSqlParameterSource().addValue("p_public_id", publicId, Types.VARCHAR));
        Number deleted = (Number) out.get("o_deleted");
        return deleted != null ? deleted.intValue() : 0;
    }

    public List<SlotResponse> list(
            int page,
            int size,
            String profesionalPublicId,
            String sedePublicId,
            String especialidadPublicId,
            Instant fechaDesde,
            Instant fechaHasta,
            String estado) {
        int offset = Math.max(0, page) * Math.max(1, size);
        int limit = Math.max(1, size);
        return gateway.list(
                LIST_SLOTS,
                new MapSqlParameterSource()
                        .addValue("p_professional_public_id", normalizeTrim(profesionalPublicId), Types.VARCHAR)
                        .addValue("p_site_public_id", normalizeTrim(sedePublicId), Types.VARCHAR)
                        .addValue("p_specialty_public_id", normalizeTrim(especialidadPublicId), Types.VARCHAR)
                        .addValue("p_from", toTimestamp(fechaDesde), Types.TIMESTAMP)
                        .addValue("p_to", toTimestamp(fechaHasta), Types.TIMESTAMP)
                        .addValue("p_status", normalizeUpper(estado), Types.VARCHAR)
                        .addValue("p_offset", offset, Types.NUMERIC)
                        .addValue("p_limit", limit, Types.NUMERIC),
                "o_rows");
    }

    public int count(
            String profesionalPublicId,
            String sedePublicId,
            String especialidadPublicId,
            Instant fechaDesde,
            Instant fechaHasta,
            String estado) {
        Number count = gateway.callFunction(
                COUNT_SLOTS,
                new MapSqlParameterSource()
                        .addValue("p_professional_public_id", normalizeTrim(profesionalPublicId), Types.VARCHAR)
                        .addValue("p_site_public_id", normalizeTrim(sedePublicId), Types.VARCHAR)
                        .addValue("p_specialty_public_id", normalizeTrim(especialidadPublicId), Types.VARCHAR)
                        .addValue("p_from", toTimestamp(fechaDesde), Types.TIMESTAMP)
                        .addValue("p_to", toTimestamp(fechaHasta), Types.TIMESTAMP)
                        .addValue("p_status", normalizeUpper(estado), Types.VARCHAR));
        return count != null ? count.intValue() : 0;
    }

    /** Consulta slots disponibles (LIBRE) para asignación de citas asistenciales (HU-03). */
    public List<AvailabilitySlotResponse> findAvailable(
            String especialidadPublicId,
            String sedePublicId,
            String modalidad,
            Instant fechaDesde,
            Instant fechaHasta,
            int page,
            int size) {
        return findAvailable(
                especialidadPublicId, null, sedePublicId, modalidad, fechaDesde, fechaHasta, page, size);
    }

    /** Consulta slots disponibles (LIBRE) para asignación de citas asistenciales (HU-03). */
    public List<AvailabilitySlotResponse> findAvailable(
            String especialidadPublicId,
            String profesionalPublicId,
            String sedePublicId,
            String modalidad,
            Instant fechaDesde,
            Instant fechaHasta,
            int page,
            int size) {
        int offset = Math.max(0, page) * Math.max(1, size);
        int limit = Math.max(1, size);
        return gateway.list(
                LIST_AVAILABLE_SLOTS,
                new MapSqlParameterSource()
                        .addValue("p_specialty_public_id", normalizeTrim(especialidadPublicId), Types.VARCHAR)
                        .addValue("p_professional_public_id", normalizeTrim(profesionalPublicId), Types.VARCHAR)
                        .addValue("p_site_public_id", normalizeTrim(sedePublicId), Types.VARCHAR)
                        .addValue("p_modality", normalizeUpper(modalidad), Types.VARCHAR)
                        .addValue("p_from", toTimestamp(fechaDesde), Types.TIMESTAMP)
                        .addValue("p_to", toTimestamp(fechaHasta), Types.TIMESTAMP)
                        .addValue("p_offset", offset, Types.NUMERIC)
                        .addValue("p_limit", limit, Types.NUMERIC),
                "o_rows");
    }

    public int countAvailable(
            String especialidadPublicId,
            String sedePublicId,
            String modalidad,
            Instant fechaDesde,
            Instant fechaHasta) {
        return countAvailable(especialidadPublicId, null, sedePublicId, modalidad, fechaDesde, fechaHasta);
    }

    /** Cuenta el total de slots disponibles (LIBRE) para los filtros asistenciales dados. */
    public int countAvailable(
            String especialidadPublicId,
            String profesionalPublicId,
            String sedePublicId,
            String modalidad,
            Instant fechaDesde,
            Instant fechaHasta) {
        Number count = gateway.callFunction(
                COUNT_AVAILABLE_SLOTS,
                new MapSqlParameterSource()
                        .addValue("p_specialty_public_id", normalizeTrim(especialidadPublicId), Types.VARCHAR)
                        .addValue("p_professional_public_id", normalizeTrim(profesionalPublicId), Types.VARCHAR)
                        .addValue("p_site_public_id", normalizeTrim(sedePublicId), Types.VARCHAR)
                        .addValue("p_modality", normalizeUpper(modalidad), Types.VARCHAR)
                        .addValue("p_from", toTimestamp(fechaDesde), Types.TIMESTAMP)
                        .addValue("p_to", toTimestamp(fechaHasta), Types.TIMESTAMP));
        return count != null ? count.intValue() : 0;
    }

    /** Intenta reservar atómicamente un slot libre cambiándolo a OCUPADO (ADR-006, HU-04). */
    public int reserveSlot(Long slotId) {
        Map<String, Object> out = gateway.execute(
                RESERVE_SLOT, new MapSqlParameterSource().addValue("p_slot_id", slotId, Types.NUMERIC));
        Number updated = (Number) out.get("o_updated");
        return updated != null ? updated.intValue() : 0;
    }

    /** Libera un slot previamente ocupado cambiándolo de regreso a LIBRE (ADR-006). */
    public int releaseSlot(Long slotId) {
        Map<String, Object> out = gateway.execute(
                RELEASE_SLOT, new MapSqlParameterSource().addValue("p_slot_id", slotId, Types.NUMERIC));
        Number updated = (Number) out.get("o_updated");
        return updated != null ? updated.intValue() : 0;
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static String normalizeTrim(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private static String normalizeUpper(String value) {
        return value != null && !value.isBlank() ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static <T> Optional<T> first(List<T> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
