package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.audit.AuditRecordResponse;
import com.meditriaje.dto.clinical.BreakGlassAccessResponse;
import com.meditriaje.model.BreakGlassAccess;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.AuditResult;
import com.meditriaje.repository.plsql.OracleErrorTranslator;
import com.meditriaje.repository.plsql.PackageGateway;
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

/** PKG_AUDIT contra Oracle Free real, llamado como MEDITRIAJE_APP: bitácora de auditoría y accesos Break-Glass. */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class PkgAuditIT {

    private static final String OWNER = "MT_PKG_AUDIT";
    private static final String MOTIVO = "Paciente inconsciente sin acompañante en urgencias";

    private static OracleContainer oracle;
    private static JdbcTemplate owner;
    private static AuditRepository auditoria;
    private static BreakGlassRepository breakGlass;
    private static Long auditorId;
    private static Long profesionalId;
    private static Long pacienteId;

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
        auditoria = new AuditRepository(gateway);
        breakGlass = new BreakGlassRepository(gateway);
        seed();
    }

    @AfterAll
    static void stop() {
        if (oracle != null) {
            oracle.stop();
        }
    }

    private static void seed() {
        owner.update("INSERT INTO USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH) VALUES ('au-user', 'auditor@audit.test', 'x')");
        owner.update("INSERT INTO USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH) VALUES ('au-prof', 'prof@audit.test', 'x')");
        owner.update("INSERT INTO USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH) VALUES ('au-pat', 'pat@audit.test', 'x')");
        owner.update("INSERT INTO ESPECIALIDAD (PUBLIC_ID, NOMBRE) VALUES ('au-spec', 'Especialidad Auditoria')");
        owner.update("""
                INSERT INTO PROFESIONAL (USUARIO_ID, PUBLIC_ID, ESPECIALIDAD_ID, REGISTRO_MEDICO, NOMBRES, APELLIDOS,
                                         NUMERO_DOCUMENTO)
                VALUES ((SELECT ID FROM USUARIO WHERE PUBLIC_ID = 'au-prof'), 'au-prof-1',
                        (SELECT ID FROM ESPECIALIDAD WHERE PUBLIC_ID = 'au-spec'), 'RM-AU-1', 'Pedro', 'Medico', '800')""");
        owner.update("""
                INSERT INTO PACIENTE (USUARIO_ID, PUBLIC_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO, NOMBRES, APELLIDOS,
                                      FECHA_NACIMIENTO)
                VALUES ((SELECT ID FROM USUARIO WHERE PUBLIC_ID = 'au-pat'), 'au-pat-1', 'CC', '700', 'Ana', 'Prueba',
                        DATE '1990-05-05')""");
        auditorId = owner.queryForObject("SELECT ID FROM USUARIO WHERE PUBLIC_ID = 'au-user'", Long.class);
        profesionalId = owner.queryForObject("SELECT ID FROM PROFESIONAL WHERE PUBLIC_ID = 'au-prof-1'", Long.class);
        pacienteId = owner.queryForObject("SELECT ID FROM PACIENTE WHERE PUBLIC_ID = 'au-pat-1'", Long.class);
    }

    @Test
    void auditEventsAreInsertedFilteredPagedAndCounted() {
        AuditableAction accion = AuditableAction.values()[0];
        auditoria.record(new AuditEvent(auditorId, accion, "USUARIO", "r-1", AuditResult.EXITO, "10.0.0.1"));
        auditoria.record(new AuditEvent(null, accion, "USUARIO", "r-2", AuditResult.FALLO, "10.0.0.2"));
        auditoria.record(new AuditEvent(auditorId, accion, "USUARIO", "r-3", AuditResult.EXITO, "10.0.0.3"));

        List<AuditRecordResponse> all = auditoria.listEvents(null, null, accion.name(), null, 0, 50);
        assertThat(all).extracting(AuditRecordResponse::resourcePublicId).containsExactly("r-3", "r-2", "r-1");
        assertThat(all.get(1).userEmail()).isEqualTo("Sistema / Anónimo");
        assertThat(all.get(0).userEmail()).isEqualTo("auditor@audit.test");

        assertThat(auditoria.listEvents(null, null, accion.name().toLowerCase(), "exito", 0, 1))
                .extracting(AuditRecordResponse::resourcePublicId).containsExactly("r-3");
        assertThat(auditoria.listEvents(null, null, accion.name(), "EXITO", 1, 1))
                .extracting(AuditRecordResponse::resourcePublicId).containsExactly("r-1");
        assertThat(auditoria.countEvents(null, null, accion.name(), null)).isEqualTo(3L);
        assertThat(auditoria.countEvents(null, null, accion.name(), "FALLO")).isEqualTo(1L);
        Instant future = Instant.now().plus(1, ChronoUnit.DAYS);
        assertThat(auditoria.countEvents(future, null, null, null)).isZero();
        assertThat(auditoria.listEvents(null, future, accion.name(), null, 0, 10)).hasSize(3);
    }

    @Test
    void breakGlassAccessesAreRegisteredAndQueried() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant expires = now.plus(1, ChronoUnit.HOURS);
        Long id = breakGlass.recordAccess(new BreakGlassAccess(null, "bg-1", profesionalId, pacienteId, MOTIVO, expires, now));
        breakGlass.recordAccess(new BreakGlassAccess(
                null, "bg-old", profesionalId, pacienteId, MOTIVO, now.minus(1, ChronoUnit.HOURS), now.minus(2, ChronoUnit.HOURS)));

        assertThat(breakGlass.existsActiveAccess(profesionalId, pacienteId, now)).isTrue();
        assertThat(breakGlass.existsActiveAccess(profesionalId, pacienteId, expires.plusSeconds(1))).isFalse();

        BreakGlassAccess entity = breakGlass.findEntityByPublicId("bg-1").orElseThrow();
        assertThat(entity.id()).isEqualTo(id);
        assertThat(entity.expirationDate()).isEqualTo(expires);
        assertThat(entity.createdAt()).isEqualTo(now);
        assertThat(breakGlass.findEntityByPublicId("no-existe")).isEmpty();

        BreakGlassAccessResponse active = breakGlass.findByPublicId("bg-1", now).orElseThrow();
        assertThat(active.active()).isTrue();
        assertThat(active.professionalName()).isEqualTo("Pedro Medico");
        assertThat(active.patientName()).isEqualTo("Ana Prueba");
        assertThat(breakGlass.findByPublicId("bg-1", null).orElseThrow().active()).isFalse();
        assertThat(breakGlass.findByPublicId("bg-old", now).orElseThrow().active()).isFalse();
        assertThat(breakGlass.findByPublicId("no-existe", now)).isEmpty();

        List<BreakGlassAccessResponse> activos = breakGlass.listActiveByProfessional(profesionalId, now);
        assertThat(activos).extracting(BreakGlassAccessResponse::publicId).containsExactly("bg-1");
        assertThat(activos.get(0).active()).isTrue();
    }
}
