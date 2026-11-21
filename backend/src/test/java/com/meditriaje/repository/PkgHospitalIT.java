package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.hospital.CamaDetalleResponse;
import com.meditriaje.dto.hospital.MovimientoResponse;
import com.meditriaje.dto.hospital.ProcedimientoResponse;
import com.meditriaje.model.AreaHospitalaria;
import com.meditriaje.model.CamaHospitalaria;
import com.meditriaje.model.EgresoHospitalario;
import com.meditriaje.model.HabitacionSala;
import com.meditriaje.model.MovimientoPaciente;
import com.meditriaje.model.OcupacionCama;
import com.meditriaje.model.ProcedimientoHospitalario;
import com.meditriaje.repository.plsql.OracleErrorTranslator;
import com.meditriaje.repository.plsql.PackageGateway;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * {@code PKG_HOSPITAL} contra Oracle Free real, llamado como el usuario de aplicación (MEDITRIAJE_APP) a través de
 * los repositorios migrados: áreas, habitaciones, camas, ocupación, movimientos, egresos y procedimientos.
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PkgHospitalIT {

    private static final String OWNER = "MT_PKG_HOSPITAL";

    private static OracleContainer oracle;
    private static JdbcTemplate owner;
    private static AreaHospitalariaRepository areas;
    private static HabitacionSalaRepository rooms;
    private static CamaHospitalariaRepository beds;
    private static OcupacionCamaRepository occupancies;
    private static MovimientoPacienteRepository movements;
    private static EgresoHospitalarioRepository discharges;
    private static ProcedimientoHospitalarioRepository procedures;

    private static Long siteId;
    private static Long episodeId;
    private static Long episode2Id;
    private static Long staffUserId;
    private static Long professionalId;

    private static AreaHospitalaria area;
    private static HabitacionSala room;
    private static CamaHospitalaria bed;
    private static CamaHospitalaria bed2;

    @BeforeAll
    static void migrate() {
        oracle = OracleTestDatabase.newContainer().withStartupTimeout(Duration.ofMinutes(10));
        oracle.start();
        DataSource ownerDs = OracleTestDatabase.provisionOwner(oracle, OWNER);
        OracleTestDatabase.flyway(ownerDs).migrate();
        OracleTestDatabase.runScript(ownerDs, "db/pkg-clinical-fixture.sql");
        owner = new JdbcTemplate(ownerDs);
        DataSource appDs =
                OracleTestDatabase.dataSource(oracle, OracleTestDatabase.APP_USER, OracleTestDatabase.APP_PASSWORD);
        PackageGateway gateway = new PackageGateway(appDs, OWNER, new OracleErrorTranslator());
        areas = new AreaHospitalariaRepository(gateway);
        rooms = new HabitacionSalaRepository(gateway);
        beds = new CamaHospitalariaRepository(gateway);
        occupancies = new OcupacionCamaRepository(gateway);
        movements = new MovimientoPacienteRepository(gateway);
        discharges = new EgresoHospitalarioRepository(gateway);
        procedures = new ProcedimientoHospitalarioRepository(gateway);

        siteId = owner.queryForObject("SELECT MIN(ID) FROM CONFIGURACION_HOSPITAL", Long.class);
        episodeId = idOf("EPISODIO_ATENCION", "fx-episode-1");
        episode2Id = idOf("EPISODIO_ATENCION", "fx-episode-2");
        staffUserId = idOf("USUARIO", "fx-user-staff");
        professionalId = idOf("PROFESIONAL", "fx-prof-1");
    }

    @AfterAll
    static void stop() {
        if (oracle != null) {
            oracle.stop();
        }
    }

    private static Long idOf(String table, String publicId) {
        return owner.queryForObject("SELECT ID FROM " + table + " WHERE PUBLIC_ID = ?", Long.class, publicId);
    }

    @Test
    @Order(1)
    void areasAreCreatedOnTheSingleSiteAndListedByCode() {
        area = areas.guardar(new AreaHospitalaria(
                null, uuid(), 999L, "URG-B", "Urgencias B", "URGENCIAS", "1", null, null));
        AreaHospitalaria other = areas.guardar(new AreaHospitalaria(
                null, uuid(), null, "OBS-A", "Observacion A", "OBSERVACION", null, "ACTIVA", null));

        assertThat(area.id()).isNotNull();
        assertThat(area.sedeId()).isEqualTo(siteId);
        assertThat(area.estado()).isEqualTo("ACTIVA");
        assertThat(area.creadoAt()).isNotNull();
        assertThat(areas.buscarPorPublicId(area.publicId())).contains(area);
        assertThat(areas.buscarPorPublicId("no-existe")).isEmpty();
        assertThat(areas.buscarPorId(null)).isEmpty();
        assertThat(areas.listarPorSedeId(siteId)).extracting(AreaHospitalaria::codigo)
                .containsExactly(other.codigo(), area.codigo());
        assertThat(areas.listarPorSedeId(-1L)).isEmpty();
    }

    @Test
    @Order(2)
    void roomsAndBedsAreCreatedAndReservedAtomically() {
        room = rooms.guardar(new HabitacionSala(null, uuid(), area.id(), "H-101", null, null, null));
        assertThat(room.tipo()).isEqualTo("COMPARTIDA");
        assertThat(rooms.buscarPorPublicId(room.publicId())).contains(room);
        assertThat(rooms.listarPorAreaId(area.id())).containsExactly(room);

        bed = beds.guardar(new CamaHospitalaria(null, uuid(), room.id(), "C-2", null, null, null));
        bed2 = beds.guardar(new CamaHospitalaria(null, uuid(), room.id(), "C-1", "DISPONIBLE", null, null));
        assertThat(bed.estado()).isEqualTo("DISPONIBLE");
        assertThat(beds.buscarPorPublicId(bed.publicId())).contains(bed);

        assertThat(beds.reservarCamaSiDisponible(bed.id())).isTrue();
        assertThat(beds.reservarCamaSiDisponible(bed.id())).isFalse();
        beds.actualizarEstado(bed2.id(), "LIMPIEZA");
        assertThat(beds.buscarPorId(bed2.id())).get().extracting(CamaHospitalaria::estado).isEqualTo("LIMPIEZA");
        beds.actualizarEstado(bed2.id(), "DISPONIBLE");
    }

    @Test
    @Order(3)
    void occupancyIsUniquePerBedAndShowsInTheCensus() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        OcupacionCama occupancy = occupancies.guardar(new OcupacionCama(
                null, uuid(), episodeId, bed.id(), now, null, null, staffUserId, "Ingreso", null));
        assertThat(occupancy.estado()).isEqualTo("ACTIVA");
        assertThat(occupancy.inicioAt()).isEqualTo(now);
        assertThat(occupancies.buscarActivaPorCamaId(bed.id())).contains(occupancy);
        assertThat(occupancies.buscarActivaPorEpisodioId(episodeId)).contains(occupancy);

        // Misma cama para otro episodio: el índice único lo rechaza y el servicio recibe la misma excepción de antes.
        assertThatThrownBy(() -> occupancies.guardar(new OcupacionCama(
                        null, uuid(), episode2Id, bed.id(), now, null, "ACTIVA", staffUserId, null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);

        List<CamaDetalleResponse> census = beds.listarDetallePorSedeId(siteId);
        assertThat(census).extracting(CamaDetalleResponse::codigo).containsExactly("C-1", "C-2");
        CamaDetalleResponse occupied = beds.buscarDetallePorPublicId(bed.publicId()).orElseThrow();
        assertThat(occupied.pacienteNombre()).isEqualTo("Ana Prueba");
        assertThat(occupied.ocupacionPublicId()).isEqualTo(occupancy.publicId());
        assertThat(occupied.episodioPublicId()).isEqualTo("fx-episode-1");
        assertThat(beds.buscarDetallePorPublicId("no-existe")).isEmpty();

        Instant end = now.plusSeconds(60);
        occupancies.finalizarOcupacion(occupancy.id(), end);
        OcupacionCama finished = occupancies.buscarPorId(occupancy.id()).orElseThrow();
        assertThat(finished.estado()).isEqualTo("FINALIZADA");
        assertThat(finished.finAt()).isEqualTo(end);
        assertThat(occupancies.buscarActivaPorCamaId(bed.id())).isEmpty();
    }

    @Test
    @Order(4)
    void movementsAreListedChronologicallyWithNames() {
        Instant t0 = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        MovimientoPaciente first = movements.guardar(new MovimientoPaciente(
                null, uuid(), episodeId, null, area.id(), null, bed.id(), t0, "Asignacion inicial", staffUserId, null));
        movements.guardar(new MovimientoPaciente(
                null, uuid(), episodeId, area.id(), area.id(), bed.id(), bed2.id(), t0.plusSeconds(5), "Traslado",
                staffUserId, null));

        assertThat(first.areaOrigenId()).isNull();
        assertThat(first.camaDestinoId()).isEqualTo(bed.id());
        List<MovimientoResponse> list = movements.listarPorEpisodioId(episodeId);
        assertThat(list).extracting(MovimientoResponse::motivoTraslado).containsExactly("Asignacion inicial", "Traslado");
        assertThat(list.get(0).areaOrigenNombre()).isNull();
        assertThat(list.get(1).camaOrigenCodigo()).isEqualTo("C-2");
        assertThat(list.get(1).registradoPorNombre()).isEqualTo("fx.staff@pkg.test");
    }

    @Test
    @Order(5)
    void proceduresAreUpdatedAndListedNewestFirst() {
        ProcedimientoHospitalario p = procedures.guardar(new ProcedimientoHospitalario(
                null, uuid(), episodeId, "CIRUGIA", "Apendicectomia", professionalId, room.id(), null, null, null,
                "Inicial", null));
        assertThat(p.estado()).isEqualTo("PROGRAMADO");
        assertThat(p.inicioAt()).isNull();
        assertThat(procedures.buscarPorPublicId(p.publicId())).contains(p);

        Instant end = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        procedures.actualizarEstado(p.id(), "FINALIZADO", end, null);
        ProcedimientoHospitalario updated = procedures.buscarPorId(p.id()).orElseThrow();
        assertThat(updated.estado()).isEqualTo("FINALIZADO");
        assertThat(updated.finAt()).isEqualTo(end);
        assertThat(updated.observaciones()).isEqualTo("Inicial");

        List<ProcedimientoResponse> list = procedures.listarPorEpisodioId(episodeId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).profesionalNombre()).isEqualTo("Pedro Medico");
        assertThat(list.get(0).salaCodigo()).isEqualTo("H-101");
    }

    @Test
    @Order(6)
    void dischargeIsFoundByEpisode() {
        Instant at = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        EgresoHospitalario e = discharges.guardar(new EgresoHospitalario(
                null, uuid(), episodeId, professionalId, at, "ALTA_DOMICILIO", "Diagnostico", null, "Plan", null));
        assertThat(e.fechaEgreso()).isEqualTo(at);
        assertThat(discharges.buscarPorEpisodioId(episodeId)).contains(e);
        assertThat(discharges.buscarPorEpisodioId(episode2Id)).isEmpty();
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}
