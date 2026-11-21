package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.emergency.EmergencyEpisodeResponse;
import com.meditriaje.dto.emergency.EmergencyQueueItemResponse;
import com.meditriaje.model.CareEpisode;
import com.meditriaje.model.EmergencyAdmission;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * {@code PKG_EMERGENCY} contra Oracle Free real, llamado como el usuario de aplicación (MEDITRIAJE_APP) a través de
 * {@link EpisodioAtencionRepository} e {@link IngresoUrgenciaRepository}.
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class PkgEmergencyIT {

    private static final String OWNER = "MT_PKG_EMERGENCY";

    private static OracleContainer oracle;
    private static JdbcTemplate owner;
    private static CareEpisodeRepository episodes;
    private static EmergencyAdmissionRepository admissions;
    private static Long siteId;
    private static Long patientId;
    private static Long staffUserId;
    private static Long professionalId;

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
        episodes = new CareEpisodeRepository(gateway);
        admissions = new EmergencyAdmissionRepository(gateway);

        siteId = owner.queryForObject("SELECT MIN(ID) FROM CONFIGURACION_HOSPITAL", Long.class);
        patientId = owner.queryForObject("SELECT ID FROM PACIENTE WHERE PUBLIC_ID = 'fx-patient-1'", Long.class);
        staffUserId = owner.queryForObject("SELECT ID FROM USUARIO WHERE PUBLIC_ID = 'fx-user-staff'", Long.class);
        professionalId = owner.queryForObject("SELECT ID FROM PROFESIONAL WHERE PUBLIC_ID = 'fx-prof-1'", Long.class);
    }

    @AfterAll
    static void stop() {
        if (oracle != null) {
            oracle.stop();
        }
    }

    @Test
    void episodeLifecycleUsesTheSingleSite() {
        Instant admitted = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        CareEpisode saved = episodes.save(newEpisode(null, admitted, "REGISTRADO"));

        assertThat(saved.id()).isNotNull();
        CareEpisode read = episodes.findByPublicId(saved.publicId()).orElseThrow();
        assertThat(read.siteId()).isEqualTo(siteId);
        assertThat(read.patientId()).isNull();
        assertThat(read.admittedAt()).isEqualTo(admitted);
        assertThat(episodes.findById(saved.id())).contains(read);
        assertThat(episodes.findByPublicId("no-existe")).isEmpty();

        assertThat(episodes.updateStatus(saved.id(), "EN_TRIAJE")).isEqualTo(1);
        assertThat(episodes.linkPatient(saved.id(), patientId)).isEqualTo(1);
        assertThat(episodes.updateStatus(-1L, "EN_TRIAJE")).isZero();

        Instant discharged = admitted.plusSeconds(3600);
        assertThat(episodes.closeEpisode(saved.id(), discharged)).isEqualTo(1);
        CareEpisode closed = episodes.findById(saved.id()).orElseThrow();
        assertThat(closed.status()).isEqualTo("EGRESADO");
        assertThat(closed.dischargedAt()).isEqualTo(discharged);
        assertThat(closed.patientId()).isEqualTo(patientId);
    }

    @Test
    void admissionAndDetailShowTriageLevelProvisionalIdentityAndDoctor() {
        CareEpisode episode = episodes.save(newEpisode(null, Instant.now(), "REGISTRADO"));
        EmergencyAdmission admission = admissions.save(new EmergencyAdmission(
                null, UUID.randomUUID().toString(), episode.id(), "AMBULANCIA", "Acompanante", "3000000000",
                "Dolor toracico", null, staffUserId, null));
        assertThat(admission.id()).isNotNull();
        assertThat(admissions.findByEpisodeId(episode.id())).get()
                .extracting(EmergencyAdmission::arrivalMode).isEqualTo("AMBULANCIA");
        assertThat(admissions.findByEpisodeId(-1L)).isEmpty();

        EmergencyEpisodeResponse pending = episodes.findDetailByPublicId(episode.publicId()).orElseThrow();
        assertThat(pending.currentTriageLevel()).isEqualTo("PENDIENTE_VALORACION");
        assertThat(pending.isProvisionalIdentity()).isFalse();
        assertThat(pending.arrivalReason()).isEqualTo("Dolor toracico");

        owner.update("INSERT INTO IDENTIDAD_PROVISIONAL (PUBLIC_ID, EPISODIO_ID, CODIGO_PROVISIONAL) VALUES (?, ?, ?)",
                UUID.randomUUID().toString(), episode.id(), "NN-0001");
        insertTriage(episode.id(), 1, "III");
        insertTriage(episode.id(), 2, "II");
        owner.update("INSERT INTO ASIGNACION_ASISTENCIAL (PUBLIC_ID, EPISODIO_ID, PROFESIONAL_ID, FUNCION, "
                        + "ASIGNADO_POR_USUARIO_ID) VALUES (?, ?, ?, 'MEDICO_TRATANTE', ?)",
                UUID.randomUUID().toString(), episode.id(), professionalId, staffUserId);

        EmergencyEpisodeResponse detail = episodes.findDetailByPublicId(episode.publicId()).orElseThrow();
        assertThat(detail.currentTriageLevel()).isEqualTo("II");
        assertThat(detail.isProvisionalIdentity()).isTrue();
        assertThat(detail.provisionalCode()).isEqualTo("NN-0001");
        assertThat(detail.attendingProfessionalName()).isEqualTo("Pedro Medico");
        assertThat(episodes.findDetailByPublicId("no-existe")).isEmpty();
    }

    @Test
    void queueIsOrderedByTriageLevelThenArrivalAndHonoursFilters() {
        Instant base = Instant.now().minus(2, ChronoUnit.HOURS);
        CareEpisode pending = episodes.save(newEpisode(null, base, "OBSERVACION"));
        CareEpisode levelTwo = episodes.save(newEpisode(null, base.plusSeconds(60), "OBSERVACION"));
        CareEpisode levelOne = episodes.save(newEpisode(null, base.plusSeconds(120), "OBSERVACION"));
        CareEpisode levelTwoLater = episodes.save(newEpisode(null, base.plusSeconds(180), "OBSERVACION"));
        insertTriage(levelTwo.id(), 1, "II");
        insertTriage(levelOne.id(), 1, "I");
        insertTriage(levelTwoLater.id(), 1, "II");

        List<EmergencyQueueItemResponse> queue = episodes.listEmergencyQueueBySite(null, "OBSERVACION");
        assertThat(queue).extracting(EmergencyQueueItemResponse::episodePublicId).containsExactly(
                levelOne.publicId(), levelTwo.publicId(), levelTwoLater.publicId(), pending.publicId());
        assertThat(queue.get(3).triageLevel()).isEqualTo("PENDIENTE_VALORACION");
        assertThat(queue.get(3).visibleIdentifier()).isEqualTo("Paciente no identificado");
        assertThat(queue.get(0).waitMinutes()).isBetween(100L, 130L);

        assertThat(episodes.listEmergencyQueueBySite(siteId, " ")).extracting(EmergencyQueueItemResponse::episodePublicId)
                .contains(pending.publicId(), levelOne.publicId());
        assertThat(episodes.listEmergencyQueueBySite(-1L, null)).isEmpty();

        episodes.closeEpisode(levelOne.id(), null);
        assertThat(episodes.listEmergencyQueueBySite(null, "OBSERVACION"))
                .extracting(EmergencyQueueItemResponse::episodePublicId).doesNotContain(levelOne.publicId());
    }

    private static CareEpisode newEpisode(Long patient, Instant admitted, String status) {
        return new CareEpisode(null, UUID.randomUUID().toString(), patient, "URGENCIA", siteId, status,
                admitted, null, "Motivo de ingreso", null, null);
    }

    private static void insertTriage(Long episodeId, int version, String level) {
        owner.update("INSERT INTO VALORACION_TRIAJE (PUBLIC_ID, EPISODIO_ID, VERSION, NIVEL, MOTIVO_CONSULTA, "
                        + "EVALUADOR_USUARIO_ID) VALUES (?, ?, ?, ?, 'Motivo', ?)",
                UUID.randomUUID().toString(), episodeId, version, level, staffUserId);
    }
}
