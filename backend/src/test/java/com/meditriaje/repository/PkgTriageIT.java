package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.emergency.TriageAssessmentResponse;
import com.meditriaje.dto.triage.SymptomCatalogResponse;
import com.meditriaje.dto.triage.TriageResponse;
import com.meditriaje.model.Triage;
import com.meditriaje.model.TriageSymptomEntry;
import com.meditriaje.model.TriageAssessment;
import com.meditriaje.repository.plsql.OracleErrorTranslator;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.triage.PriorityLevel;
import com.meditriaje.triage.TriageRule;
import com.meditriaje.triage.TriageSymptom;
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
    private static TriageRepository triages;
    private static TriageRulesRepository rules;
    private static TriageAssessmentRepository assessments;

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
        triages = new TriageRepository(gateway);
        rules = new TriageRulesRepository(gateway);
        assessments = new TriageAssessmentRepository(gateway);

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
        List<SymptomCatalogResponse> catalog = triages.listActiveSymptomCatalog();
        assertThat(catalog).isNotEmpty();
        assertThat(catalog).extracting(SymptomCatalogResponse::code)
                .contains("FIEBRE", "DOLOR_TORACICO_OPRESIVO");

        Map<String, Long> ids = triages.getSymptomCodeToIdMap();
        assertThat(ids).containsKeys("FIEBRE", "TOS", "DOLOR_TORACICO_OPRESIVO");

        List<TriageSymptom> engineSymptoms = rules.loadActiveSymptoms();
        assertThat(engineSymptoms).contains(new TriageSymptom("DOLOR_TORACICO_OPRESIVO", true));
        assertThat(engineSymptoms).contains(new TriageSymptom("FIEBRE", false));

        List<TriageRule> v1Rules = rules.loadActiveRules("v1-prototipo");
        assertThat(v1Rules).isNotEmpty();
        assertThat(v1Rules).anyMatch(r -> r.symptomCode().equals("FIEBRE")
                && r.level() == PriorityLevel.II
                && r.minIntensity() == 7);
        assertThat(rules.loadActiveRules("version-inexistente")).isEmpty();
    }

    @Test
    void selfTriagePersistsSymptomsAndConsolidatesDetail() {
        Map<String, Long> ids = triages.getSymptomCodeToIdMap();
        String pubId = UUID.randomUUID().toString();
        Long triageId = triages.saveTriage(new Triage(
                pubId, patientId, "v1-prototipo", "I", "URGENCIAS", true, "Dolor intenso en pecho"));
        assertThat(triageId).isPositive();

        triages.saveSymptoms(triageId, List.of(
                new TriageSymptomEntry(triageId, ids.get("FIEBRE"), new BigDecimal("12.0"), 5),
                new TriageSymptomEntry(triageId, ids.get("DOLOR_TORACICO_OPRESIVO"), new BigDecimal("1.5"), 9)));

        Triage entityByPub = triages.findEntityByPublicId(pubId).orElseThrow();
        Triage entityById = triages.findEntityById(triageId).orElseThrow();
        assertThat(entityById).isEqualTo(entityByPub);
        assertThat(entityById.isEmergency()).isTrue();
        assertThat(entityById.notes()).isEqualTo("Dolor intenso en pecho");
        assertThat(triages.findEntityByPublicId("no-existe")).isEmpty();
        assertThat(triages.findEntityById(-1L)).isEmpty();

        TriageResponse detail = triages.findByPublicId(pubId).orElseThrow();
        assertThat(detail.patientPublicId()).isEqualTo("fx-patient-1");
        assertThat(detail.isEmergency()).isTrue();
        assertThat(detail.alarmSymptoms()).containsExactly("DOLOR_TORACICO_OPRESIVO");
        assertThat(detail.symptoms()).extracting(s -> s.code())
                .containsExactly("DOLOR_TORACICO_OPRESIVO", "FIEBRE");

        String emptyPubId = UUID.randomUUID().toString();
        triages.saveTriage(new Triage(
                emptyPubId, patientId, "v1-prototipo", "IV", "CONSULTA_PROGRAMADA", false, null));
        TriageResponse withoutSymptoms = triages.findByPublicId(emptyPubId).orElseThrow();
        assertThat(withoutSymptoms.symptoms()).isEmpty();
        assertThat(withoutSymptoms.alarmSymptoms()).isEmpty();
        assertThat(withoutSymptoms.notes()).isNull();
        assertThat(triages.findByPublicId("no-existe")).isEmpty();
    }

    @Test
    void emergencyAssessmentsAreVersionedAndPreservePartialNullVitals() {
        assertThat(assessments.getNextVersion(episodeId)).isEqualTo(1);
        assertThat(assessments.findLatestByEpisodeId(episode2Id)).isEmpty();

        // v1: TEMPERATURA presente, pero FRECUENCIA_CARDIACA y ESCALA_GLASGOW nulos.
        TriageAssessment v1 = assessments.save(new TriageAssessment(
                null, UUID.randomUUID().toString(), episodeId, 1, "III", "Fiebre persistente",
                "Paciente alerta", "120/80", null, 18, 97, new BigDecimal("38.5"), null,
                staffUserId, false, null, null));
        assertThat(v1.id()).isNotNull();
        assertThat(assessments.getNextVersion(episodeId)).isEqualTo(2);

        // v2: reevaluación por médico con FRECUENCIA_CARDIACA presente y TEMPERATURA nula.
        TriageAssessment v2 = assessments.save(new TriageAssessment(
                null, UUID.randomUUID().toString(), episodeId, 2, "II", "Taquicardia",
                null, null, 128, null, null, null, 15,
                profUserId, true, "Deterioro clinico", null));
        assertThat(v2.id()).isNotNull();

        List<TriageAssessmentResponse> history = assessments.listByEpisodeId(episodeId);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).version()).isEqualTo(1);
        assertThat(history.get(0).evaluatorName()).isEqualTo("fx.staff@pkg.test");
        assertThat(history.get(0).heartRate()).isNull();
        assertThat(history.get(0).respiratoryRate()).isEqualTo(18);
        assertThat(history.get(0).temperature()).isEqualByComparingTo("38.5");
        assertThat(history.get(0).glasgowScale()).isNull();
        assertThat(history.get(0).isReevaluation()).isFalse();

        assertThat(history.get(1).version()).isEqualTo(2);
        assertThat(history.get(1).evaluatorName()).isEqualTo("Pedro Medico");
        assertThat(history.get(1).heartRate()).isEqualTo(128);
        assertThat(history.get(1).temperature()).isNull();
        assertThat(history.get(1).glasgowScale()).isEqualTo(15);
        assertThat(history.get(1).isReevaluation()).isTrue();
        assertThat(history.get(1).reevaluationReason()).isEqualTo("Deterioro clinico");

        TriageAssessment latest = assessments.findLatestByEpisodeId(episodeId).orElseThrow();
        assertThat(latest.version()).isEqualTo(2);
        assertThat(latest.level()).isEqualTo("II");
        assertThat(latest.heartRate()).isEqualTo(128);
        assertThat(latest.temperature()).isNull();
    }
}
