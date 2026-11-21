package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.emergency.EpisodioUrgenciaResponse;
import com.meditriaje.dto.emergency.ItemColaUrgenciaResponse;
import com.meditriaje.model.EpisodioAtencion;
import com.meditriaje.model.IngresoUrgencia;
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
    private static EpisodioAtencionRepository episodes;
    private static IngresoUrgenciaRepository admissions;
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
        episodes = new EpisodioAtencionRepository(gateway);
        admissions = new IngresoUrgenciaRepository(gateway);

        siteId = owner.queryForObject("SELECT MIN(ID) FROM SEDE", Long.class);
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
        EpisodioAtencion saved = episodes.guardar(newEpisode(null, admitted, "REGISTRADO"));

        assertThat(saved.id()).isNotNull();
        EpisodioAtencion read = episodes.buscarPorPublicId(saved.publicId()).orElseThrow();
        assertThat(read.sedeId()).isEqualTo(siteId);
        assertThat(read.pacienteId()).isNull();
        assertThat(read.ingresoAt()).isEqualTo(admitted);
        assertThat(episodes.buscarPorId(saved.id())).contains(read);
        assertThat(episodes.buscarPorPublicId("no-existe")).isEmpty();

        assertThat(episodes.actualizarEstado(saved.id(), "EN_TRIAJE")).isEqualTo(1);
        assertThat(episodes.vincularPaciente(saved.id(), patientId)).isEqualTo(1);
        assertThat(episodes.actualizarEstado(-1L, "EN_TRIAJE")).isZero();

        Instant discharged = admitted.plusSeconds(3600);
        assertThat(episodes.cerrarEpisodio(saved.id(), discharged)).isEqualTo(1);
        EpisodioAtencion closed = episodes.buscarPorId(saved.id()).orElseThrow();
        assertThat(closed.estado()).isEqualTo("EGRESADO");
        assertThat(closed.egresoAt()).isEqualTo(discharged);
        assertThat(closed.pacienteId()).isEqualTo(patientId);
    }

    @Test
    void admissionAndDetailShowTriageLevelProvisionalIdentityAndDoctor() {
        EpisodioAtencion episode = episodes.guardar(newEpisode(null, Instant.now(), "REGISTRADO"));
        IngresoUrgencia admission = admissions.guardar(new IngresoUrgencia(
                null, UUID.randomUUID().toString(), episode.id(), "AMBULANCIA", "Acompanante", "3000000000",
                "Dolor toracico", null, staffUserId, null));
        assertThat(admission.id()).isNotNull();
        assertThat(admissions.buscarPorEpisodioId(episode.id())).get()
                .extracting(IngresoUrgencia::medioLlegada).isEqualTo("AMBULANCIA");
        assertThat(admissions.buscarPorEpisodioId(-1L)).isEmpty();

        EpisodioUrgenciaResponse pending = episodes.buscarDetallePorPublicId(episode.publicId()).orElseThrow();
        assertThat(pending.nivelTriajeActual()).isEqualTo("PENDIENTE_VALORACION");
        assertThat(pending.esIdentidadProvisional()).isFalse();
        assertThat(pending.motivoLlegada()).isEqualTo("Dolor toracico");

        owner.update("INSERT INTO IDENTIDAD_PROVISIONAL (PUBLIC_ID, EPISODIO_ID, CODIGO_PROVISIONAL) VALUES (?, ?, ?)",
                UUID.randomUUID().toString(), episode.id(), "NN-0001");
        insertTriage(episode.id(), 1, "III");
        insertTriage(episode.id(), 2, "II");
        owner.update("INSERT INTO ASIGNACION_ASISTENCIAL (PUBLIC_ID, EPISODIO_ID, PROFESIONAL_ID, FUNCION, "
                        + "ASIGNADO_POR_USUARIO_ID) VALUES (?, ?, ?, 'MEDICO_TRATANTE', ?)",
                UUID.randomUUID().toString(), episode.id(), professionalId, staffUserId);

        EpisodioUrgenciaResponse detail = episodes.buscarDetallePorPublicId(episode.publicId()).orElseThrow();
        assertThat(detail.nivelTriajeActual()).isEqualTo("II");
        assertThat(detail.esIdentidadProvisional()).isTrue();
        assertThat(detail.codigoProvisional()).isEqualTo("NN-0001");
        assertThat(detail.profesionalTratanteNombre()).isEqualTo("Pedro Medico");
        assertThat(episodes.buscarDetallePorPublicId("no-existe")).isEmpty();
    }

    @Test
    void queueIsOrderedByTriageLevelThenArrivalAndHonoursFilters() {
        Instant base = Instant.now().minus(2, ChronoUnit.HOURS);
        EpisodioAtencion pending = episodes.guardar(newEpisode(null, base, "OBSERVACION"));
        EpisodioAtencion levelTwo = episodes.guardar(newEpisode(null, base.plusSeconds(60), "OBSERVACION"));
        EpisodioAtencion levelOne = episodes.guardar(newEpisode(null, base.plusSeconds(120), "OBSERVACION"));
        EpisodioAtencion levelTwoLater = episodes.guardar(newEpisode(null, base.plusSeconds(180), "OBSERVACION"));
        insertTriage(levelTwo.id(), 1, "II");
        insertTriage(levelOne.id(), 1, "I");
        insertTriage(levelTwoLater.id(), 1, "II");

        List<ItemColaUrgenciaResponse> queue = episodes.listarColaUrgenciaPorSede(null, "OBSERVACION");
        assertThat(queue).extracting(ItemColaUrgenciaResponse::episodioPublicId).containsExactly(
                levelOne.publicId(), levelTwo.publicId(), levelTwoLater.publicId(), pending.publicId());
        assertThat(queue.get(3).nivelTriaje()).isEqualTo("PENDIENTE_VALORACION");
        assertThat(queue.get(3).identificadorVisible()).isEqualTo("Paciente no identificado");
        assertThat(queue.get(0).minutosEspera()).isBetween(100L, 130L);

        assertThat(episodes.listarColaUrgenciaPorSede(siteId, " ")).extracting(ItemColaUrgenciaResponse::episodioPublicId)
                .contains(pending.publicId(), levelOne.publicId());
        assertThat(episodes.listarColaUrgenciaPorSede(-1L, null)).isEmpty();

        episodes.cerrarEpisodio(levelOne.id(), null);
        assertThat(episodes.listarColaUrgenciaPorSede(null, "OBSERVACION"))
                .extracting(ItemColaUrgenciaResponse::episodioPublicId).doesNotContain(levelOne.publicId());
    }

    private static EpisodioAtencion newEpisode(Long patient, Instant admitted, String status) {
        return new EpisodioAtencion(null, UUID.randomUUID().toString(), patient, "URGENCIA", siteId, status,
                admitted, null, "Motivo de ingreso", null, null);
    }

    private static void insertTriage(Long episodeId, int version, String level) {
        owner.update("INSERT INTO VALORACION_TRIAJE (PUBLIC_ID, EPISODIO_ID, VERSION, NIVEL, MOTIVO_CONSULTA, "
                        + "EVALUADOR_USUARIO_ID) VALUES (?, ?, ?, ?, 'Motivo', ?)",
                UUID.randomUUID().toString(), episodeId, version, level, staffUserId);
    }
}
