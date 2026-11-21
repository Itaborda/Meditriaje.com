package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.emergency.ValoracionTriajeResponse;
import com.meditriaje.dto.triage.CatalogoSintomaResponse;
import com.meditriaje.dto.triage.TriajeResponse;
import com.meditriaje.model.Triaje;
import com.meditriaje.model.TriajeSintoma;
import com.meditriaje.model.ValoracionTriaje;
import com.meditriaje.repository.plsql.OracleErrorTranslator;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.triage.NivelPrioridad;
import com.meditriaje.triage.ReglaTriaje;
import com.meditriaje.triage.SintomaTriaje;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
 * {@code PKG_TRIAGE} contra Oracle Free real, llamado como el usuario de aplicación (MEDITRIAJE_APP) a través de
 * {@link TriajeRepository}, {@link TriajeReglasRepository} y {@link ValoracionTriajeRepository}.
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class PkgTriageIT {

    private static final String OWNER = "MT_PKG_TRIAGE";

    private static OracleContainer oracle;
    private static TriajeRepository triages;
    private static TriajeReglasRepository rules;
    private static ValoracionTriajeRepository assessments;

    private static Long patientId;
    private static Long episodeId;
    private static Long episode2Id;
    private static Long staffUserId;
    private static Long profUserId;

    @BeforeAll
    static void migrate() {
        oracle = OracleTestDatabase.newContainer().withStartupTimeout(Duration.ofMinutes(10));
        oracle.start();
        DataSource ownerDs = OracleTestDatabase.provisionOwner(oracle, OWNER);
        OracleTestDatabase.flyway(ownerDs).migrate();
        OracleTestDatabase.runScript(ownerDs, "db/pkg-clinical-fixture.sql");
        JdbcTemplate owner = new JdbcTemplate(ownerDs);
        DataSource appDs =
                OracleTestDatabase.dataSource(oracle, OracleTestDatabase.APP_USER, OracleTestDatabase.APP_PASSWORD);
        PackageGateway gateway = new PackageGateway(appDs, OWNER, new OracleErrorTranslator());
        triages = new TriajeRepository(gateway);
        rules = new TriajeReglasRepository(gateway);
        assessments = new ValoracionTriajeRepository(gateway);

        patientId = owner.queryForObject("SELECT ID FROM PACIENTE WHERE PUBLIC_ID = 'fx-patient-1'", Long.class);
        episodeId = owner.queryForObject("SELECT ID FROM EPISODIO_ATENCION WHERE PUBLIC_ID = 'fx-episode-1'", Long.class);
        episode2Id = owner.queryForObject("SELECT ID FROM EPISODIO_ATENCION WHERE PUBLIC_ID = 'fx-episode-2'", Long.class);
        staffUserId = owner.queryForObject("SELECT ID FROM USUARIO WHERE PUBLIC_ID = 'fx-user-staff'", Long.class);
        profUserId = owner.queryForObject("SELECT ID FROM USUARIO WHERE PUBLIC_ID = 'fx-user-prof'", Long.class);
    }

    @AfterAll
    static void stop() {
        if (oracle != null) {
            oracle.stop();
        }
    }

    @Test
    void symptomCatalogAndRulesAreLoadedFromSeeds() {
        List<CatalogoSintomaResponse> catalog = triages.listarCatalogoSintomasActivos();
        assertThat(catalog).isNotEmpty();
        assertThat(catalog).extracting(CatalogoSintomaResponse::codigo)
                .contains("FIEBRE", "DOLOR_TORACICO_OPRESIVO");

        Map<String, Long> ids = triages.obtenerMapaCodigoAIdSintomas();
        assertThat(ids).containsKeys("FIEBRE", "TOS", "DOLOR_TORACICO_OPRESIVO");

        List<SintomaTriaje> engineSymptoms = rules.cargarSintomasActivos();
        assertThat(engineSymptoms).contains(new SintomaTriaje("DOLOR_TORACICO_OPRESIVO", true));
        assertThat(engineSymptoms).contains(new SintomaTriaje("FIEBRE", false));

        List<ReglaTriaje> v1Rules = rules.cargarReglasActivas("v1-prototipo");
        assertThat(v1Rules).isNotEmpty();
        assertThat(v1Rules).anyMatch(r -> r.sintomaCodigo().equals("FIEBRE")
                && r.nivel() == NivelPrioridad.II
                && r.intensidadMin() == 7);
        assertThat(rules.cargarReglasActivas("version-inexistente")).isEmpty();
    }

    @Test
    void selfTriagePersistsSymptomsAndConsolidatesDetail() {
        Map<String, Long> ids = triages.obtenerMapaCodigoAIdSintomas();
        String pubId = UUID.randomUUID().toString();
        Long triageId = triages.guardarTriaje(new Triaje(
                pubId, patientId, "v1-prototipo", "I", "URGENCIAS", true, "Dolor intenso en pecho"));
        assertThat(triageId).isPositive();

        triages.guardarSintomas(triageId, List.of(
                new TriajeSintoma(triageId, ids.get("FIEBRE"), new BigDecimal("12.0"), 5),
                new TriajeSintoma(triageId, ids.get("DOLOR_TORACICO_OPRESIVO"), new BigDecimal("1.5"), 9)));

        Triaje entityByPub = triages.buscarEntidadPorPublicId(pubId).orElseThrow();
        Triaje entityById = triages.buscarEntidadPorId(triageId).orElseThrow();
        assertThat(entityById).isEqualTo(entityByPub);
        assertThat(entityById.esEmergencia()).isTrue();
        assertThat(entityById.observaciones()).isEqualTo("Dolor intenso en pecho");
        assertThat(triages.buscarEntidadPorPublicId("no-existe")).isEmpty();
        assertThat(triages.buscarEntidadPorId(-1L)).isEmpty();

        TriajeResponse detail = triages.buscarPorPublicId(pubId).orElseThrow();
        assertThat(detail.pacientePublicId()).isEqualTo("fx-patient-1");
        assertThat(detail.esEmergencia()).isTrue();
        assertThat(detail.sintomasAlarma()).containsExactly("DOLOR_TORACICO_OPRESIVO");
        assertThat(detail.sintomas()).extracting(s -> s.codigo())
                .containsExactly("DOLOR_TORACICO_OPRESIVO", "FIEBRE");

        String emptyPubId = UUID.randomUUID().toString();
        triages.guardarTriaje(new Triaje(
                emptyPubId, patientId, "v1-prototipo", "IV", "CONSULTA_PROGRAMADA", false, null));
        TriajeResponse withoutSymptoms = triages.buscarPorPublicId(emptyPubId).orElseThrow();
        assertThat(withoutSymptoms.sintomas()).isEmpty();
        assertThat(withoutSymptoms.sintomasAlarma()).isEmpty();
        assertThat(withoutSymptoms.observaciones()).isNull();
        assertThat(triages.buscarPorPublicId("no-existe")).isEmpty();
    }

    @Test
    void emergencyAssessmentsAreVersionedAndPreservePartialNullVitals() {
        assertThat(assessments.obtenerSiguienteVersion(episodeId)).isEqualTo(1);
        assertThat(assessments.buscarUltimaPorEpisodioId(episode2Id)).isEmpty();

        // v1: TEMPERATURA presente, pero FRECUENCIA_CARDIACA y ESCALA_GLASGOW nulos.
        ValoracionTriaje v1 = assessments.guardar(new ValoracionTriaje(
                null, UUID.randomUUID().toString(), episodeId, 1, "III", "Fiebre persistente",
                "Paciente alerta", "120/80", null, 18, 97, new BigDecimal("38.5"), null,
                staffUserId, false, null, null));
        assertThat(v1.id()).isNotNull();
        assertThat(assessments.obtenerSiguienteVersion(episodeId)).isEqualTo(2);

        // v2: reevaluación por médico con FRECUENCIA_CARDIACA presente y TEMPERATURA nula.
        ValoracionTriaje v2 = assessments.guardar(new ValoracionTriaje(
                null, UUID.randomUUID().toString(), episodeId, 2, "II", "Taquicardia",
                null, null, 128, null, null, null, 15,
                profUserId, true, "Deterioro clinico", null));
        assertThat(v2.id()).isNotNull();

        List<ValoracionTriajeResponse> history = assessments.listarPorEpisodioId(episodeId);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).version()).isEqualTo(1);
        assertThat(history.get(0).evaluadorNombre()).isEqualTo("fx.staff@pkg.test");
        assertThat(history.get(0).frecuenciaCardiaca()).isNull();
        assertThat(history.get(0).frecuenciaRespiratoria()).isEqualTo(18);
        assertThat(history.get(0).temperatura()).isEqualByComparingTo("38.5");
        assertThat(history.get(0).escalaGlasgow()).isNull();
        assertThat(history.get(0).esReevaluacion()).isFalse();

        assertThat(history.get(1).version()).isEqualTo(2);
        assertThat(history.get(1).evaluadorNombre()).isEqualTo("Pedro Medico");
        assertThat(history.get(1).frecuenciaCardiaca()).isEqualTo(128);
        assertThat(history.get(1).temperatura()).isNull();
        assertThat(history.get(1).escalaGlasgow()).isEqualTo(15);
        assertThat(history.get(1).esReevaluacion()).isTrue();
        assertThat(history.get(1).motivoReevaluacion()).isEqualTo("Deterioro clinico");

        ValoracionTriaje latest = assessments.buscarUltimaPorEpisodioId(episodeId).orElseThrow();
        assertThat(latest.version()).isEqualTo(2);
        assertThat(latest.nivel()).isEqualTo("II");
        assertThat(latest.frecuenciaCardiaca()).isEqualTo(128);
        assertThat(latest.temperatura()).isNull();
    }
}
