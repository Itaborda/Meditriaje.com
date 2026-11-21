package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.operational.OperationalAlertResponse;
import com.meditriaje.exception.OperationConflictException;
import com.meditriaje.model.TemporaryQrAccess;
import com.meditriaje.model.OperationalAlert;
import com.meditriaje.model.InpatientQrTracking;
import com.meditriaje.repository.plsql.OracleErrorTranslator;
import com.meditriaje.repository.plsql.PackageGateway;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * PKG_OPERATIONS contra Oracle Free real, llamado como MEDITRIAJE_APP a través de los repositorios
 * {@link AlertaOperativaRepository}, {@link AccesoTemporalQrRepository}, {@link SeguimientoQrRepository}
 * y {@link DemoSeedRepository}.
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class PkgOperationsIT {

    private static final String OWNER = "MT_PKG_OPERATIONS";

    private static OracleContainer oracle;
    private static JdbcTemplate owner;
    private static OperationalAlertRepository alertas;
    private static TemporaryQrAccessRepository accesosQr;
    private static TrackingQrRepository seguimientosQr;
    private static DemoSeedRepository demoSeed;

    private static Long sedeId;
    private static Long userAckId;
    private static Long pacienteId;
    private static Long episodioId;

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
        alertas = new OperationalAlertRepository(gateway);
        accesosQr = new TemporaryQrAccessRepository(gateway);
        seguimientosQr = new TrackingQrRepository(gateway);
        demoSeed = new DemoSeedRepository(gateway);
        seed();
    }

    @AfterAll
    static void stop() {
        if (oracle != null) {
            oracle.stop();
        }
    }

    private static void seed() {
        sedeId = owner.queryForObject("SELECT MIN(ID) FROM CONFIGURACION_HOSPITAL", Long.class);
        owner.update("INSERT INTO USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH) VALUES ('op-u-ack', 'ack@demo.meditriaje.test', 'x')");
        owner.update("INSERT INTO USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH) VALUES ('op-u-pat', 'pat@real.org', 'x')");
        userAckId = owner.queryForObject("SELECT ID FROM USUARIO WHERE PUBLIC_ID = 'op-u-ack'", Long.class);
        Long userPatId = owner.queryForObject("SELECT ID FROM USUARIO WHERE PUBLIC_ID = 'op-u-pat'", Long.class);

        owner.update("""
                INSERT INTO PACIENTE (USUARIO_ID, PUBLIC_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO, NOMBRES, APELLIDOS,
                                      FECHA_NACIMIENTO)
                VALUES (?, 'op-pat-1', 'CC', '7701', 'Camila', 'Operaciones', DATE '1993-07-20')""", userPatId);
        pacienteId = owner.queryForObject("SELECT ID FROM PACIENTE WHERE PUBLIC_ID = 'op-pat-1'", Long.class);

        owner.update("INSERT INTO EPISODIO_ATENCION (PUBLIC_ID, TIPO) VALUES ('op-ep-1', 'URGENCIA')");
        episodioId = owner.queryForObject("SELECT ID FROM EPISODIO_ATENCION WHERE PUBLIC_ID = 'op-ep-1'", Long.class);
    }

    @Test
    void operationalAlertsLifecycleDeduplicationAndListing() {
        // Estado null -> por defecto 'ACTIVA', sede resuelta por PKG_COMMON.get_single_site_id.
        OperationalAlert creada = alertas.save(new OperationalAlert(
                null, "op-al-1", 9999L, "SATURACION_CAMAS", "CRITICA", "Ocupacion al 92%",
                null, null, null, null, null));
        assertThat(creada.id()).isNotNull();
        assertThat(creada.siteId()).isEqualTo(sedeId);
        assertThat(creada.status()).isEqualTo("ACTIVA");
        assertThat(creada.createdAt()).isNotNull();

        assertThat(alertas.findById(creada.id())).contains(creada);
        assertThat(alertas.findById(null)).isEmpty();
        assertThat(alertas.findByPublicId("op-al-1")).contains(creada);
        assertThat(alertas.findByPublicId(" ")).isEmpty();
        assertThat(alertas.findByPublicId("no-existe")).isEmpty();

        // El índice UQ_ALERTA_OPERATIVA_ACTIVA dispara ORA-00001 -> ConflictoOperacionException.
        assertThatThrownBy(() -> alertas.save(new OperationalAlert(
                null, "op-al-dup", sedeId, "SATURACION_CAMAS", "CRITICA", "Duplicada",
                "ACTIVA", null, null, null, null)))
                .isInstanceOf(OperationConflictException.class);

        Instant ackTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        alertas.acknowledgeAlert(creada.id(), userAckId, ackTime, "Se habilitan camillas de expansion");

        OperationalAlert reconocida = alertas.findByPublicId("op-al-1").orElseThrow();
        assertThat(reconocida.status()).isEqualTo("RECONOCIDA");
        assertThat(reconocida.acknowledgedByUserId()).isEqualTo(userAckId);
        assertThat(reconocida.acknowledgedAt()).isEqualTo(ackTime);
        assertThat(reconocida.acknowledgmentReason()).isEqualTo("Se habilitan camillas de expansion");

        // Tras reconocer la primera, ya puede existir una nueva alerta ACTIVA del mismo tipo.
        alertas.save(new OperationalAlert(
                null, "op-al-2", sedeId, "SATURACION_CAMAS", "ALTA", "Nueva alerta",
                "ACTIVA", null, null, null, null));

        List<OperationalAlertResponse> activas = alertas.listBySite(sedeId, "ACTIVA");
        assertThat(activas).extracting(OperationalAlertResponse::publicId).containsExactly("op-al-2");
        assertThat(activas.get(0).acknowledgedByEmail()).isNull();

        List<OperationalAlertResponse> todas = alertas.listBySite(null, " ");
        assertThat(todas).extracting(OperationalAlertResponse::publicId).containsExactly("op-al-2", "op-al-1");
        assertThat(todas.get(1).acknowledgedByEmail()).isEqualTo("ack@demo.meditriaje.test");
    }

    @Test
    void temporaryQrAccessCreationAtomicReadsPinFailuresAndRevocation() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant future = now.plus(15, ChronoUnit.MINUTES);
        Instant past = now.minus(5, ChronoUnit.MINUTES);

        Long id1 = accesosQr.create(new TemporaryQrAccess(
                null, "qr-op-1", pacienteId, "hash-qr-1", "pin-hash-1",
                true, false, true, false, 3, 0, false, 0, future, null, null));
        Long id2 = accesosQr.create(new TemporaryQrAccess(
                null, "qr-op-2", pacienteId, "hash-qr-2", null,
                true, true, true, true, 2, 0, false, 0, future, null, null));
        Long idExpired = accesosQr.create(new TemporaryQrAccess(
                null, "qr-op-exp", pacienteId, "hash-qr-exp", null,
                true, true, true, true, 3, 0, false, 0, past, null, null));

        TemporaryQrAccess byHash = accesosQr.findByTokenHash("hash-qr-1").orElseThrow();
        assertThat(byHash.id()).isEqualTo(id1);
        assertThat(byHash.pinHash()).isEqualTo("pin-hash-1");
        assertThat(byHash.includeAllergies()).isTrue();
        assertThat(byHash.includeMedications()).isFalse();
        assertThat(byHash.expiresAt()).isEqualTo(future);
        assertThat(accesosQr.findByTokenHash("no-hash")).isEmpty();

        assertThat(accesosQr.findByPublicId("qr-op-2")).map(TemporaryQrAccess::id).contains(id2);
        assertThat(accesosQr.findByPublicId("no-pub")).isEmpty();

        assertThat(accesosQr.listByPatientId(pacienteId))
                .extracting(TemporaryQrAccess::publicId)
                .containsExactly("qr-op-exp", "qr-op-2", "qr-op-1");

        // Lecturas atómicas: token expirado falla; token activo consume hasta maxAccesos.
        assertThat(accesosQr.recordAccess(idExpired, now)).isZero();
        assertThat(accesosQr.recordAccess(id2, now)).isEqualTo(1);
        assertThat(accesosQr.recordAccess(id2, now)).isEqualTo(1);
        assertThat(accesosQr.recordAccess(id2, now)).isZero(); // agotado (2/2)

        // Intentos fallidos de PIN: al llegar a 3 revoca automáticamente sin superar MAX_ACCESOS.
        assertThat(accesosQr.recordFailedPin(id1)).isEqualTo(1);
        assertThat(accesosQr.findByPublicId("qr-op-1").orElseThrow().revoked()).isFalse();
        assertThat(accesosQr.recordFailedPin(id1)).isEqualTo(1);
        assertThat(accesosQr.findByPublicId("qr-op-1").orElseThrow().revoked()).isFalse();
        assertThat(accesosQr.recordFailedPin(id1)).isEqualTo(1);

        TemporaryQrAccess lockedByPin = accesosQr.findByPublicId("qr-op-1").orElseThrow();
        assertThat(lockedByPin.failedPinAttempts()).isEqualTo(3);
        assertThat(lockedByPin.accessCount()).isEqualTo(3);
        assertThat(lockedByPin.revoked()).isTrue();
        assertThat(accesosQr.recordAccess(id1, now)).isZero();

        // Revocación manual valida dueño y evita doble revocación.
        assertThat(accesosQr.revoke("qr-op-2", -999L)).isZero();
        assertThat(accesosQr.revoke("qr-op-2", pacienteId)).isEqualTo(1);
        assertThat(accesosQr.revoke("qr-op-2", pacienteId)).isZero();
        assertThat(accesosQr.findByPublicId("qr-op-2").orElseThrow().revoked()).isTrue();
    }

    @Test
    void inpatientTrackingQrLifecycleAndLocationUpdate() {
        Instant expira = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        InpatientQrTracking q1 = seguimientosQr.save(new InpatientQrTracking(
                null, "inq-1", episodioId, "QROP-ONE", "URGENCIAS / TRIAGE", null, null, expira));
        assertThat(q1.id()).isNotNull();
        assertThat(q1.status()).isEqualTo("ACTIVO");
        assertThat(q1.createdAt()).isNotNull();

        assertThat(seguimientosQr.findById(q1.id())).contains(q1);
        assertThat(seguimientosQr.findById(null)).isEmpty();
        assertThat(seguimientosQr.findByToken("QROP-ONE")).contains(q1);
        assertThat(seguimientosQr.findByToken(" ")).isEmpty();
        assertThat(seguimientosQr.findByToken("QROP-MISSING")).isEmpty();

        // Un segundo QR inactivo no debe ocultar al activo; un segundo QR activo sí prevalece.
        seguimientosQr.save(new InpatientQrTracking(
                null, "inq-inactivo", episodioId, "QROP-INACT", "OTRA", "INACTIVO", null, expira));
        assertThat(seguimientosQr.findActiveByEpisodeId(episodioId))
                .map(InpatientQrTracking::publicId).contains("inq-1");

        InpatientQrTracking q2 = seguimientosQr.save(new InpatientQrTracking(
                null, "inq-2", episodioId, "QROP-TWO", "OBSERVACION / CAMA 4", "ACTIVO", null, expira));
        assertThat(seguimientosQr.findActiveByEpisodeId(episodioId))
                .map(InpatientQrTracking::publicId).contains("inq-2");
        assertThat(seguimientosQr.findActiveByEpisodeId(-1L)).isEmpty();

        seguimientosQr.updateLocation(q2.id(), "HOSPITALIZACION / PISO 3");
        assertThat(seguimientosQr.findById(q2.id()))
                .map(InpatientQrTracking::currentLocationText)
                .contains("HOSPITALIZACION / PISO 3");
    }

    @Test
    void demoSeedHelpersCountDomainsAndShiftSlotToPast() {
        // Sembrado en seed(): ack@demo.meditriaje.test (en dominio) y pat@real.org (fuera de dominio).
        assertThat(demoSeed.countUsersInDomain("@demo.meditriaje.test")).isEqualTo(1);
        assertThat(demoSeed.countUsersOutsideDomain("@demo.meditriaje.test")).isEqualTo(1);

        owner.update("INSERT INTO USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH) VALUES ('op-u-doc', 'doc@demo.meditriaje.test', 'x')");
        Long uDocId = owner.queryForObject("SELECT ID FROM USUARIO WHERE PUBLIC_ID = 'op-u-doc'", Long.class);
        owner.update("INSERT INTO ESPECIALIDAD (PUBLIC_ID, NOMBRE, DURACION_SLOT_MIN) VALUES ('op-esp', 'Esp Demo Op', 20)");
        Long espId = owner.queryForObject("SELECT ID FROM ESPECIALIDAD WHERE PUBLIC_ID = 'op-esp'", Long.class);
        owner.update("""
                INSERT INTO PROFESIONAL (USUARIO_ID, PUBLIC_ID, ESPECIALIDAD_ID, REGISTRO_MEDICO, NOMBRES, APELLIDOS,
                                         NUMERO_DOCUMENTO)
                VALUES (?, 'op-prof', ?, 'RM-OP-1', 'Mateo', 'Demo', '7702')""", uDocId, espId);
        Long profId = owner.queryForObject("SELECT ID FROM PROFESIONAL WHERE PUBLIC_ID = 'op-prof'", Long.class);

        Instant futureStart = Instant.now().plus(2, ChronoUnit.DAYS);
        owner.update("""
                INSERT INTO DISPONIBILIDAD_SLOT (PUBLIC_ID, PROFESIONAL_ID, ESPECIALIDAD_ID,
                                                 FECHA_HORA_INICIO, FECHA_HORA_FIN, MODALIDAD, ESTADO)
                VALUES ('op-slot-demo', ?, ?, ?, ?, 'PRESENCIAL', 'LIBRE')""",
                profId, espId, Timestamp.from(futureStart), Timestamp.from(futureStart.plus(20, ChronoUnit.MINUTES)));

        demoSeed.moveSlotToPastForDemo("op-slot-demo");

        assertThat(demoSeed.countUsersInDomain("@demo.meditriaje.test")).isEqualTo(2);

        Instant shiftedStart = owner.queryForObject(
                "SELECT FECHA_HORA_INICIO FROM DISPONIBILIDAD_SLOT WHERE PUBLIC_ID = 'op-slot-demo'", Timestamp.class)
                .toInstant();
        Instant shiftedEnd = owner.queryForObject(
                "SELECT FECHA_HORA_FIN FROM DISPONIBILIDAD_SLOT WHERE PUBLIC_ID = 'op-slot-demo'", Timestamp.class)
                .toInstant();
        assertThat(shiftedStart).isBefore(Instant.now());
        assertThat(shiftedEnd).isAfter(shiftedStart).isBefore(Instant.now());
    }
}
