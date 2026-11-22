package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.clinical.Cie10DiagnosisResponse;
import com.meditriaje.model.Cie10Diagnosis;
import com.meditriaje.repository.plsql.OracleErrorTranslator;
import com.meditriaje.repository.plsql.PackageGateway;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/** PKG_CATALOGS contra Oracle Free real, llamado como el usuario de aplicación (MEDITRIAJE_APP). */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class PkgCatalogsIT {

    private static final String OWNER = "MT_PKG_CATALOGS";

    private static OracleContainer oracle;
    private static Cie10DiagnosisRepository repository;

    @BeforeAll
    static void migrate() {
        oracle = OracleTestDatabase.newContainer();
        oracle.start();
        DataSource ownerDs = OracleTestDatabase.provisionOwner(oracle, OWNER);
        OracleTestDatabase.flyway(ownerDs).migrate();
        DataSource appDs = OracleTestDatabase.dataSource(
                oracle, OracleTestDatabase.APP_USER, OracleTestDatabase.APP_PASSWORD);
        repository = new Cie10DiagnosisRepository(
                new PackageGateway(appDs, OWNER, new OracleErrorTranslator()));
    }

    @AfterAll
    static void stop() {
        if (oracle != null) {
            oracle.stop();
        }
    }

    @Test
    void getDiagnosisByCodeReturnsTheSeededRow() {
        Optional<Cie10Diagnosis> diag = repository.findByCode("j00");

        assertThat(diag).isPresent();
        assertThat(diag.get().code()).isEqualTo("J00");
        assertThat(diag.get().status()).isEqualTo("ACTIVO");
    }

    @Test
    void getDiagnosisByIdRoundTripsTheCode() {
        Cie10Diagnosis byCode = repository.findByCode("J02.9").orElseThrow();

        assertThat(repository.findById(byCode.id())).contains(byCode);
    }

    @Test
    void unknownCodeAndIdReturnEmpty() {
        assertThat(repository.findByCode("Z99.99")).isEmpty();
        assertThat(repository.findById(-1L)).isEmpty();
    }

    @Test
    void listActiveDiagnosesFiltersAndOrdersByCode() {
        List<Cie10DiagnosisResponse> all = repository.listActive(null);
        List<Cie10DiagnosisResponse> filtered = repository.listActive("faringitis");

        assertThat(all).isNotEmpty();
        assertThat(all).extracting(Cie10DiagnosisResponse::code).isSorted();
        assertThat(filtered).extracting(Cie10DiagnosisResponse::code).contains("J02.9");
        assertThat(filtered.size()).isLessThan(all.size());
    }
}
