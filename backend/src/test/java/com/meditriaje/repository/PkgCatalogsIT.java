package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.clinical.DiagnosticoCie10Response;
import com.meditriaje.model.DiagnosticoCie10;
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
    private static DiagnosticoCie10Repository repository;

    @BeforeAll
    static void migrate() {
        oracle = OracleTestDatabase.newContainer();
        oracle.start();
        DataSource ownerDs = OracleTestDatabase.provisionOwner(oracle, OWNER);
        OracleTestDatabase.flyway(ownerDs).migrate();
        DataSource appDs = OracleTestDatabase.dataSource(
                oracle, OracleTestDatabase.APP_USER, OracleTestDatabase.APP_PASSWORD);
        repository = new DiagnosticoCie10Repository(
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
        Optional<DiagnosticoCie10> diag = repository.buscarPorCodigo("j00");

        assertThat(diag).isPresent();
        assertThat(diag.get().codigo()).isEqualTo("J00");
        assertThat(diag.get().estado()).isEqualTo("ACTIVO");
    }

    @Test
    void getDiagnosisByIdRoundTripsTheCode() {
        DiagnosticoCie10 byCode = repository.buscarPorCodigo("J02.9").orElseThrow();

        assertThat(repository.buscarPorId(byCode.id())).contains(byCode);
    }

    @Test
    void unknownCodeAndIdReturnEmpty() {
        assertThat(repository.buscarPorCodigo("Z99.99")).isEmpty();
        assertThat(repository.buscarPorId(-1L)).isEmpty();
    }

    @Test
    void listActiveDiagnosesFiltersAndOrdersByCode() {
        List<DiagnosticoCie10Response> all = repository.listarActivos(null);
        List<DiagnosticoCie10Response> filtered = repository.listarActivos("faringitis");

        assertThat(all).isNotEmpty();
        assertThat(all).extracting(DiagnosticoCie10Response::codigo).isSorted();
        assertThat(filtered).extracting(DiagnosticoCie10Response::codigo).contains("J02.9");
        assertThat(filtered.size()).isLessThan(all.size());
    }
}
