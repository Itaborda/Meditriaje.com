package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.clinical.AtencionResponse;
import com.meditriaje.dto.clinical.EnmiendaResponse;
import com.meditriaje.dto.clinical.SignosVitalesDto;
import com.meditriaje.dto.followup.SeguimientoResponse;
import com.meditriaje.exception.ConflictoOperacionException;
import com.meditriaje.model.Atencion;
import com.meditriaje.model.SeguimientoPostAtencion;
import com.meditriaje.model.SignoVital;
import com.meditriaje.repository.plsql.OracleErrorTranslator;
import com.meditriaje.repository.plsql.PackageGateway;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

/** PKG_CLINICAL contra Oracle Free real, llamado como el usuario de aplicación (MEDITRIAJE_APP). */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class PkgClinicalIT {

    private static final String OWNER = "MT_PKG_CLINICAL";

    private static OracleContainer oracle;
    private static JdbcTemplate owner;
    private static AtencionRepository atenciones;
    private static SeguimientoRepository seguimientos;

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
        atenciones = new AtencionRepository(gateway);
        seguimientos = new SeguimientoRepository(gateway);
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

    private static Long newPatient(String docSuffix) {
        Long userId = newUser("pac");
        String publicId = UUID.randomUUID().toString();
        owner.update(
                "INSERT INTO PACIENTE (USUARIO_ID, PUBLIC_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO, NOMBRES, APELLIDOS, FECHA_NACIMIENTO) "
                        + "VALUES (?, ?, 'CC', ?, 'Carlos', 'Ruiz', DATE '1991-04-12')",
                userId, publicId, "800" + docSuffix);
        return owner.queryForObject("SELECT ID FROM PACIENTE WHERE PUBLIC_ID = ?", Long.class, publicId);
    }

    private static Long newProfessional(String docSuffix) {
        Long userId = newUser("prof");
        String publicId = UUID.randomUUID().toString();
        Long espId = owner.queryForObject("SELECT MIN(ID) FROM ESPECIALIDAD", Long.class);
        owner.update(
                "INSERT INTO PROFESIONAL (USUARIO_ID, PUBLIC_ID, ESPECIALIDAD_ID, REGISTRO_MEDICO, NOMBRES, APELLIDOS, TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TELEFONO) "
                        + "VALUES (?, ?, ?, ?, 'Laura', 'Gomez', 'CC', ?, '3001112233')",
                userId, publicId, espId, "RM-" + docSuffix, "700" + docSuffix);
        return owner.queryForObject("SELECT ID FROM PROFESIONAL WHERE PUBLIC_ID = ?", Long.class, publicId);
    }

    private static Long newAppointment(Long patientId, Long professionalId, int hourOffset) {
        Long espId = owner.queryForObject("SELECT ESPECIALIDAD_ID FROM PROFESIONAL WHERE ID = ?", Long.class, professionalId);
        String slotPublicId = UUID.randomUUID().toString();
        Instant start = Instant.now().plus(hourOffset, ChronoUnit.HOURS);
        Instant end = start.plus(20, ChronoUnit.MINUTES);
        owner.update(
                "INSERT INTO DISPONIBILIDAD_SLOT (PUBLIC_ID, PROFESIONAL_ID, ESPECIALIDAD_ID, FECHA_HORA_INICIO, FECHA_HORA_FIN) "
                        + "VALUES (?, ?, ?, ?, ?)",
                slotPublicId, professionalId, espId, Timestamp.from(start), Timestamp.from(end));
        Long slotId = owner.queryForObject("SELECT ID FROM DISPONIBILIDAD_SLOT WHERE PUBLIC_ID = ?", Long.class, slotPublicId);

        String citaPublicId = UUID.randomUUID().toString();
        owner.update(
                "INSERT INTO CITA (PUBLIC_ID, SLOT_ID, PACIENTE_ID, ESTADO) VALUES (?, ?, ?, 'ATENDIDA')",
                citaPublicId, slotId, patientId);
        return owner.queryForObject("SELECT ID FROM CITA WHERE PUBLIC_ID = ?", Long.class, citaPublicId);
    }

    @Test
    void encounterVitalSignsCloseAmendmentAndTriggersRoundTrip() {
        Long patientId = newPatient("101");
        Long professionalId = newProfessional("101");
        Long appointmentId = newAppointment(patientId, professionalId, 1);
        Long diagnosisId = owner.queryForObject(
                "SELECT ID FROM DIAGNOSTICO_CIE10 WHERE CODIGO = 'J00'", Long.class);

        String encounterPub = UUID.randomUUID().toString();
        Long encounterId = atenciones.crear(new Atencion(null, encounterPub, appointmentId, patientId, professionalId, "ABIERTA"));

        assertThat(encounterId).isPositive();
        assertThat(atenciones.existePorCitaId(appointmentId)).isTrue();
        assertThat(atenciones.existePorCitaId(-1L)).isFalse();

        Atencion byPub = atenciones.buscarEntidadPorPublicId(encounterPub).orElseThrow();
        assertThat(byPub.id()).isEqualTo(encounterId);
        assertThat(byPub.diagnosticoPrincipalId()).isNull();
        assertThat(atenciones.buscarEntidadPorId(encounterId)).contains(byPub);
        assertThat(atenciones.buscarEntidadPorCitaId(appointmentId)).contains(byPub);
        assertThat(atenciones.buscarEntidadPorPublicId("no-existe")).isEmpty();

        // Duplicar cita en ATENCION dispara ORA-00001 -> ConflictoOperacionException
        assertThatThrownBy(() -> atenciones.crear(
                        new Atencion(null, UUID.randomUUID().toString(), appointmentId, patientId, professionalId, "ABIERTA")))
                .isInstanceOf(ConflictoOperacionException.class);

        // Enmienda sobre atención ABIERTA dispara trigger -20005 -> ConflictoOperacionException
        assertThatThrownBy(() -> atenciones.crearEnmienda(
                        encounterId, professionalId, "Error", "No debe permitirse", Instant.now()))
                .isInstanceOf(ConflictoOperacionException.class);

        // Signos vitales con campos nulos y no nulos
        atenciones.guardarSignosVitales(new SignoVital(
                encounterId, 120, 80, 74, null, new BigDecimal("36.8"), 98, new BigDecimal("72.50"), null));
        SignosVitalesDto sv = atenciones.buscarSignosVitalesPorAtencionId(encounterId).orElseThrow();
        assertThat(sv.presionSistolica()).isEqualTo(120);
        assertThat(sv.frecuenciaRespiratoria()).isNull();
        assertThat(sv.temperatura()).isEqualByComparingTo("36.8");
        assertThat(sv.tallaCm()).isNull();

        // Cerrar atención
        Instant closedAt = Instant.now();
        atenciones.cerrarAtencion(encounterId, diagnosisId, "Rinorrea", "Paciente estable", "Hidratacion", closedAt);

        // Re-cerrar falla con IllegalStateException (o_affected = 0)
        assertThatThrownBy(() -> atenciones.cerrarAtencion(
                        encounterId, diagnosisId, "x", "y", "z", Instant.now()))
                .isInstanceOf(IllegalStateException.class);

        // Insertar signos vitales en atención CERRADA dispara trigger -20003 -> ConflictoOperacionException
        assertThatThrownBy(() -> atenciones.guardarSignosVitales(
                        new SignoVital(encounterId, 118, 78, 70, 16, new BigDecimal("36.5"), 99, null, null)))
                .isInstanceOf(ConflictoOperacionException.class);

        // Enmiendas sobre atención CERRADA en orden cronológico
        Instant t1 = closedAt.plusSeconds(10);
        Instant t2 = closedAt.plusSeconds(20);
        Long enmienda1 = atenciones.crearEnmienda(encounterId, professionalId, "Aclaracion", "Sin fiebre", t1);
        Long enmienda2 = atenciones.crearEnmienda(encounterId, professionalId, "Complemento", "Control en 48h", t2);
        assertThat(enmienda1).isPositive();
        assertThat(enmienda2).isGreaterThan(enmienda1);

        List<EnmiendaResponse> enmiendas = atenciones.buscarEnmiendasPorAtencionId(encounterId);
        assertThat(enmiendas).extracting(EnmiendaResponse::motivo).containsExactly("Aclaracion", "Complemento");

        AtencionResponse detail = atenciones.buscarDetallePorPublicId(encounterPub).orElseThrow();
        assertThat(detail.estado()).isEqualTo("CERRADA");
        assertThat(detail.diagnosticoCodigo()).isEqualTo("J00");
        assertThat(detail.signosVitales()).isNotNull();
        assertThat(detail.enmiendas()).hasSize(2);
    }

    @Test
    void encounterWindowAndPaginatedHistoryWork() {
        Long patientId = newPatient("202");
        Long professionalId = newProfessional("202");

        Long cita1 = newAppointment(patientId, professionalId, 2);
        Long cita2 = newAppointment(patientId, professionalId, 3);
        Long cita3 = newAppointment(patientId, professionalId, 4);

        String pub1 = UUID.randomUUID().toString();
        String pub2 = UUID.randomUUID().toString();
        String pub3 = UUID.randomUUID().toString();
        atenciones.crear(new Atencion(null, pub1, cita1, patientId, professionalId, "ABIERTA"));
        atenciones.crear(new Atencion(null, pub2, cita2, patientId, professionalId, "ABIERTA"));
        atenciones.crear(new Atencion(null, pub3, cita3, patientId, professionalId, "ABIERTA"));

        assertThat(atenciones.existeAtencionPreviaEnVentana(
                professionalId, patientId, Instant.now().minus(1, ChronoUnit.DAYS))).isTrue();
        assertThat(atenciones.existeAtencionPreviaEnVentana(
                professionalId, patientId, Instant.now().plus(1, ChronoUnit.DAYS))).isFalse();

        assertThat(atenciones.contarHistoriaPaciente(patientId)).isEqualTo(3);

        List<AtencionResponse> page0 = atenciones.listarHistoriaPaciente(patientId, 0, 2);
        List<AtencionResponse> page1 = atenciones.listarHistoriaPaciente(patientId, 1, 2);
        assertThat(page0).extracting(AtencionResponse::publicId).containsExactly(pub3, pub2);
        assertThat(page1).extracting(AtencionResponse::publicId).containsExactly(pub1);
    }

    @Test
    void followUpRoutinesRoundTrip() {
        Long patientId = newPatient("303");
        Long professionalId = newProfessional("303");
        Long citaId = newAppointment(patientId, professionalId, 5);
        Long encounterId = atenciones.crear(
                new Atencion(null, UUID.randomUUID().toString(), citaId, patientId, professionalId, "ABIERTA"));

        Instant now = Instant.now();
        String pub1 = UUID.randomUUID().toString();
        String pub2 = UUID.randomUUID().toString();
        String pub3 = UUID.randomUUID().toString();

        Long id1 = seguimientos.guardar(new SeguimientoPostAtencion(
                null, pub1, encounterId, patientId, professionalId,
                "CONTROL_MEDICO", "Control en 7 dias", LocalDate.now().plusDays(7),
                null, null, null, now.minusSeconds(30), now.minusSeconds(30)));
        Long id2 = seguimientos.guardar(new SeguimientoPostAtencion(
                null, pub2, encounterId, patientId, professionalId,
                "EVOLUCION_SINTOMAS", "Reportar fiebre", null,
                "PENDIENTE", null, null, now.minusSeconds(20), now.minusSeconds(20)));
        Long id3 = seguimientos.guardar(new SeguimientoPostAtencion(
                null, pub3, encounterId, patientId, professionalId,
                "ADHERENCIA_TRATAMIENTO", "Tomar dosis completa", null,
                "PENDIENTE", null, null, now.minusSeconds(10), now.minusSeconds(10)));

        assertThat(id1).isPositive();
        SeguimientoPostAtencion loaded1 = seguimientos.buscarEntidadPorId(id1).orElseThrow();
        assertThat(loaded1.estado()).isEqualTo("PENDIENTE");
        assertThat(seguimientos.buscarEntidadPorPublicId(pub1)).contains(loaded1);
        assertThat(seguimientos.buscarEntidadPorPublicId("no-existe")).isEmpty();

        SeguimientoResponse view2 = seguimientos.buscarPorPublicId(pub2).orElseThrow();
        assertThat(view2.fechaSugeridaControl()).isNull();
        assertThat(view2.pacienteNombre()).isEqualTo("Carlos Ruiz");

        // Reporte paciente -> COMPLETADO; actualizarEstado -> CANCELADO
        Instant answeredAt = Instant.now();
        seguimientos.registrarReportePaciente(id1, "Sin sintomas", answeredAt);
        SeguimientoPostAtencion completed1 = seguimientos.buscarEntidadPorId(id1).orElseThrow();
        assertThat(completed1.estado()).isEqualTo("COMPLETADO");
        assertThat(completed1.reportePaciente()).isEqualTo("Sin sintomas");
        assertThat(completed1.fechaRespuestaPaciente()).isNotNull();

        seguimientos.actualizarEstado(id3, "CANCELADO");
        assertThat(seguimientos.buscarEntidadPorId(id3).orElseThrow().estado()).isEqualTo("CANCELADO");

        // Listado por atención (orden de creación ascendente)
        assertThat(seguimientos.listarPorAtencionId(encounterId))
                .extracting(SeguimientoResponse::publicId)
                .containsExactly(pub1, pub2, pub3);

        // Conteo y paginación por paciente con y sin filtro de estado
        assertThat(seguimientos.contarPorPacienteId(patientId, null)).isEqualTo(3);
        assertThat(seguimientos.contarPorPacienteId(patientId, " pendiente ")).isEqualTo(1);
        assertThat(seguimientos.contarPorPacienteId(patientId, "COMPLETADO")).isEqualTo(1);

        List<SeguimientoResponse> allPage0 = seguimientos.listarPorPacienteId(patientId, " ", 0, 2);
        List<SeguimientoResponse> allPage1 = seguimientos.listarPorPacienteId(patientId, null, 1, 2);
        assertThat(allPage0).extracting(SeguimientoResponse::publicId).containsExactly(pub3, pub2);
        assertThat(allPage1).extracting(SeguimientoResponse::publicId).containsExactly(pub1);

        List<SeguimientoResponse> pendingOnly = seguimientos.listarPorPacienteId(patientId, "pendiente", 0, 10);
        assertThat(pendingOnly).extracting(SeguimientoResponse::publicId).containsExactly(pub2);
    }
}
