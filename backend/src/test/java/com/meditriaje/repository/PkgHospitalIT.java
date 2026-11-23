package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.hospital.BedDetailResponse;
import com.meditriaje.dto.hospital.MovementResponse;
import com.meditriaje.dto.hospital.ProcedureResponse;
import com.meditriaje.model.HospitalArea;
import com.meditriaje.model.HospitalBed;
import com.meditriaje.model.HospitalDischarge;
import com.meditriaje.model.HospitalRoom;
import com.meditriaje.model.PatientMovement;
import com.meditriaje.model.BedOccupancy;
import com.meditriaje.model.HospitalProcedure;
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
    private static HospitalAreaRepository areas;
    private static HospitalRoomRepository rooms;
    private static HospitalBedRepository beds;
    private static BedOccupancyRepository occupancies;
    private static PatientMovementRepository movements;
    private static HospitalDischargeRepository discharges;
    private static HospitalProcedureRepository procedures;

    private static Long siteId;
    private static Long episodeId;
    private static Long episode2Id;
    private static Long staffUserId;
    private static Long professionalId;

    private static HospitalArea area;
    private static HospitalRoom room;
    private static HospitalBed bed;
    private static HospitalBed bed2;

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
        areas = new HospitalAreaRepository(gateway);
        rooms = new HospitalRoomRepository(gateway);
        beds = new HospitalBedRepository(gateway);
        occupancies = new BedOccupancyRepository(gateway);
        movements = new PatientMovementRepository(gateway);
        discharges = new HospitalDischargeRepository(gateway);
        procedures = new HospitalProcedureRepository(gateway);

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
        area = areas.save(new HospitalArea(
                null, uuid(), 999L, "URG-B", "Urgencias B", "URGENCIAS", "1", null, null));
        HospitalArea other = areas.save(new HospitalArea(
                null, uuid(), null, "OBS-A", "Observacion A", "OBSERVACION", null, "ACTIVA", null));

        assertThat(area.id()).isNotNull();
        assertThat(area.siteId()).isEqualTo(siteId);
        assertThat(area.status()).isEqualTo("ACTIVA");
        assertThat(area.createdAt()).isNotNull();
        assertThat(areas.findByPublicId(area.publicId())).contains(area);
        assertThat(areas.findByPublicId("no-existe")).isEmpty();
        assertThat(areas.findById(null)).isEmpty();
        assertThat(areas.listBySiteId(siteId)).extracting(HospitalArea::code)
                .contains(other.code(), area.code());
        assertThat(areas.listBySiteId(-1L)).isEmpty();
    }

    @Test
    @Order(2)
    void roomsAndBedsAreCreatedAndReservedAtomically() {
        room = rooms.save(new HospitalRoom(null, uuid(), area.id(), "H-101", null, null, null));
        assertThat(room.type()).isEqualTo("COMPARTIDA");
        assertThat(rooms.findByPublicId(room.publicId())).contains(room);
        assertThat(rooms.listByAreaId(area.id())).containsExactly(room);

        bed = beds.save(new HospitalBed(null, uuid(), room.id(), "C-2", null, null, null));
        bed2 = beds.save(new HospitalBed(null, uuid(), room.id(), "C-1", "DISPONIBLE", null, null));
        assertThat(bed.status()).isEqualTo("DISPONIBLE");
        assertThat(beds.findByPublicId(bed.publicId())).contains(bed);

        assertThat(beds.reserveBedIfAvailable(bed.id())).isTrue();
        assertThat(beds.reserveBedIfAvailable(bed.id())).isFalse();
        beds.updateStatus(bed2.id(), "LIMPIEZA");
        assertThat(beds.findById(bed2.id())).get().extracting(HospitalBed::status).isEqualTo("LIMPIEZA");
        beds.updateStatus(bed2.id(), "DISPONIBLE");
    }

    @Test
    @Order(3)
    void occupancyIsUniquePerBedAndShowsInTheCensus() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        BedOccupancy occupancy = occupancies.save(new BedOccupancy(
                null, uuid(), episodeId, bed.id(), now, null, null, staffUserId, "Ingreso", null));
        assertThat(occupancy.status()).isEqualTo("ACTIVA");
        assertThat(occupancy.startedAt()).isEqualTo(now);
        assertThat(occupancies.findActiveByBedId(bed.id())).contains(occupancy);
        assertThat(occupancies.findActiveByEpisodeId(episodeId)).contains(occupancy);

        // Misma cama para otro episodio: el índice único lo rechaza y el servicio recibe la misma excepción de antes.
        assertThatThrownBy(() -> occupancies.save(new BedOccupancy(
                        null, uuid(), episode2Id, bed.id(), now, null, "ACTIVA", staffUserId, null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);

        List<BedDetailResponse> census = beds.listDetailsBySiteId(siteId);
        assertThat(census).extracting(BedDetailResponse::code).contains("C-1", "C-2");
        BedDetailResponse occupied = beds.findDetailByPublicId(bed.publicId()).orElseThrow();
        assertThat(occupied.patientName()).isEqualTo("Ana Prueba");
        assertThat(occupied.occupancyPublicId()).isEqualTo(occupancy.publicId());
        assertThat(occupied.episodePublicId()).isEqualTo("fx-episode-1");
        assertThat(beds.findDetailByPublicId("no-existe")).isEmpty();

        Instant end = now.plusSeconds(60);
        occupancies.finishOccupancy(occupancy.id(), end);
        BedOccupancy finished = occupancies.findById(occupancy.id()).orElseThrow();
        assertThat(finished.status()).isEqualTo("FINALIZADA");
        assertThat(finished.endedAt()).isEqualTo(end);
        assertThat(occupancies.findActiveByBedId(bed.id())).isEmpty();
    }

    @Test
    @Order(4)
    void movementsAreListedChronologicallyWithNames() {
        Instant t0 = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        PatientMovement first = movements.save(new PatientMovement(
                null, uuid(), episodeId, null, area.id(), null, bed.id(), t0, "Asignacion inicial", staffUserId, null));
        movements.save(new PatientMovement(
                null, uuid(), episodeId, area.id(), area.id(), bed.id(), bed2.id(), t0.plusSeconds(5), "Traslado",
                staffUserId, null));

        assertThat(first.originAreaId()).isNull();
        assertThat(first.destinationBedId()).isEqualTo(bed.id());
        List<MovementResponse> list = movements.listByEpisodeId(episodeId);
        assertThat(list).extracting(MovementResponse::transferReason).containsExactly("Asignacion inicial", "Traslado");
        assertThat(list.get(0).originAreaName()).isNull();
        assertThat(list.get(1).originBedCode()).isEqualTo("C-2");
        assertThat(list.get(1).registeredByName()).isEqualTo("fx.staff@pkg.test");
    }

    @Test
    @Order(5)
    void proceduresAreUpdatedAndListedNewestFirst() {
        HospitalProcedure p = procedures.save(new HospitalProcedure(
                null, uuid(), episodeId, "CIRUGIA", "Apendicectomia", professionalId, room.id(), null, null, null,
                "Inicial", null));
        assertThat(p.status()).isEqualTo("PROGRAMADO");
        assertThat(p.startedAt()).isNull();
        assertThat(procedures.findByPublicId(p.publicId())).contains(p);

        Instant end = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        procedures.updateStatus(p.id(), "FINALIZADO", end, null);
        HospitalProcedure updated = procedures.findById(p.id()).orElseThrow();
        assertThat(updated.status()).isEqualTo("FINALIZADO");
        assertThat(updated.endedAt()).isEqualTo(end);
        assertThat(updated.notes()).isEqualTo("Inicial");

        List<ProcedureResponse> list = procedures.listByEpisodeId(episodeId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).professionalName()).isEqualTo("Pedro Medico");
        assertThat(list.get(0).roomCode()).isEqualTo("H-101");
    }

    @Test
    @Order(6)
    void dischargeIsFoundByEpisode() {
        Instant at = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        HospitalDischarge e = discharges.save(new HospitalDischarge(
                null, uuid(), episodeId, professionalId, at, "ALTA_DOMICILIO", "Diagnostico", null, "Plan", null));
        assertThat(e.dischargeDate()).isEqualTo(at);
        assertThat(discharges.findByEpisodeId(episodeId)).contains(e);
        assertThat(discharges.findByEpisodeId(episode2Id)).isEmpty();
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}
