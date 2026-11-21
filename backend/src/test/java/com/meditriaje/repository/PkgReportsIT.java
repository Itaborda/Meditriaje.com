package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.report.DistributionItemDto;
import com.meditriaje.repository.plsql.OracleErrorTranslator;
import com.meditriaje.repository.plsql.PackageGateway;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * PKG_REPORTS contra Oracle Free real, llamado como MEDITRIAJE_APP a través de {@link ReporteRepository}.
 * Cubre las 12 rutinas con base vacía, datos poblados y filtros por ventana temporal.
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PkgReportsIT {

    private static final String OWNER = "MT_PKG_REPORTS";

    private static OracleContainer oracle;
    private static JdbcTemplate owner;
    private static ReportRepository reportes;

    @BeforeAll
    static void migrate() {
        oracle = OracleTestDatabase.newContainer().withStartupTimeout(Duration.ofMinutes(3));
        oracle.start();
        DataSource ownerDs = OracleTestDatabase.provisionOwner(oracle, OWNER);
        OracleTestDatabase.flyway(ownerDs).migrate();
        owner = new JdbcTemplate(ownerDs);
        DataSource appDs = OracleTestDatabase.dataSource(
                oracle, OracleTestDatabase.APP_USER, OracleTestDatabase.APP_PASSWORD);
        reportes = new ReportRepository(new PackageGateway(appDs, OWNER, new OracleErrorTranslator()));
    }

    @AfterAll
    static void stop() {
        if (oracle != null) {
            oracle.stop();
        }
    }

    @Test
    @Order(1)
    void emptyDatabaseReturnsZerosEmptyCollectionsAndZeroStatuses() {
        Instant now = Instant.now();

        assertThat(reportes.countAppointmentsByStatus(null, null)).isEmpty();
        assertThat(reportes.getAppointmentsBySpecialty(null, null)).isEmpty();
        assertThat(reportes.getAppointmentsBySite(null, null)).isEmpty();
        assertThat(reportes.countTriagesByLevel(null, null)).isEmpty();
        assertThat(reportes.countEmergencyTriages(null, null)).isZero();
        assertThat(reportes.getTriagesByRoute(null, null)).isEmpty();
        assertThat(reportes.countIssuedPrescriptions(null, null)).isZero();
        assertThat(reportes.countDispensedUnits(null, null)).isZero();
        assertThat(reportes.countPrescriptionsByDispensationStatus(null, null))
                .containsEntry("PENDIENTE", 0L)
                .containsEntry("DISPENSADA_PARCIAL", 0L)
                .containsEntry("DISPENSADA_TOTAL", 0L);
        assertThat(reportes.countBreakGlassActivations(null, null)).isZero();
        assertThat(reportes.countActiveBreakGlass(now)).isZero();
        assertThat(reportes.getBreakGlassBySpecialty(null, null)).isEmpty();
    }

    @Test
    @Order(2)
    void populatedDatabaseAggregatesAndFiltersByWindow() {
        Instant tBase = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant tOld = tBase.minus(10, ChronoUnit.DAYS);
        Instant tIn1 = tBase.minus(2, ChronoUnit.DAYS);
        Instant tIn2 = tBase.minus(1, ChronoUnit.DAYS);
        Instant windowFrom = tBase.minus(3, ChronoUnit.DAYS);
        Instant windowTo = tBase.plus(1, ChronoUnit.HOURS);

        // Actores base
        owner.update("INSERT INTO USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH) VALUES ('rep-u-pat', 'pat@rep.test', 'x')");
        owner.update("INSERT INTO USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH) VALUES ('rep-u-p1', 'p1@rep.test', 'x')");
        owner.update("INSERT INTO USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH) VALUES ('rep-u-p2', 'p2@rep.test', 'x')");
        owner.update("INSERT INTO USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH) VALUES ('rep-u-farm', 'farm@rep.test', 'x')");

        Long uPatId = id("USUARIO", "rep-u-pat");
        Long uP1Id = id("USUARIO", "rep-u-p1");
        Long uP2Id = id("USUARIO", "rep-u-p2");
        Long uFarmId = id("USUARIO", "rep-u-farm");

        owner.update("""
                INSERT INTO PACIENTE (USUARIO_ID, PUBLIC_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO, NOMBRES, APELLIDOS,
                                      FECHA_NACIMIENTO)
                VALUES (?, 'rep-pat-1', 'CC', '9001', 'Laura', 'Reportes', DATE '1992-03-10')""", uPatId);
        Long patId = id("PACIENTE", "rep-pat-1");

        owner.update("INSERT INTO ESPECIALIDAD (PUBLIC_ID, NOMBRE, DURACION_SLOT_MIN) VALUES ('rep-esp-1', 'Cardiologia Rep', 30)");
        owner.update("INSERT INTO ESPECIALIDAD (PUBLIC_ID, NOMBRE, DURACION_SLOT_MIN) VALUES ('rep-esp-2', 'Pediatria Rep', 20)");
        Long esp1Id = id("ESPECIALIDAD", "rep-esp-1");
        Long esp2Id = id("ESPECIALIDAD", "rep-esp-2");

        owner.update("""
                INSERT INTO PROFESIONAL (USUARIO_ID, PUBLIC_ID, ESPECIALIDAD_ID, REGISTRO_MEDICO, NOMBRES, APELLIDOS,
                                         NUMERO_DOCUMENTO)
                VALUES (?, 'rep-prof-1', ?, 'RM-REP-1', 'Carlos', 'Cardio', '9101')""", uP1Id, esp1Id);
        owner.update("""
                INSERT INTO PROFESIONAL (USUARIO_ID, PUBLIC_ID, ESPECIALIDAD_ID, REGISTRO_MEDICO, NOMBRES, APELLIDOS,
                                         NUMERO_DOCUMENTO)
                VALUES (?, 'rep-prof-2', ?, 'RM-REP-2', 'Sofia', 'Pediatra', '9102')""", uP2Id, esp2Id);
        Long prof1Id = id("PROFESIONAL", "rep-prof-1");
        Long prof2Id = id("PROFESIONAL", "rep-prof-2");

        Long sedeId = owner.queryForObject("SELECT MIN(ID) FROM CONFIGURACION_HOSPITAL", Long.class);
        String sedePublicId = owner.queryForObject("SELECT PUBLIC_ID FROM CONFIGURACION_HOSPITAL WHERE ID = ?", String.class, sedeId);
        String sedeNombre = owner.queryForObject("SELECT RAZON_SOCIAL FROM CONFIGURACION_HOSPITAL WHERE ID = ?", String.class, sedeId);

        // 4 slots y 4 citas (3 dentro de ventana: 2 ATENDIDA en esp-1, 1 CANCELADA en esp-2; 1 antigua CANCELADA en esp-2)
        insertSlot("rep-slot-1", prof1Id, esp1Id, tBase.plus(1, ChronoUnit.DAYS));
        insertSlot("rep-slot-2", prof1Id, esp1Id, tBase.plus(2, ChronoUnit.DAYS));
        insertSlot("rep-slot-3", prof2Id, esp2Id, tBase.plus(3, ChronoUnit.DAYS));
        insertSlot("rep-slot-4", prof2Id, esp2Id, tBase.plus(4, ChronoUnit.DAYS));

        Long s1 = id("DISPONIBILIDAD_SLOT", "rep-slot-1");
        Long s2 = id("DISPONIBILIDAD_SLOT", "rep-slot-2");
        Long s3 = id("DISPONIBILIDAD_SLOT", "rep-slot-3");
        Long s4 = id("DISPONIBILIDAD_SLOT", "rep-slot-4");

        owner.update("INSERT INTO CITA (PUBLIC_ID, SLOT_ID, PACIENTE_ID, ESTADO, CREATED_AT) VALUES ('rep-c1', ?, ?, 'ATENDIDA', ?)",
                s1, patId, Timestamp.from(tIn1));
        owner.update("INSERT INTO CITA (PUBLIC_ID, SLOT_ID, PACIENTE_ID, ESTADO, CREATED_AT) VALUES ('rep-c2', ?, ?, 'ATENDIDA', ?)",
                s2, patId, Timestamp.from(tIn2));
        owner.update("INSERT INTO CITA (PUBLIC_ID, SLOT_ID, PACIENTE_ID, ESTADO, CREATED_AT) VALUES ('rep-c3', ?, ?, 'CANCELADA', ?)",
                s3, patId, Timestamp.from(tIn2));
        owner.update("INSERT INTO CITA (PUBLIC_ID, SLOT_ID, PACIENTE_ID, ESTADO, CREATED_AT) VALUES ('rep-c4', ?, ?, 'CANCELADA', ?)",
                s4, patId, Timestamp.from(tOld));

        // 4 triajes (3 en ventana: 1 Nivel I emergencia URGENCIAS, 2 Nivel III no emergencia CITA_PRESENCIAL; 1 antiguo Nivel V)
        owner.update("""
                INSERT INTO TRIAJE (PUBLIC_ID, PACIENTE_ID, VERSION_REGLAS, NIVEL_PRIORIDAD, RUTA_SUGERIDA, ES_EMERGENCIA, CREATED_AT)
                VALUES ('rep-t1', ?, 'v1-prototipo', 'I', 'URGENCIAS', 1, ?)""", patId, Timestamp.from(tIn1));
        owner.update("""
                INSERT INTO TRIAJE (PUBLIC_ID, PACIENTE_ID, VERSION_REGLAS, NIVEL_PRIORIDAD, RUTA_SUGERIDA, ES_EMERGENCIA, CREATED_AT)
                VALUES ('rep-t2', ?, 'v1-prototipo', 'III', 'CITA_PRESENCIAL', 0, ?)""", patId, Timestamp.from(tIn2));
        owner.update("""
                INSERT INTO TRIAJE (PUBLIC_ID, PACIENTE_ID, VERSION_REGLAS, NIVEL_PRIORIDAD, RUTA_SUGERIDA, ES_EMERGENCIA, CREATED_AT)
                VALUES ('rep-t3', ?, 'v1-prototipo', 'III', 'CITA_PRESENCIAL', 0, ?)""", patId, Timestamp.from(tIn2));
        owner.update("""
                INSERT INTO TRIAJE (PUBLIC_ID, PACIENTE_ID, VERSION_REGLAS, NIVEL_PRIORIDAD, RUTA_SUGERIDA, ES_EMERGENCIA, CREATED_AT)
                VALUES ('rep-t4', ?, 'v1-prototipo', 'V', 'CONSULTA_PROGRAMADA', 0, ?)""", patId, Timestamp.from(tOld));

        // Atenciones + recetas (3 en ventana: 1 PENDIENTE, 1 PARCIAL, 1 TOTAL; 1 antigua PENDIENTE)
        Long c1 = id("CITA", "rep-c1");
        Long c2 = id("CITA", "rep-c2");
        Long c3 = id("CITA", "rep-c3");
        Long c4 = id("CITA", "rep-c4");

        owner.update("INSERT INTO ATENCION (PUBLIC_ID, CITA_ID, PACIENTE_ID, PROFESIONAL_ID) VALUES ('rep-at1', ?, ?, ?)", c1, patId, prof1Id);
        owner.update("INSERT INTO ATENCION (PUBLIC_ID, CITA_ID, PACIENTE_ID, PROFESIONAL_ID) VALUES ('rep-at2', ?, ?, ?)", c2, patId, prof1Id);
        owner.update("INSERT INTO ATENCION (PUBLIC_ID, CITA_ID, PACIENTE_ID, PROFESIONAL_ID) VALUES ('rep-at3', ?, ?, ?)", c3, patId, prof2Id);
        owner.update("INSERT INTO ATENCION (PUBLIC_ID, CITA_ID, PACIENTE_ID, PROFESIONAL_ID) VALUES ('rep-at4', ?, ?, ?)", c4, patId, prof2Id);

        Long at1 = id("ATENCION", "rep-at1");
        Long at2 = id("ATENCION", "rep-at2");
        Long at3 = id("ATENCION", "rep-at3");
        Long at4 = id("ATENCION", "rep-at4");

        Long medId = owner.queryForObject("SELECT MIN(ID) FROM MEDICAMENTO", Long.class);

        owner.update("INSERT INTO RECETA (PUBLIC_ID, ATENCION_ID, PACIENTE_ID, PROFESIONAL_ID, CREATED_AT) VALUES ('rep-rx1', ?, ?, ?, ?)",
                at1, patId, prof1Id, Timestamp.from(tIn1));
        owner.update("INSERT INTO RECETA (PUBLIC_ID, ATENCION_ID, PACIENTE_ID, PROFESIONAL_ID, CREATED_AT) VALUES ('rep-rx2', ?, ?, ?, ?)",
                at2, patId, prof1Id, Timestamp.from(tIn2));
        owner.update("INSERT INTO RECETA (PUBLIC_ID, ATENCION_ID, PACIENTE_ID, PROFESIONAL_ID, CREATED_AT) VALUES ('rep-rx3', ?, ?, ?, ?)",
                at3, patId, prof2Id, Timestamp.from(tIn2));
        owner.update("INSERT INTO RECETA (PUBLIC_ID, ATENCION_ID, PACIENTE_ID, PROFESIONAL_ID, CREATED_AT) VALUES ('rep-rx4', ?, ?, ?, ?)",
                at4, patId, prof2Id, Timestamp.from(tOld));

        Long rx1 = id("RECETA", "rep-rx1");
        Long rx2 = id("RECETA", "rep-rx2");
        Long rx3 = id("RECETA", "rep-rx3");
        Long rx4 = id("RECETA", "rep-rx4");

        insertRxDetail(rx1, medId, 10); // PENDIENTE (0 entregadas)
        insertRxDetail(rx2, medId, 10); // PARCIAL (4 entregadas)
        insertRxDetail(rx3, medId, 6);  // TOTAL (6 entregadas)
        insertRxDetail(rx4, medId, 8);  // antigua PENDIENTE

        Long rd2 = owner.queryForObject("SELECT ID FROM RECETA_DETALLE WHERE RECETA_ID = ?", Long.class, rx2);
        Long rd3 = owner.queryForObject("SELECT ID FROM RECETA_DETALLE WHERE RECETA_ID = ?", Long.class, rx3);

        owner.update("INSERT INTO DISPENSACION (PUBLIC_ID, RECETA_ID, USUARIO_ID, CREATED_AT) VALUES ('rep-disp1', ?, ?, ?)",
                rx2, uFarmId, Timestamp.from(tIn2));
        owner.update("INSERT INTO DISPENSACION (PUBLIC_ID, RECETA_ID, USUARIO_ID, CREATED_AT) VALUES ('rep-disp2', ?, ?, ?)",
                rx3, uFarmId, Timestamp.from(tOld));

        Long disp1 = id("DISPENSACION", "rep-disp1");
        Long disp2 = id("DISPENSACION", "rep-disp2");

        owner.update("INSERT INTO DISPENSACION_DETALLE (DISPENSACION_ID, RECETA_DETALLE_ID, CANTIDAD_ENTREGADA) VALUES (?, ?, 4)", disp1, rd2);
        owner.update("INSERT INTO DISPENSACION_DETALLE (DISPENSACION_ID, RECETA_DETALLE_ID, CANTIDAD_ENTREGADA) VALUES (?, ?, 6)", disp2, rd3);

        // Break-Glass (3: 2 en ventana para esp-1, 1 activo y 1 expirado; 1 antiguo para esp-2 ya expirado)
        String motivo = "Emergencia clinica verificada para reporte operativo";
        owner.update("""
                INSERT INTO ACCESO_BREAK_GLASS (PUBLIC_ID, PROFESIONAL_ID, PACIENTE_ID, MOTIVO, FECHA_EXPIRACION, CREATED_AT)
                VALUES ('rep-bg1', ?, ?, ?, ?, ?)""",
                prof1Id, patId, motivo, Timestamp.from(tBase.plus(2, ChronoUnit.HOURS)), Timestamp.from(tIn1));
        owner.update("""
                INSERT INTO ACCESO_BREAK_GLASS (PUBLIC_ID, PROFESIONAL_ID, PACIENTE_ID, MOTIVO, FECHA_EXPIRACION, CREATED_AT)
                VALUES ('rep-bg2', ?, ?, ?, ?, ?)""",
                prof1Id, patId, motivo, Timestamp.from(tBase.minus(1, ChronoUnit.HOURS)), Timestamp.from(tIn2));
        owner.update("""
                INSERT INTO ACCESO_BREAK_GLASS (PUBLIC_ID, PROFESIONAL_ID, PACIENTE_ID, MOTIVO, FECHA_EXPIRACION, CREATED_AT)
                VALUES ('rep-bg3', ?, ?, ?, ?, ?)""",
                prof2Id, patId, motivo, Timestamp.from(tOld.plus(1, ChronoUnit.HOURS)), Timestamp.from(tOld));

        // 1. Verificar métricas sin ventana (todas las filas)
        assertThat(reportes.countAppointmentsByStatus(null, null))
                .containsEntry("ATENDIDA", 2L)
                .containsEntry("CANCELADA", 2L);
        assertThat(reportes.getAppointmentsBySite(null, null))
                .containsExactly(new DistributionItemDto(sedePublicId, sedeNombre, 4L, 100.0));
        assertThat(reportes.countEmergencyTriages(null, null)).isEqualTo(1L);
        assertThat(reportes.countIssuedPrescriptions(null, null)).isEqualTo(4L);
        assertThat(reportes.countDispensedUnits(null, null)).isEqualTo(10L);
        assertThat(reportes.countPrescriptionsByDispensationStatus(null, null))
                .containsEntry("PENDIENTE", 2L)
                .containsEntry("DISPENSADA_PARCIAL", 1L)
                .containsEntry("DISPENSADA_TOTAL", 1L);
        assertThat(reportes.countBreakGlassActivations(null, null)).isEqualTo(3L);
        assertThat(reportes.countActiveBreakGlass(tBase)).isEqualTo(1L);

        // 2. Verificar métricas filtradas por ventana [windowFrom, windowTo]
        assertThat(reportes.countAppointmentsByStatus(windowFrom, windowTo))
                .containsEntry("ATENDIDA", 2L)
                .containsEntry("CANCELADA", 1L);

        List<DistributionItemDto> citasEsp = reportes.getAppointmentsBySpecialty(windowFrom, windowTo);
        assertThat(citasEsp).containsExactly(
                new DistributionItemDto("rep-esp-1", "Cardiologia Rep", 2L, 66.67),
                new DistributionItemDto("rep-esp-2", "Pediatria Rep", 1L, 33.33)
        );

        assertThat(reportes.countTriagesByLevel(windowFrom, windowTo))
                .containsEntry("I", 1L)
                .containsEntry("III", 2L)
                .doesNotContainKey("V");
        assertThat(reportes.countEmergencyTriages(windowFrom, windowTo)).isEqualTo(1L);
        assertThat(reportes.getTriagesByRoute(windowFrom, windowTo)).containsExactly(
                new DistributionItemDto("CITA_PRESENCIAL", "CITA_PRESENCIAL", 2L, 66.67),
                new DistributionItemDto("URGENCIAS", "URGENCIAS", 1L, 33.33)
        );

        assertThat(reportes.countIssuedPrescriptions(windowFrom, windowTo)).isEqualTo(3L);
        assertThat(reportes.countDispensedUnits(windowFrom, windowTo)).isEqualTo(4L);
        assertThat(reportes.countPrescriptionsByDispensationStatus(windowFrom, windowTo))
                .containsEntry("PENDIENTE", 1L)
                .containsEntry("DISPENSADA_PARCIAL", 1L)
                .containsEntry("DISPENSADA_TOTAL", 1L);

        assertThat(reportes.countBreakGlassActivations(windowFrom, windowTo)).isEqualTo(2L);
        assertThat(reportes.getBreakGlassBySpecialty(windowFrom, windowTo)).containsExactly(
                new DistributionItemDto("rep-esp-1", "Cardiologia Rep", 2L, 100.0)
        );
    }

    private static Long id(String table, String publicId) {
        return owner.queryForObject("SELECT ID FROM " + table + " WHERE PUBLIC_ID = ?", Long.class, publicId);
    }

    private static void insertSlot(String publicId, Long profId, Long espId, Instant start) {
        owner.update("""
                INSERT INTO DISPONIBILIDAD_SLOT (PUBLIC_ID, PROFESIONAL_ID, ESPECIALIDAD_ID,
                                                 FECHA_HORA_INICIO, FECHA_HORA_FIN, MODALIDAD, ESTADO)
                VALUES (?, ?, ?, ?, ?, 'PRESENCIAL', 'OCUPADO')""",
                publicId, profId, espId, Timestamp.from(start), Timestamp.from(start.plus(20, ChronoUnit.MINUTES)));
    }

    private static void insertRxDetail(Long recetaId, Long medId, int cantidad) {
        owner.update("""
                INSERT INTO RECETA_DETALLE (RECETA_ID, MEDICAMENTO_ID, SNAPSHOT_NOMBRE, SNAPSHOT_PRINCIPIO_ACTIVO,
                                            SNAPSHOT_PRESENTACION, SNAPSHOT_CONCENTRACION, DOSIS, FRECUENCIA,
                                            DURACION_DIAS, CANTIDAD)
                VALUES (?, ?, 'Acetaminofen', 'Acetaminofen', 'Tableta', '500 mg', '500 mg', 'Cada 8 horas', 5, ?)""",
                recetaId, medId, cantidad);
    }
}
