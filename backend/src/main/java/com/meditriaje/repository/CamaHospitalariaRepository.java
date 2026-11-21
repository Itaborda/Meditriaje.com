package com.meditriaje.repository;

import com.meditriaje.dto.hospital.CamaDetalleResponse;
import com.meditriaje.model.CamaHospitalaria;
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

/** Camas hospitalarias y censo de camas a través de {@code PKG_HOSPITAL}. No contiene SQL. */
@Repository
public class CamaHospitalariaRepository {

    static final String PACKAGE = "PKG_HOSPITAL";

    static final RowMapper<CamaHospitalaria> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        Timestamp tsUpdated = rs.getTimestamp("UPDATED_AT");
        return new CamaHospitalaria(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("HABITACION_ID"),
                rs.getString("CODIGO"),
                rs.getString("ESTADO"),
                tsCreated != null ? tsCreated.toInstant() : null,
                tsUpdated != null ? tsUpdated.toInstant() : null);
    };

    static final RowMapper<CamaDetalleResponse> DETAIL_MAPPER = (rs, rowNum) -> new CamaDetalleResponse(
            rs.getString("CAMA_PUB_ID"),
            rs.getString("HAB_PUB_ID"),
            rs.getString("HAB_CODIGO"),
            rs.getString("AREA_PUB_ID"),
            rs.getString("AREA_NOMBRE"),
            rs.getString("SEDE_PUB_ID"),
            rs.getString("SEDE_NOMBRE"),
            rs.getString("CAMA_CODIGO"),
            rs.getString("ESTADO"),
            rs.getString("EPISODIO_PUB_ID"),
            rs.getString("PACIENTE_NOMBRE"),
            rs.getString("OCUPACION_PUB_ID"));

    static final ProcedureSpec CREATE_BED = ProcedureSpec.procedure(PACKAGE, "create_bed")
            .in("p_public_id", Types.VARCHAR)
            .in("p_room_id", Types.NUMERIC)
            .in("p_code", Types.VARCHAR)
            .in("p_status", Types.VARCHAR)
            .out("o_id", Types.NUMERIC)
            .build();

    static final ProcedureSpec GET_BED_BY_ID = ProcedureSpec.procedure(PACKAGE, "get_bed_by_id")
            .in("p_id", Types.NUMERIC)
            .cursor("o_row", ROW_MAPPER)
            .build();

    static final ProcedureSpec GET_BED_BY_PUBLIC_ID = ProcedureSpec.procedure(PACKAGE, "get_bed_by_public_id")
            .in("p_public_id", Types.VARCHAR)
            .cursor("o_row", ROW_MAPPER)
            .build();

    static final ProcedureSpec UPDATE_BED_STATUS = ProcedureSpec.procedure(PACKAGE, "update_bed_status")
            .in("p_bed_id", Types.NUMERIC)
            .in("p_status", Types.VARCHAR)
            .build();

    static final ProcedureSpec RESERVE_BED_IF_AVAILABLE =
            ProcedureSpec.procedure(PACKAGE, "reserve_bed_if_available")
                    .in("p_bed_id", Types.NUMERIC)
                    .out("o_reserved", Types.NUMERIC)
                    .build();

    static final ProcedureSpec LIST_BED_DETAILS_BY_SITE =
            ProcedureSpec.procedure(PACKAGE, "list_bed_details_by_site")
                    .in("p_site_id", Types.NUMERIC)
                    .cursor("o_rows", DETAIL_MAPPER)
                    .build();

    static final ProcedureSpec GET_BED_DETAIL_BY_PUBLIC_ID =
            ProcedureSpec.procedure(PACKAGE, "get_bed_detail_by_public_id")
                    .in("p_public_id", Types.VARCHAR)
                    .cursor("o_row", DETAIL_MAPPER)
                    .build();

    private final PackageGateway gateway;

    public CamaHospitalariaRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public CamaHospitalaria guardar(CamaHospitalaria cama) {
        Map<String, Object> out = gateway.execute(CREATE_BED, new MapSqlParameterSource()
                .addValue("p_public_id", cama.publicId())
                .addValue("p_room_id", cama.habitacionId())
                .addValue("p_code", cama.codigo())
                .addValue("p_status", cama.estado() != null ? cama.estado() : "DISPONIBLE"));
        Number key = (Number) out.get("o_id");
        Long id = key != null ? key.longValue() : null;
        return buscarPorId(id).orElseThrow(() -> new IllegalStateException("Error al recuperar cama recién guardada"));
    }

    public Optional<CamaHospitalaria> buscarPorId(Long id) {
        if (id == null) return Optional.empty();
        return first(gateway.list(GET_BED_BY_ID, new MapSqlParameterSource("p_id", id), "o_row"));
    }

    public Optional<CamaHospitalaria> buscarPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) return Optional.empty();
        return first(gateway.list(GET_BED_BY_PUBLIC_ID, new MapSqlParameterSource("p_public_id", publicId), "o_row"));
    }

    public void actualizarEstado(Long camaId, String nuevoEstado) {
        gateway.execute(UPDATE_BED_STATUS, new MapSqlParameterSource()
                .addValue("p_bed_id", camaId)
                .addValue("p_status", nuevoEstado));
    }

    /**
     * Intenta reservar de manera atómica una cama pasando su estado de DISPONIBLE a OCUPADA.
     * Retorna true si se reservó exitosamente (1 fila afectada), o false si ya no estaba DISPONIBLE (O-03).
     */
    public boolean reservarCamaSiDisponible(Long camaId) {
        Map<String, Object> out =
                gateway.execute(RESERVE_BED_IF_AVAILABLE, new MapSqlParameterSource("p_bed_id", camaId));
        Number reserved = (Number) out.get("o_reserved");
        return reserved != null && reserved.intValue() == 1;
    }

    public List<CamaDetalleResponse> listarDetallePorSedeId(Long sedeId) {
        return gateway.list(LIST_BED_DETAILS_BY_SITE, new MapSqlParameterSource("p_site_id", sedeId), "o_rows");
    }

    public Optional<CamaDetalleResponse> buscarDetallePorPublicId(String publicId) {
        return first(gateway.list(
                GET_BED_DETAIL_BY_PUBLIC_ID, new MapSqlParameterSource("p_public_id", publicId), "o_row"));
    }

    private static <T> Optional<T> first(List<T> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
