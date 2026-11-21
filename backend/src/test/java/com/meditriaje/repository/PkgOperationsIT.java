package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.operational.AlertaOperativaResponse;
import com.meditriaje.exception.ConflictoOperacionException;
import com.meditriaje.model.AccesoTemporalQr;
import com.meditriaje.model.AlertaOperativa;
import com.meditriaje.model.SeguimientoIntrahospitalarioQr;
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
    private static AlertaOperativaRepository alertas;
    private static AccesoTemporalQrRepository accesosQr;
    private static SeguimientoQrRepository seguimientosQr;
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
        alertas = new AlertaOperativaRepository(gateway);
        accesosQr = new AccesoTemporalQrRepository(gateway);
        seguimientosQr = new SeguimientoQrRepository(gateway);
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
        AlertaOperativa creada = alertas.guardar(new AlertaOperativa(
                null, "op-al-1", 9999L, "SATURACION_CAMAS", "CRITICA", "Ocupacion al 92%",
                null, null, null, null, null));
        assertThat(creada.id()).isNotNull();
        assertThat(creada.sedeId()).isEqualTo(sedeId);
        assertThat(creada.estado()).isEqualTo("ACTIVA");
        assertThat(creada.creadoAt()).isNotNull();

        assertThat(alertas.buscarPorId(creada.id())).contains(creada);
        assertThat(alertas.buscarPorId(null)).isEmpty();
        assertThat(alertas.buscarPorPublicId("op-al-1")).contains(creada);
        assertThat(alertas.buscarPorPublicId(" ")).isEmpty();
        assertThat(alertas.buscarPorPublicId("no-existe")).isEmpty();

        // El índice UQ_ALERTA_OPERATIVA_ACTIVA dispara ORA-00001 -> ConflictoOperacionException.
        assertThatThrownBy(() -> alertas.guardar(new AlertaOperativa(
                null, "op-al-dup", sedeId, "SATURACION_CAMAS", "CRITICA", "Duplicada",
                "ACTIVA", null, null, null, null)))
                .isInstanceOf(ConflictoOperacionException.class);

        Instant ackTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        alertas.reconocerAlerta(creada.id(), userAckId, ackTime, "Se habilitan camillas de expansion");

        AlertaOperativa reconocida = alertas.buscarPorPublicId("op-al-1").orElseThrow();
        assertThat(reconocida.estado()).isEqualTo("RECONOCIDA");
        assertThat(reconocida.reconocidoPorUsuarioId()).isEqualTo(userAckId);
        assertThat(reconocida.reconocidoAt()).isEqualTo(ackTime);
        assertThat(reconocida.motivoReconocimiento()).isEqualTo("Se habilitan camillas de expansion");

        // Tras reconocer la primera, ya puede existir una nueva alerta ACTIVA del mismo tipo.
        alertas.guardar(new AlertaOperativa(
                null, "op-al-2", sedeId, "SATURACION_CAMAS", "ALTA", "Nueva alerta",
                "ACTIVA", null, null, null, null));

        List<AlertaOperativaResponse> activas = alertas.listarPorSede(sedeId, "ACTIVA");
        assertThat(activas).extracting(AlertaOperativaResponse::publicId).containsExactly("op-al-2");
        assertThat(activas.get(0).reconocidoPorEmail()).isNull();

        List<AlertaOperativaResponse> todas = alertas.listarPorSede(null, " ");
        assertThat(todas).extracting(AlertaOperativaResponse::publicId).containsExactly("op-al-2", "op-al-1");
        assertThat(todas.get(1).reconocidoPorEmail()).isEqualTo("ack@demo.meditriaje.test");
    }

    @Test
    void temporaryQrAccessCreationAtomicReadsPinFailuresAndRevocation() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant future = now.plus(15, ChronoUnit.MINUTES);
        Instant past = now.minus(5, ChronoUnit.MINUTES);

        Long id1 = accesosQr.crear(new AccesoTemporalQr(
                null, "qr-op-1", pacienteId, "hash-qr-1", "pin-hash-1",
                true, false, true, false, 3, 0, false, 0, future, null, null));
        Long id2 = accesosQr.crear(new AccesoTemporalQr(
                null, "qr-op-2", pacienteId, "hash-qr-2", null,
                true, true, true, true, 2, 0, false, 0, future, null, null));
        Long idExpired = accesosQr.crear(new AccesoTemporalQr(
                null, "qr-op-exp", pacienteId, "hash-qr-exp", null,
                true, true, true, true, 3, 0, false, 0, past, null, null));

        AccesoTemporalQr byHash = accesosQr.buscarPorTokenHash("hash-qr-1").orElseThrow();
        assertThat(byHash.id()).isEqualTo(id1);
        assertThat(byHash.pinHash()).isEqualTo("pin-hash-1");
        assertThat(byHash.incluirAlergias()).isTrue();
        assertThat(byHash.incluirMedicamentos()).isFalse();
        assertThat(byHash.expiraAt()).isEqualTo(future);
        assertThat(accesosQr.buscarPorTokenHash("no-hash")).isEmpty();

        assertThat(accesosQr.buscarPorPublicId("qr-op-2")).map(AccesoTemporalQr::id).contains(id2);
        assertThat(accesosQr.buscarPorPublicId("no-pub")).isEmpty();

        assertThat(accesosQr.listarPorPacienteId(pacienteId))
                .extracting(AccesoTemporalQr::publicId)
                .containsExactly("qr-op-exp", "qr-op-2", "qr-op-1");

        // Lecturas atómicas: token expirado falla; token activo consume hasta maxAccesos.
        assertThat(accesosQr.registrarAcceso(idExpired, now)).isZero();
        assertThat(accesosQr.registrarAcceso(id2, now)).isEqualTo(1);
        assertThat(accesosQr.registrarAcceso(id2, now)).isEqualTo(1);
        assertThat(accesosQr.registrarAcceso(id2, now)).isZero(); // agotado (2/2)

        // Intentos fallidos de PIN: al llegar a 3 revoca automáticamente sin superar MAX_ACCESOS.
        assertThat(accesosQr.registrarPinFallido(id1)).isEqualTo(1);
        assertThat(accesosQr.buscarPorPublicId("qr-op-1").orElseThrow().revocado()).isFalse();
        assertThat(accesosQr.registrarPinFallido(id1)).isEqualTo(1);
        assertThat(accesosQr.buscarPorPublicId("qr-op-1").orElseThrow().revocado()).isFalse();
        assertThat(accesosQr.registrarPinFallido(id1)).isEqualTo(1);

        AccesoTemporalQr lockedByPin = accesosQr.buscarPorPublicId("qr-op-1").orElseThrow();
        assertThat(lockedByPin.intentosPinFallidos()).isEqualTo(3);
        assertThat(lockedByPin.accesosRealizados()).isEqualTo(3);
        assertThat(lockedByPin.revocado()).isTrue();
        assertThat(accesosQr.registrarAcceso(id1, now)).isZero();

        // Revocación manual valida dueño y evita doble revocación.
        assertThat(accesosQr.revocar("qr-op-2", -999L)).isZero();
        assertThat(accesosQr.revocar("qr-op-2", pacienteId)).isEqualTo(1);
        assertThat(accesosQr.revocar("qr-op-2", pacienteId)).isZero();
        assertThat(accesosQr.buscarPorPublicId("qr-op-2").orElseThrow().revocado()).isTrue();
    }

    @Test
    void inpatientTrackingQrLifecycleAndLocationUpdate() {
        Instant expira = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        SeguimientoIntrahospitalarioQr q1 = seguimientosQr.guardar(new SeguimientoIntrahospitalarioQr(
                null, "inq-1", episodioId, "QROP-ONE", "URGENCIAS / TRIAGE", null, null, expira));
        assertThat(q1.id()).isNotNull();
        assertThat(q1.estado()).isEqualTo("ACTIVO");
        assertThat(q1.creadoAt()).isNotNull();

        assertThat(seguimientosQr.buscarPorId(q1.id())).contains(q1);
        assertThat(seguimientosQr.buscarPorId(null)).isEmpty();
        assertThat(seguimientosQr.buscarPorToken("QROP-ONE")).contains(q1);
        assertThat(seguimientosQr.buscarPorToken(" ")).isEmpty();
        assertThat(seguimientosQr.buscarPorToken("QROP-MISSING")).isEmpty();

        // Un segundo QR inactivo no debe ocultar al activo; un segundo QR activo sí prevalece.
        seguimientosQr.guardar(new SeguimientoIntrahospitalarioQr(
                null, "inq-inactivo", episodioId, "QROP-INACT", "OTRA", "INACTIVO", null, expira));
        assertThat(seguimientosQr.buscarActivoPorEpisodioId(episodioId))
                .map(SeguimientoIntrahospitalarioQr::publicId).contains("inq-1");

        SeguimientoIntrahospitalarioQr q2 = seguimientosQr.guardar(new SeguimientoIntrahospitalarioQr(
                null, "inq-2", episodioId, "QROP-TWO", "OBSERVACION / CAMA 4", "ACTIVO", null, expira));
        assertThat(seguimientosQr.buscarActivoPorEpisodioId(episodioId))
                .map(SeguimientoIntrahospitalarioQr::publicId).contains("inq-2");
        assertThat(seguimientosQr.buscarActivoPorEpisodioId(-1L)).isEmpty();

        seguimientosQr.actualizarUbicacion(q2.id(), "HOSPITALIZACION / PISO 3");
        assertThat(seguimientosQr.buscarPorId(q2.id()))
                .map(SeguimientoIntrahospitalarioQr::ubicacionActualTexto)
                .contains("HOSPITALIZACION / PISO 3");
    }

    @Test
    void demoSeedHelpersCountDomainsAndShiftSlotToPast() {
        // Sembrado en seed(): ack@demo.meditriaje.test (en dominio) y pat@real.org (fuera de dominio).
        assertThat(demoSeed.contarUsuariosEnDominio("@demo.meditriaje.test")).isEqualTo(1);
        assertThat(demoSeed.contarUsuariosFueraDeDominio("@demo.meditriaje.test")).isEqualTo(1);

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

        demoSeed.moverSlotAlPasadoParaDemo("op-slot-demo");

        assertThat(demoSeed.contarUsuariosEnDominio("@demo.meditriaje.test")).isEqualTo(2);

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
