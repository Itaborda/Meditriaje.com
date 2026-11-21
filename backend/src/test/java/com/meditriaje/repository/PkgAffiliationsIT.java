package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.affiliation.AffiliationResponse;
import com.meditriaje.exception.OperationConflictException;
import com.meditriaje.model.PatientAffiliation;
import com.meditriaje.model.EpsImportDetail;
import com.meditriaje.model.EpsEntity;
import com.meditriaje.model.EpsImportBatch;
import com.meditriaje.repository.plsql.OracleErrorTranslator;
import com.meditriaje.repository.plsql.PackageGateway;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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

/** PKG_AFFILIATIONS contra Oracle Free real, llamado como el usuario de aplicación (MEDITRIAJE_APP). */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class PkgAffiliationsIT {

    private static final String OWNER = "MT_PKG_AFFILIATIONS";

    private static OracleContainer oracle;
    private static JdbcTemplate owner;
    private static EpsEntityRepository epsRepo;
    private static PatientAffiliationRepository afiliaciones;
    private static EpsImportBatchRepository lotes;

    @BeforeAll
    static void migrate() {
        oracle = OracleTestDatabase.newContainer().withStartupTimeout(Duration.ofMinutes(3));
        oracle.start();
        DataSource ownerDs = OracleTestDatabase.provisionOwner(oracle, OWNER);
        OracleTestDatabase.flyway(ownerDs).migrate();
        owner = new JdbcTemplate(ownerDs);
        DataSource appDs = OracleTestDatabase.dataSource(
                oracle, OracleTestDatabase.APP_USER, OracleTestDatabase.APP_PASSWORD);
        PackageGateway gateway = new PackageGateway(appDs, OWNER, new OracleErrorTranslator());
        epsRepo = new EpsEntityRepository(gateway);
        afiliaciones = new PatientAffiliationRepository(gateway);
        lotes = new EpsImportBatchRepository(gateway);
    }

    @AfterAll
    static void stop() {
        if (oracle != null) {
            oracle.stop();
        }
    }

    private static Long newUser(String prefix) {
        String publicId = UUID.randomUUID().toString();
        String email = prefix + "-" + publicId.substring(0, 8) + "@test.co";
        owner.update("INSERT INTO USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH) VALUES (?, ?, 'x')", publicId, email);
        return owner.queryForObject("SELECT ID FROM USUARIO WHERE PUBLIC_ID = ?", Long.class, publicId);
    }

    private static Object[] newPatient(String document) {
        Long userId = newUser("pac");
        String publicId = UUID.randomUUID().toString();
        owner.update(
                "INSERT INTO PACIENTE (USUARIO_ID, PUBLIC_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO, NOMBRES, APELLIDOS, FECHA_NACIMIENTO) "
                        + "VALUES (?, ?, 'CC', ?, 'Maria', 'Lopez', DATE '1993-08-22')",
                userId, publicId, document);
        Long patientId = owner.queryForObject("SELECT ID FROM PACIENTE WHERE PUBLIC_ID = ?", Long.class, publicId);
        return new Object[] {patientId, publicId};
    }

    @Test
    void epsCatalogRoutinesRoundTrip() {
        List<EpsEntity> active = epsRepo.listActive();
        assertThat(active).isNotEmpty();
        assertThat(active).extracting(EpsEntity::name).isSorted();
        assertThat(active).allMatch(e -> "ACTIVA".equals(e.status()));

        EpsEntity nuevaEps = epsRepo.findByCode("EPS037").orElseThrow();
        assertThat(epsRepo.findById(nuevaEps.id())).contains(nuevaEps);
        assertThat(epsRepo.findByPublicId(nuevaEps.publicId())).contains(nuevaEps);
        assertThat(epsRepo.findByCode("NO-EXISTE")).isEmpty();
        assertThat(epsRepo.findByPublicId(" ")).isEmpty();
        assertThat(epsRepo.findById(null)).isEmpty();
    }

    @Test
    void affiliationRoutinesWithDefaultsLinkingAndUpdate() {
        EpsEntity eps1 = epsRepo.findByCode("EPS037").orElseThrow();
        EpsEntity eps2 = epsRepo.findByCode("EPS005").orElseThrow();
        String doc = "910001";

        // Creación con defaults en PL/SQL (tipoAfiliado, estado, fuenteVerificacion, ultimaVerificacionAt)
        PatientAffiliation saved = afiliaciones.save(new PatientAffiliation(
                null, UUID.randomUUID().toString(), null, "CC", doc, eps1.id(),
                "CONTRIBUTIVO", null, null, null, null, null, null, null));

        assertThat(saved.id()).isPositive();
        assertThat(saved.patientId()).isNull();
        assertThat(saved.affiliateType()).isEqualTo("COTIZANTE");
        assertThat(saved.status()).isEqualTo("ACTIVO");
        assertThat(saved.verificationSource()).isEqualTo("CARGA_MASIVA");
        assertThat(saved.lastVerifiedAt()).isNotNull();
        assertThat(afiliaciones.findByDocument("CC", doc)).contains(saved);

        // Documento duplicado -> ConflictoOperacionException
        assertThatThrownBy(() -> afiliaciones.save(new PatientAffiliation(
                        null, UUID.randomUUID().toString(), null, "CC", doc, eps1.id(),
                        "CONTRIBUTIVO", "COTIZANTE", "ACTIVO", LocalDate.now(), "MANUAL", Instant.now(), null, null)))
                .isInstanceOf(OperationConflictException.class);

        // Vista antes de vincular paciente
        AffiliationResponse unlinkedDetail = afiliaciones.findDetailByDocument("CC", doc).orElseThrow();
        assertThat(unlinkedDetail.patientPublicId()).isNull();
        assertThat(unlinkedDetail.patientName()).isNull();
        assertThat(unlinkedDetail.epsCode()).isEqualTo("EPS037");

        // Vincular paciente (segunda vinculación no sobrescribe porque PACIENTE_ID ya no es NULL)
        Object[] pat1 = newPatient(doc);
        Object[] pat2 = newPatient("910002");
        Long patientId1 = (Long) pat1[0];
        String patientPub1 = (String) pat1[1];
        Long patientId2 = (Long) pat2[0];

        afiliaciones.linkPatient("CC", doc, patientId1);
        afiliaciones.linkPatient("CC", doc, patientId2);

        AffiliationResponse linkedByDoc = afiliaciones.findDetailByDocument("CC", doc).orElseThrow();
        assertThat(linkedByDoc.patientPublicId()).isEqualTo(patientPub1);
        assertThat(linkedByDoc.patientName()).isEqualTo("Maria Lopez");
        assertThat(afiliaciones.findDetailByPatientId(patientId1)).contains(linkedByDoc);
        assertThat(afiliaciones.findDetailByPatientId(patientId2)).isEmpty();

        // Actualizar afiliación
        Instant verifAt = Instant.now();
        afiliaciones.updateAffiliation(saved.id(), eps2.id(), "SUBSIDIADO", "BENEFICIARIO", "SUSPENDIDO", verifAt);
        PatientAffiliation updated = afiliaciones.findById(saved.id()).orElseThrow();
        assertThat(updated.epsId()).isEqualTo(eps2.id());
        assertThat(updated.regime()).isEqualTo("SUBSIDIADO");
        assertThat(updated.affiliateType()).isEqualTo("BENEFICIARIO");
        assertThat(updated.status()).isEqualTo("SUSPENDIDO");
    }

    @Test
    void importBatchAndItemsRoutinesRoundTrip() {
        EpsEntity eps = epsRepo.findByCode("CCFC55").orElseThrow();
        Long uploaderId = newUser("admin-eps");
        String batchPub = UUID.randomUUID().toString();

        EpsImportBatch batch = lotes.saveBatch(new EpsImportBatch(
                null, batchPub, "afiliados.xlsx", "abc123sha256", eps.id(), 3, 2, 1, null, uploaderId, null, null));

        assertThat(batch.id()).isPositive();
        assertThat(batch.status()).isEqualTo("PREVIEW");
        assertThat(lotes.findByPublicId(batchPub)).contains(batch);
        assertThat(lotes.findByPublicId("no-existe")).isEmpty();

        lotes.saveBatchDetails(batch.id(), List.of(
                new EpsImportDetail(null, null, 2, "CC", "1001", "Ana", "Perez",
                        "SUBSIDIADO", "COTIZANTE", "VALIDO", null),
                new EpsImportDetail(null, null, 4, "CC", "1002", "Luis", "Diaz",
                        "SUBSIDIADO", "BENEFICIARIO", "VALIDO", null),
                new EpsImportDetail(null, null, 3, "XX", "1", null, null,
                        null, null, "ERROR", "Documento invalido")));

        // Límite 2 ordenado por NUMERO_FILA ASC
        List<EpsImportDetail> sample = lotes.listDetails(batch.id(), 2);
        assertThat(sample).extracting(EpsImportDetail::rowNumber).containsExactly(2, 3);
        assertThat(sample.get(1).firstNames()).isNull();
        assertThat(sample.get(1).errorReason()).isEqualTo("Documento invalido");

        // Filas válidas ordenadas por NUMERO_FILA ASC
        List<EpsImportDetail> validOnly = lotes.listValidRows(batch.id());
        assertThat(validOnly).extracting(EpsImportDetail::rowNumber).containsExactly(2, 4);

        // Actualizar totales y estado
        lotes.updateTotals(batch.id(), 10, 8, 2);
        Instant processedAt = Instant.now();
        lotes.changeBatchStatus(batch.id(), "PROCESADO", processedAt);

        EpsImportBatch updated = lotes.findById(batch.id()).orElseThrow();
        assertThat(updated.totalRows()).isEqualTo(10);
        assertThat(updated.validRows()).isEqualTo(8);
        assertThat(updated.failedRows()).isEqualTo(2);
        assertThat(updated.status()).isEqualTo("PROCESADO");
        assertThat(updated.processedAt()).isNotNull();
    }
}
