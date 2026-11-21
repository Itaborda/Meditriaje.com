package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.affiliation.AfiliacionResponse;
import com.meditriaje.exception.ConflictoOperacionException;
import com.meditriaje.model.AfiliacionPaciente;
import com.meditriaje.model.DetalleImportacionEps;
import com.meditriaje.model.EntidadEps;
import com.meditriaje.model.LoteImportacionEps;
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
    private static EntidadEpsRepository epsRepo;
    private static AfiliacionPacienteRepository afiliaciones;
    private static LoteImportacionEpsRepository lotes;

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
        epsRepo = new EntidadEpsRepository(gateway);
        afiliaciones = new AfiliacionPacienteRepository(gateway);
        lotes = new LoteImportacionEpsRepository(gateway);
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
        List<EntidadEps> active = epsRepo.listarActivas();
        assertThat(active).isNotEmpty();
        assertThat(active).extracting(EntidadEps::nombre).isSorted();
        assertThat(active).allMatch(e -> "ACTIVA".equals(e.estado()));

        EntidadEps nuevaEps = epsRepo.buscarPorCodigo("EPS037").orElseThrow();
        assertThat(epsRepo.buscarPorId(nuevaEps.id())).contains(nuevaEps);
        assertThat(epsRepo.buscarPorPublicId(nuevaEps.publicId())).contains(nuevaEps);
        assertThat(epsRepo.buscarPorCodigo("NO-EXISTE")).isEmpty();
        assertThat(epsRepo.buscarPorPublicId(" ")).isEmpty();
        assertThat(epsRepo.buscarPorId(null)).isEmpty();
    }

    @Test
    void affiliationRoutinesWithDefaultsLinkingAndUpdate() {
        EntidadEps eps1 = epsRepo.buscarPorCodigo("EPS037").orElseThrow();
        EntidadEps eps2 = epsRepo.buscarPorCodigo("EPS005").orElseThrow();
        String doc = "910001";

        // Creación con defaults en PL/SQL (tipoAfiliado, estado, fuenteVerificacion, ultimaVerificacionAt)
        AfiliacionPaciente saved = afiliaciones.guardar(new AfiliacionPaciente(
                null, UUID.randomUUID().toString(), null, "CC", doc, eps1.id(),
                "CONTRIBUTIVO", null, null, null, null, null, null, null));

        assertThat(saved.id()).isPositive();
        assertThat(saved.pacienteId()).isNull();
        assertThat(saved.tipoAfiliado()).isEqualTo("COTIZANTE");
        assertThat(saved.estado()).isEqualTo("ACTIVO");
        assertThat(saved.fuenteVerificacion()).isEqualTo("CARGA_MASIVA");
        assertThat(saved.ultimaVerificacionAt()).isNotNull();
        assertThat(afiliaciones.buscarPorDocumento("CC", doc)).contains(saved);

        // Documento duplicado -> ConflictoOperacionException
        assertThatThrownBy(() -> afiliaciones.guardar(new AfiliacionPaciente(
                        null, UUID.randomUUID().toString(), null, "CC", doc, eps1.id(),
                        "CONTRIBUTIVO", "COTIZANTE", "ACTIVO", LocalDate.now(), "MANUAL", Instant.now(), null, null)))
                .isInstanceOf(ConflictoOperacionException.class);

        // Vista antes de vincular paciente
        AfiliacionResponse unlinkedDetail = afiliaciones.buscarDetallePorDocumento("CC", doc).orElseThrow();
        assertThat(unlinkedDetail.pacientePublicId()).isNull();
        assertThat(unlinkedDetail.pacienteNombre()).isNull();
        assertThat(unlinkedDetail.epsCodigo()).isEqualTo("EPS037");

        // Vincular paciente (segunda vinculación no sobrescribe porque PACIENTE_ID ya no es NULL)
        Object[] pat1 = newPatient(doc);
        Object[] pat2 = newPatient("910002");
        Long patientId1 = (Long) pat1[0];
        String patientPub1 = (String) pat1[1];
        Long patientId2 = (Long) pat2[0];

        afiliaciones.vincularPaciente("CC", doc, patientId1);
        afiliaciones.vincularPaciente("CC", doc, patientId2);

        AfiliacionResponse linkedByDoc = afiliaciones.buscarDetallePorDocumento("CC", doc).orElseThrow();
        assertThat(linkedByDoc.pacientePublicId()).isEqualTo(patientPub1);
        assertThat(linkedByDoc.pacienteNombre()).isEqualTo("Maria Lopez");
        assertThat(afiliaciones.buscarDetallePorPacienteId(patientId1)).contains(linkedByDoc);
        assertThat(afiliaciones.buscarDetallePorPacienteId(patientId2)).isEmpty();

        // Actualizar afiliación
        Instant verifAt = Instant.now();
        afiliaciones.actualizarAfiliacion(saved.id(), eps2.id(), "SUBSIDIADO", "BENEFICIARIO", "SUSPENDIDO", verifAt);
        AfiliacionPaciente updated = afiliaciones.buscarPorId(saved.id()).orElseThrow();
        assertThat(updated.epsId()).isEqualTo(eps2.id());
        assertThat(updated.regimen()).isEqualTo("SUBSIDIADO");
        assertThat(updated.tipoAfiliado()).isEqualTo("BENEFICIARIO");
        assertThat(updated.estado()).isEqualTo("SUSPENDIDO");
    }

    @Test
    void importBatchAndItemsRoutinesRoundTrip() {
        EntidadEps eps = epsRepo.buscarPorCodigo("CCFC55").orElseThrow();
        Long uploaderId = newUser("admin-eps");
        String batchPub = UUID.randomUUID().toString();

        LoteImportacionEps batch = lotes.guardarLote(new LoteImportacionEps(
                null, batchPub, "afiliados.xlsx", "abc123sha256", eps.id(), 3, 2, 1, null, uploaderId, null, null));

        assertThat(batch.id()).isPositive();
        assertThat(batch.estado()).isEqualTo("PREVIEW");
        assertThat(lotes.buscarPorPublicId(batchPub)).contains(batch);
        assertThat(lotes.buscarPorPublicId("no-existe")).isEmpty();

        lotes.guardarDetallesBatch(batch.id(), List.of(
                new DetalleImportacionEps(null, null, 2, "CC", "1001", "Ana", "Perez",
                        "SUBSIDIADO", "COTIZANTE", "VALIDO", null),
                new DetalleImportacionEps(null, null, 4, "CC", "1002", "Luis", "Diaz",
                        "SUBSIDIADO", "BENEFICIARIO", "VALIDO", null),
                new DetalleImportacionEps(null, null, 3, "XX", "1", null, null,
                        null, null, "ERROR", "Documento invalido")));

        // Límite 2 ordenado por NUMERO_FILA ASC
        List<DetalleImportacionEps> sample = lotes.listarDetalles(batch.id(), 2);
        assertThat(sample).extracting(DetalleImportacionEps::numeroFila).containsExactly(2, 3);
        assertThat(sample.get(1).nombres()).isNull();
        assertThat(sample.get(1).errorMotivo()).isEqualTo("Documento invalido");

        // Filas válidas ordenadas por NUMERO_FILA ASC
        List<DetalleImportacionEps> validOnly = lotes.listarFilasValidas(batch.id());
        assertThat(validOnly).extracting(DetalleImportacionEps::numeroFila).containsExactly(2, 4);

        // Actualizar totales y estado
        lotes.actualizarTotales(batch.id(), 10, 8, 2);
        Instant processedAt = Instant.now();
        lotes.cambiarEstadoLote(batch.id(), "PROCESADO", processedAt);

        LoteImportacionEps updated = lotes.buscarPorId(batch.id()).orElseThrow();
        assertThat(updated.totalFilas()).isEqualTo(10);
        assertThat(updated.filasValidas()).isEqualTo(8);
        assertThat(updated.filasFallidas()).isEqualTo(2);
        assertThat(updated.estado()).isEqualTo("PROCESADO");
        assertThat(updated.procesadoAt()).isNotNull();
    }
}
