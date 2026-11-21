package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.pharmacy.DispensacionResponse;
import com.meditriaje.dto.pharmacy.RecetaDispensacionResponse;
import com.meditriaje.dto.pharmacy.SaldoMedicamentoDto;
import com.meditriaje.dto.prescription.MedicamentoResponse;
import com.meditriaje.dto.prescription.RecetaResponse;
import com.meditriaje.exception.ConflictoOperacionException;
import com.meditriaje.model.Dispensacion;
import com.meditriaje.model.DispensacionDetalle;
import com.meditriaje.model.EstadoRecetaDispensacion;
import com.meditriaje.model.Medicamento;
import com.meditriaje.model.Receta;
import com.meditriaje.model.RecetaDetalle;
import com.meditriaje.repository.plsql.OracleErrorTranslator;
import com.meditriaje.repository.plsql.PackageGateway;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/** PKG_PHARMACY contra Oracle Free real, llamado como el usuario de aplicación (MEDITRIAJE_APP). */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class PkgPharmacyIT {

    private static final String OWNER = "MT_PKG_PHARMACY";

    private static OracleContainer oracle;
    private static JdbcTemplate owner;
    private static OracleErrorTranslator translator;
    private static MedicamentoRepository medicamentos;
    private static RecetaRepository recetas;
    private static DispensacionRepository dispensaciones;

    @BeforeAll
    static void migrate() {
        oracle = OracleTestDatabase.newContainer().withStartupTimeout(Duration.ofMinutes(3));
        oracle.start();
        DataSource ownerDs = OracleTestDatabase.provisionOwner(oracle, OWNER);
        OracleTestDatabase.flyway(ownerDs).migrate();
        owner = new JdbcTemplate(ownerDs);
        translator = new OracleErrorTranslator();
        DataSource appDs = OracleTestDatabase.dataSource(
                oracle, OracleTestDatabase.APP_USER, OracleTestDatabase.APP_PASSWORD);
        PackageGateway gateway = new PackageGateway(appDs, OWNER, translator);
        medicamentos = new MedicamentoRepository(gateway);
        recetas = new RecetaRepository(gateway);
        dispensaciones = new DispensacionRepository(gateway);
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

    private static Long newPatient(String docSuffix, String firstName, String lastName) {
        Long userId = newUser("pac");
        String publicId = UUID.randomUUID().toString();
        owner.update(
                "INSERT INTO PACIENTE (USUARIO_ID, PUBLIC_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO, NOMBRES, APELLIDOS, FECHA_NACIMIENTO) "
                        + "VALUES (?, ?, 'CC', ?, ?, ?, DATE '1992-07-10')",
                userId, publicId, "600" + docSuffix, firstName, lastName);
        return owner.queryForObject("SELECT ID FROM PACIENTE WHERE PUBLIC_ID = ?", Long.class, publicId);
    }

    private static Long newProfessional(String docSuffix) {
        Long userId = newUser("prof");
        String publicId = UUID.randomUUID().toString();
        Long espId = owner.queryForObject("SELECT MIN(ID) FROM ESPECIALIDAD", Long.class);
        owner.update(
                "INSERT INTO PROFESIONAL (USUARIO_ID, PUBLIC_ID, ESPECIALIDAD_ID, REGISTRO_MEDICO, NOMBRES, APELLIDOS, TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TELEFONO) "
                        + "VALUES (?, ?, ?, ?, 'Sofia', 'Mendez', 'CC', ?, '3004445566')",
                userId, publicId, espId, "RM-PH-" + docSuffix, "500" + docSuffix);
        return owner.queryForObject("SELECT ID FROM PROFESIONAL WHERE PUBLIC_ID = ?", Long.class, publicId);
    }

    private static Long newEncounter(Long patientId, Long professionalId, int hourOffset) {
        Long siteId = owner.queryForObject("SELECT MIN(ID) FROM SEDE", Long.class);
        Long espId = owner.queryForObject("SELECT ESPECIALIDAD_ID FROM PROFESIONAL WHERE ID = ?", Long.class, professionalId);
        String slotPublicId = UUID.randomUUID().toString();
        Instant start = Instant.now().plus(hourOffset, ChronoUnit.HOURS);
        owner.update(
                "INSERT INTO DISPONIBILIDAD_SLOT (PUBLIC_ID, PROFESIONAL_ID, SEDE_ID, ESPECIALIDAD_ID, FECHA_HORA_INICIO, FECHA_HORA_FIN) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                slotPublicId, professionalId, siteId, espId, Timestamp.from(start),
                Timestamp.from(start.plus(20, ChronoUnit.MINUTES)));
        Long slotId = owner.queryForObject("SELECT ID FROM DISPONIBILIDAD_SLOT WHERE PUBLIC_ID = ?", Long.class, slotPublicId);

        String citaPublicId = UUID.randomUUID().toString();
        owner.update("INSERT INTO CITA (PUBLIC_ID, SLOT_ID, PACIENTE_ID, ESTADO) VALUES (?, ?, ?, 'ATENDIDA')",
                citaPublicId, slotId, patientId);
        Long citaId = owner.queryForObject("SELECT ID FROM CITA WHERE PUBLIC_ID = ?", Long.class, citaPublicId);

        String atnPublicId = UUID.randomUUID().toString();
        owner.update("INSERT INTO ATENCION (PUBLIC_ID, CITA_ID, PACIENTE_ID, PROFESIONAL_ID, ESTADO) VALUES (?, ?, ?, ?, 'ABIERTA')",
                atnPublicId, citaId, patientId, professionalId);
        return owner.queryForObject("SELECT ID FROM ATENCION WHERE PUBLIC_ID = ?", Long.class, atnPublicId);
    }

    @Test
    void medicationCatalogRoutinesRoundTrip() {
        List<MedicamentoResponse> all = medicamentos.listarActivos(null, 0, 50);
        assertThat(all).isNotEmpty();
        assertThat(medicamentos.contarActivos(null)).isEqualTo(all.size());

        List<MedicamentoResponse> filtered = medicamentos.listarActivos("acetaminofen", 0, 10);
        assertThat(filtered).isNotEmpty();
        assertThat(medicamentos.contarActivos("acetaminofen")).isEqualTo(filtered.size());

        String pubId = filtered.get(0).publicId();
        Medicamento byPub = medicamentos.buscarPorPublicId(pubId).orElseThrow();
        assertThat(byPub.codigo()).isEqualTo(filtered.get(0).codigo());
        assertThat(medicamentos.buscarPorId(byPub.id())).contains(byPub);
        assertThat(medicamentos.buscarPorPublicId("no-existe")).isEmpty();
        assertThat(medicamentos.buscarPorId(-1L)).isEmpty();
    }

    @Test
    void prescriptionAndDispensationFlowWithBalancesSearchAndTriggers() {
        Long patientId = newPatient("111", "Lucia", "Vega");
        Long professionalId = newProfessional("111");
        Long dispenserUserId = newUser("farm");
        Long encounter1 = newEncounter(patientId, professionalId, 1);
        Long encounter2 = newEncounter(patientId, professionalId, 2);

        Medicamento med1 = medicamentos.listarActivos("acetaminofen", 0, 5).stream()
                .findFirst().flatMap(m -> medicamentos.buscarPorPublicId(m.publicId())).orElseThrow();
        Medicamento med2 = medicamentos.listarActivos("ibuprofeno", 0, 5).stream()
                .findFirst().flatMap(m -> medicamentos.buscarPorPublicId(m.publicId())).orElseThrow();

        String recPub1 = "a1b2c3d4-" + UUID.randomUUID().toString().substring(9);
        Long recId1 = recetas.crearReceta(new Receta(null, recPub1, encounter1, patientId, professionalId, 30, null));
        assertThat(recId1).isPositive();
        assertThat(recetas.existePorAtencionId(encounter1)).isTrue();
        assertThat(recetas.existePorAtencionId(-1L)).isFalse();

        recetas.guardarDetalles(recId1, List.of(
                new RecetaDetalle(null, recId1, med1.id(), med1.nombreComercial(), med1.principioActivo(),
                        med1.presentacion(), med1.concentracion(), "500 mg", "Cada 8 horas", 5, 10, "Con agua"),
                new RecetaDetalle(null, recId1, med2.id(), med2.nombreComercial(), med2.principioActivo(),
                        med2.presentacion(), med2.concentracion(), "400 mg", "Cada 12 horas", 3, 6, null)));

        // Segunda receta para probar paginación y orden
        String recPub2 = UUID.randomUUID().toString();
        Long recId2 = recetas.crearReceta(new Receta(null, recPub2, encounter2, patientId, professionalId, 15, null));
        recetas.guardarDetalles(recId2, List.of(
                new RecetaDetalle(null, recId2, med1.id(), med1.nombreComercial(), med1.principioActivo(),
                        med1.presentacion(), med1.concentracion(), "500 mg", "Cada 12 horas", 2, 4, null)));

        Receta entity1 = recetas.buscarEntidadPorPublicId(recPub1).orElseThrow();
        assertThat(entity1.id()).isEqualTo(recId1);

        RecetaResponse view1 = recetas.buscarPorPublicId(recPub1).orElseThrow();
        assertThat(view1.detalles()).hasSize(2);
        assertThat(view1.detalles().get(1).indicaciones()).isNull();
        assertThat(recetas.contarPorPacienteId(patientId)).isEqualTo(2);

        List<RecetaResponse> page0 = recetas.listarPorPacienteId(patientId, 0, 1);
        List<RecetaResponse> page1 = recetas.listarPorPacienteId(patientId, 1, 1);
        assertThat(page0).extracting(RecetaResponse::publicId).containsExactly(recPub2);
        assertThat(page1).extracting(RecetaResponse::publicId).containsExactly(recPub1);

        // Ítems prescritos y saldos iniciales
        List<DispensacionRepository.ItemPrescritoInfo> prescribed = dispensaciones.buscarItemsPrescripcion(recId1);
        assertThat(prescribed).hasSize(2);
        assertThat(dispensaciones.obtenerTotalesDispensadosPorRecetaId(recId1)).isEmpty();
        assertThat(dispensaciones.obtenerSaldosPorRecetaId(recId1))
                .extracting(SaldoMedicamentoDto::estado)
                .containsExactly("PENDIENTE", "PENDIENTE");

        // Primera dispensación parcial (med1: 4 de 10; med2: 6 de 6)
        Long canonicalSiteId = owner.queryForObject("SELECT ID FROM SEDE", Long.class);
        String dispPub1 = UUID.randomUUID().toString();
        Long dispId1 = dispensaciones.crearDispensacion(
                new Dispensacion(null, dispPub1, recId1, 99999L, dispenserUserId, "Entrega parcial", null));
        Dispensacion dispEntity1 = dispensaciones.buscarEntidadPorPublicId(dispPub1).orElseThrow();
        assertThat(dispEntity1.id()).isEqualTo(dispId1);
        assertThat(dispEntity1.sedeId()).isEqualTo(canonicalSiteId);

        dispensaciones.guardarDetalles(dispId1, List.of(
                new DispensacionDetalle(null, dispId1, prescribed.get(0).recetaDetalleId(), 4,
                        "LOTE-1", LocalDate.of(2027, 12, 31), null),
                new DispensacionDetalle(null, dispId1, prescribed.get(1).recetaDetalleId(), 6,
                        null, null, null)));

        DispensacionResponse dispView1 = dispensaciones.buscarPorPublicId(dispPub1).orElseThrow();
        assertThat(dispView1.detalles()).hasSize(2);
        assertThat(dispensaciones.listarPorRecetaId(recId1)).hasSize(1);

        Map<Long, Integer> totalsAfterFirst = dispensaciones.obtenerTotalesDispensadosPorRecetaId(recId1);
        assertThat(totalsAfterFirst).containsEntry(prescribed.get(0).recetaDetalleId(), 4);
        assertThat(totalsAfterFirst).containsEntry(prescribed.get(1).recetaDetalleId(), 6);

        List<SaldoMedicamentoDto> partialBalances = dispensaciones.obtenerSaldosPorRecetaId(recId1);
        assertThat(partialBalances.get(0).estado()).isEqualTo("DISPENSADA_PARCIAL");
        assertThat(partialBalances.get(0).saldoPendiente()).isEqualTo(6);
        assertThat(partialBalances.get(1).estado()).isEqualTo("DISPENSADA_TOTAL");
        assertThat(partialBalances.get(1).saldoPendiente()).isZero();

        RecetaDispensacionResponse dispensingPartial =
                dispensaciones.buscarRecetaDispensacionPorPublicId(recPub1).orElseThrow();
        assertThat(dispensingPartial.codigoReclamacion()).isEqualTo("REC-A1B2C3D4");
        assertThat(dispensingPartial.estadoDispensacion()).isEqualTo(EstadoRecetaDispensacion.DISPENSADA_PARCIAL);
        assertThat(dispensingPartial.entregasPrevias()).hasSize(1);

        // Segunda dispensación completando med1 (6 restantes -> total 10)
        String dispPub2 = UUID.randomUUID().toString();
        Long dispId2 = dispensaciones.crearDispensacion(
                new Dispensacion(null, dispPub2, recId1, canonicalSiteId, dispenserUserId, null, null));
        dispensaciones.guardarDetalles(dispId2, List.of(
                new DispensacionDetalle(null, dispId2, prescribed.get(0).recetaDetalleId(), 6,
                        "LOTE-2", null, null)));

        RecetaDispensacionResponse dispensingTotal =
                dispensaciones.buscarRecetaDispensacionPorPublicId(recPub1).orElseThrow();
        assertThat(dispensingTotal.estadoDispensacion()).isEqualTo(EstadoRecetaDispensacion.DISPENSADA_TOTAL);
        assertThat(dispensingTotal.entregasPrevias()).hasSize(2);

        // Búsqueda de ventanilla: sin filtro, por documento, por nombre y por código REC-
        assertThat(dispensaciones.contarRecetasDispensacion(null)).isGreaterThanOrEqualTo(2);
        assertThat(dispensaciones.buscarRecetasDispensacion("600111", 0, 10))
                .extracting(RecetaDispensacionResponse::recetaPublicId)
                .contains(recPub1, recPub2);
        assertThat(dispensaciones.buscarRecetasDispensacion("lucia vega", 0, 10))
                .extracting(RecetaDispensacionResponse::recetaPublicId)
                .contains(recPub1);
        assertThat(dispensaciones.contarRecetasDispensacion("REC-A1B2C3D4")).isEqualTo(1);
        assertThat(dispensaciones.buscarRecetasDispensacion("REC-A1B2C3D4", 0, 10))
                .extracting(RecetaDispensacionResponse::recetaPublicId)
                .containsExactly(recPub1);

        // Clave duplicada al crear dispensación -> ConflictoOperacionException
        assertThatThrownBy(() -> dispensaciones.crearDispensacion(
                        new Dispensacion(null, dispPub1, recId1, canonicalSiteId, dispenserUserId, null, null)))
                .isInstanceOf(ConflictoOperacionException.class);

        // Triggers de inmutabilidad de DISPENSACION (-20030/-20031) traducidos a ConflictoOperacionException
        assertThatThrownBy(() -> {
            try {
                owner.update("UPDATE DISPENSACION SET OBSERVACIONES = 'alterada' WHERE ID = ?", dispId1);
            } catch (DataAccessException ex) {
                throw translator.translate(ex);
            }
        }).isInstanceOf(ConflictoOperacionException.class);

        assertThatThrownBy(() -> {
            try {
                owner.update("DELETE FROM DISPENSACION WHERE ID = ?", dispId1);
            } catch (DataAccessException ex) {
                throw translator.translate(ex);
            }
        }).isInstanceOf(ConflictoOperacionException.class);
    }
}
