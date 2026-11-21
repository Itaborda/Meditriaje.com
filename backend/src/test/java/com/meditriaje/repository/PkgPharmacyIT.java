package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.pharmacy.DispensationResponse;
import com.meditriaje.dto.pharmacy.PrescriptionDispensationResponse;
import com.meditriaje.dto.pharmacy.MedicationBalanceDto;
import com.meditriaje.dto.prescription.MedicationResponse;
import com.meditriaje.dto.prescription.PrescriptionResponse;
import com.meditriaje.exception.OperationConflictException;
import com.meditriaje.model.Dispensation;
import com.meditriaje.model.DispensationDetail;
import com.meditriaje.model.PrescriptionDispensationStatus;
import com.meditriaje.model.Medication;
import com.meditriaje.model.Prescription;
import com.meditriaje.model.PrescriptionDetail;
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
    private static MedicationRepository medicamentos;
    private static PrescriptionRepository recetas;
    private static DispensationRepository dispensaciones;

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
        medicamentos = new MedicationRepository(gateway);
        recetas = new PrescriptionRepository(gateway);
        dispensaciones = new DispensationRepository(gateway);
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
        Long espId = owner.queryForObject("SELECT ESPECIALIDAD_ID FROM PROFESIONAL WHERE ID = ?", Long.class, professionalId);
        String slotPublicId = UUID.randomUUID().toString();
        Instant start = Instant.now().plus(hourOffset, ChronoUnit.HOURS);
        owner.update(
                "INSERT INTO DISPONIBILIDAD_SLOT (PUBLIC_ID, PROFESIONAL_ID, ESPECIALIDAD_ID, FECHA_HORA_INICIO, FECHA_HORA_FIN) "
                        + "VALUES (?, ?, ?, ?, ?)",
                slotPublicId, professionalId, espId, Timestamp.from(start),
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
        List<MedicationResponse> all = medicamentos.listActive(null, 0, 50);
        assertThat(all).isNotEmpty();
        assertThat(medicamentos.countActive(null)).isEqualTo(all.size());

        List<MedicationResponse> filtered = medicamentos.listActive("acetaminofen", 0, 10);
        assertThat(filtered).isNotEmpty();
        assertThat(medicamentos.countActive("acetaminofen")).isEqualTo(filtered.size());

        String pubId = filtered.get(0).publicId();
        Medication byPub = medicamentos.findByPublicId(pubId).orElseThrow();
        assertThat(byPub.code()).isEqualTo(filtered.get(0).code());
        assertThat(medicamentos.findById(byPub.id())).contains(byPub);
        assertThat(medicamentos.findByPublicId("no-existe")).isEmpty();
        assertThat(medicamentos.findById(-1L)).isEmpty();
    }

    @Test
    void prescriptionAndDispensationFlowWithBalancesSearchAndTriggers() {
        Long patientId = newPatient("111", "Lucia", "Vega");
        Long professionalId = newProfessional("111");
        Long dispenserUserId = newUser("farm");
        Long encounter1 = newEncounter(patientId, professionalId, 1);
        Long encounter2 = newEncounter(patientId, professionalId, 2);

        Medication med1 = medicamentos.listActive("acetaminofen", 0, 5).stream()
                .findFirst().flatMap(m -> medicamentos.findByPublicId(m.publicId())).orElseThrow();
        Medication med2 = medicamentos.listActive("ibuprofeno", 0, 5).stream()
                .findFirst().flatMap(m -> medicamentos.findByPublicId(m.publicId())).orElseThrow();

        String recPub1 = "a1b2c3d4-" + UUID.randomUUID().toString().substring(9);
        Long recId1 = recetas.createPrescription(new Prescription(null, recPub1, encounter1, patientId, professionalId, 30, null));
        assertThat(recId1).isPositive();
        assertThat(recetas.existsByAttentionId(encounter1)).isTrue();
        assertThat(recetas.existsByAttentionId(-1L)).isFalse();

        recetas.saveDetails(recId1, List.of(
                new PrescriptionDetail(null, recId1, med1.id(), med1.brandName(), med1.activeIngredient(),
                        med1.presentation(), med1.concentration(), "500 mg", "Cada 8 horas", 5, 10, "Con agua"),
                new PrescriptionDetail(null, recId1, med2.id(), med2.brandName(), med2.activeIngredient(),
                        med2.presentation(), med2.concentration(), "400 mg", "Cada 12 horas", 3, 6, null)));

        // Segunda receta para probar paginación y orden
        String recPub2 = UUID.randomUUID().toString();
        Long recId2 = recetas.createPrescription(new Prescription(null, recPub2, encounter2, patientId, professionalId, 15, null));
        recetas.saveDetails(recId2, List.of(
                new PrescriptionDetail(null, recId2, med1.id(), med1.brandName(), med1.activeIngredient(),
                        med1.presentation(), med1.concentration(), "500 mg", "Cada 12 horas", 2, 4, null)));

        Prescription entity1 = recetas.findEntityByPublicId(recPub1).orElseThrow();
        assertThat(entity1.id()).isEqualTo(recId1);

        PrescriptionResponse view1 = recetas.findByPublicId(recPub1).orElseThrow();
        assertThat(view1.details()).hasSize(2);
        assertThat(view1.details().get(1).instructions()).isNull();
        assertThat(recetas.countByPatientId(patientId)).isEqualTo(2);

        List<PrescriptionResponse> page0 = recetas.listByPatientId(patientId, 0, 1);
        List<PrescriptionResponse> page1 = recetas.listByPatientId(patientId, 1, 1);
        assertThat(page0).extracting(PrescriptionResponse::publicId).containsExactly(recPub2);
        assertThat(page1).extracting(PrescriptionResponse::publicId).containsExactly(recPub1);

        // Ítems prescritos y saldos iniciales
        List<DispensationRepository.PrescribedItemInfo> prescribed = dispensaciones.findPrescriptionItems(recId1);
        assertThat(prescribed).hasSize(2);
        assertThat(dispensaciones.getDispensedTotalsByPrescriptionId(recId1)).isEmpty();
        assertThat(dispensaciones.getBalancesByPrescriptionId(recId1))
                .extracting(MedicationBalanceDto::status)
                .containsExactly("PENDIENTE", "PENDIENTE");

        // Primera dispensación parcial (med1: 4 de 10; med2: 6 de 6)
        Long canonicalSiteId = owner.queryForObject("SELECT ID FROM CONFIGURACION_HOSPITAL", Long.class);
        String dispPub1 = UUID.randomUUID().toString();
        Long dispId1 = dispensaciones.createDispensation(
                new Dispensation(null, dispPub1, recId1, 99999L, dispenserUserId, "Entrega parcial", null));
        Dispensation dispEntity1 = dispensaciones.findEntityByPublicId(dispPub1).orElseThrow();
        assertThat(dispEntity1.id()).isEqualTo(dispId1);
        assertThat(dispEntity1.siteId()).isEqualTo(canonicalSiteId);

        dispensaciones.saveDetails(dispId1, List.of(
                new DispensationDetail(null, dispId1, prescribed.get(0).prescriptionDetailId(), 4,
                        "LOTE-1", LocalDate.of(2027, 12, 31), null),
                new DispensationDetail(null, dispId1, prescribed.get(1).prescriptionDetailId(), 6,
                        null, null, null)));

        DispensationResponse dispView1 = dispensaciones.findByPublicId(dispPub1).orElseThrow();
        assertThat(dispView1.details()).hasSize(2);
        assertThat(dispensaciones.listByPrescriptionId(recId1)).hasSize(1);

        Map<Long, Integer> totalsAfterFirst = dispensaciones.getDispensedTotalsByPrescriptionId(recId1);
        assertThat(totalsAfterFirst).containsEntry(prescribed.get(0).prescriptionDetailId(), 4);
        assertThat(totalsAfterFirst).containsEntry(prescribed.get(1).prescriptionDetailId(), 6);

        List<MedicationBalanceDto> partialBalances = dispensaciones.getBalancesByPrescriptionId(recId1);
        assertThat(partialBalances.get(0).status()).isEqualTo("DISPENSADA_PARCIAL");
        assertThat(partialBalances.get(0).pendingBalance()).isEqualTo(6);
        assertThat(partialBalances.get(1).status()).isEqualTo("DISPENSADA_TOTAL");
        assertThat(partialBalances.get(1).pendingBalance()).isZero();

        PrescriptionDispensationResponse dispensingPartial =
                dispensaciones.findPrescriptionDispensationByPublicId(recPub1).orElseThrow();
        assertThat(dispensingPartial.claimCode()).isEqualTo("REC-A1B2C3D4");
        assertThat(dispensingPartial.dispensationStatus()).isEqualTo(PrescriptionDispensationStatus.DISPENSADA_PARCIAL);
        assertThat(dispensingPartial.previousDeliveries()).hasSize(1);

        // Segunda dispensación completando med1 (6 restantes -> total 10)
        String dispPub2 = UUID.randomUUID().toString();
        Long dispId2 = dispensaciones.createDispensation(
                new Dispensation(null, dispPub2, recId1, canonicalSiteId, dispenserUserId, null, null));
        dispensaciones.saveDetails(dispId2, List.of(
                new DispensationDetail(null, dispId2, prescribed.get(0).prescriptionDetailId(), 6,
                        "LOTE-2", null, null)));

        PrescriptionDispensationResponse dispensingTotal =
                dispensaciones.findPrescriptionDispensationByPublicId(recPub1).orElseThrow();
        assertThat(dispensingTotal.dispensationStatus()).isEqualTo(PrescriptionDispensationStatus.DISPENSADA_TOTAL);
        assertThat(dispensingTotal.previousDeliveries()).hasSize(2);

        // Búsqueda de ventanilla: sin filtro, por documento, por nombre y por código REC-
        assertThat(dispensaciones.countPrescriptionDispensations(null)).isGreaterThanOrEqualTo(2);
        assertThat(dispensaciones.searchPrescriptionDispensations("600111", 0, 10))
                .extracting(PrescriptionDispensationResponse::prescriptionPublicId)
                .contains(recPub1, recPub2);
        assertThat(dispensaciones.searchPrescriptionDispensations("lucia vega", 0, 10))
                .extracting(PrescriptionDispensationResponse::prescriptionPublicId)
                .contains(recPub1);
        assertThat(dispensaciones.countPrescriptionDispensations("REC-A1B2C3D4")).isEqualTo(1);
        assertThat(dispensaciones.searchPrescriptionDispensations("REC-A1B2C3D4", 0, 10))
                .extracting(PrescriptionDispensationResponse::prescriptionPublicId)
                .containsExactly(recPub1);

        // Clave duplicada al crear dispensación -> ConflictoOperacionException
        assertThatThrownBy(() -> dispensaciones.createDispensation(
                        new Dispensation(null, dispPub1, recId1, canonicalSiteId, dispenserUserId, null, null)))
                .isInstanceOf(OperationConflictException.class);

        // Triggers de inmutabilidad de DISPENSACION (-20030/-20031) traducidos a ConflictoOperacionException
        assertThatThrownBy(() -> {
            try {
                owner.update("UPDATE DISPENSACION SET OBSERVACIONES = 'alterada' WHERE ID = ?", dispId1);
            } catch (DataAccessException ex) {
                throw translator.translate(ex);
            }
        }).isInstanceOf(OperationConflictException.class);

        assertThatThrownBy(() -> {
            try {
                owner.update("DELETE FROM DISPENSACION WHERE ID = ?", dispId1);
            } catch (DataAccessException ex) {
                throw translator.translate(ex);
            }
        }).isInstanceOf(OperationConflictException.class);
    }
}
