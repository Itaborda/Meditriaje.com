package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.admin.AdminCitaDetalleResponse;
import com.meditriaje.dto.admin.SlotResponse;
import com.meditriaje.dto.affiliation.AusenciaResponse;
import com.meditriaje.dto.appointment.CitaResponse;
import com.meditriaje.dto.availability.DisponibilidadSlotResponse;
import com.meditriaje.exception.ConflictoOperacionException;
import com.meditriaje.model.AusenciaMedica;
import com.meditriaje.model.Cita;
import com.meditriaje.model.DisponibilidadSlot;
import com.meditriaje.model.RecordatorioCita;
import com.meditriaje.repository.plsql.OracleErrorTranslator;
import com.meditriaje.repository.plsql.PackageGateway;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
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
 * {@code PKG_SCHEDULING} contra Oracle Free real, llamado como el usuario de aplicación (MEDITRIAJE_APP) a través de
 * {@link CitaRepository}, {@link DisponibilidadSlotRepository}, {@link AusenciaMedicaRepository} y
 * {@link RecordatorioCitaRepository}.
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PkgSchedulingIT {

    private static final String OWNER = "MT_PKG_SCHEDULING";

    private static OracleContainer oracle;
    private static JdbcTemplate owner;
    private static DisponibilidadSlotRepository slots;
    private static CitaRepository appointments;
    private static AusenciaMedicaRepository absences;
    private static RecordatorioCitaRepository reminders;

    private static Long siteId;
    private static String sitePublicId;
    private static Long patientId;
    private static Long patient2Id;
    private static Long profId;
    private static Long specialtyId;
    private static Long staffUserId;
    private static Long triageId;
    private static String triagePublicId;

    private static Instant t0;
    private static Long slot1Id;
    private static String slot1PubId;
    private static Long slot2Id;
    private static Long slot3Id;
    private static Long cita1Id;
    private static String cita1PubId;

    @BeforeAll
    static void migrate() {
        oracle = OracleTestDatabase.newContainer().withStartupTimeout(Duration.ofMinutes(10));
        oracle.start();
        DataSource ownerDs = OracleTestDatabase.provisionOwner(oracle, OWNER);
        OracleTestDatabase.flyway(ownerDs).migrate();
        OracleTestDatabase.runScript(ownerDs, "db/pkg-clinical-fixture.sql");
        owner = new JdbcTemplate(ownerDs);
        DataSource appDs =
                OracleTestDatabase.dataSource(oracle, OracleTestDatabase.APP_USER, OracleTestDatabase.APP_PASSWORD);
        PackageGateway gateway = new PackageGateway(appDs, OWNER, new OracleErrorTranslator());
        slots = new DisponibilidadSlotRepository(gateway);
        appointments = new CitaRepository(gateway);
        absences = new AusenciaMedicaRepository(gateway);
        reminders = new RecordatorioCitaRepository(gateway);

        siteId = owner.queryForObject("SELECT MIN(ID) FROM SEDE", Long.class);
        sitePublicId = owner.queryForObject("SELECT PUBLIC_ID FROM SEDE WHERE ID = ?", String.class, siteId);
        patientId = owner.queryForObject("SELECT ID FROM PACIENTE WHERE PUBLIC_ID = 'fx-patient-1'", Long.class);
        patient2Id = owner.queryForObject("SELECT ID FROM PACIENTE WHERE PUBLIC_ID = 'fx-patient-2'", Long.class);
        profId = owner.queryForObject("SELECT ID FROM PROFESIONAL WHERE PUBLIC_ID = 'fx-prof-1'", Long.class);
        specialtyId = owner.queryForObject("SELECT ID FROM ESPECIALIDAD WHERE PUBLIC_ID = 'fx-spec-1'", Long.class);
        staffUserId = owner.queryForObject("SELECT ID FROM USUARIO WHERE PUBLIC_ID = 'fx-user-staff'", Long.class);

        triagePublicId = UUID.randomUUID().toString();
        owner.update("INSERT INTO TRIAJE (PUBLIC_ID, PACIENTE_ID, VERSION_REGLAS, NIVEL_PRIORIDAD, RUTA_SUGERIDA, "
                        + "ES_EMERGENCIA, OBSERVACIONES) VALUES (?, ?, 'v1-prototipo', 'II', 'ATENCION_PRIORITARIA', 0, ?)",
                triagePublicId, patientId, "Dolor agudo");
        triageId = owner.queryForObject("SELECT ID FROM TRIAJE WHERE PUBLIC_ID = ?", Long.class, triagePublicId);

        t0 = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
    }

    @AfterAll
    static void stop() {
        if (oracle != null) {
            oracle.stop();
        }
    }

    @Test
    @Order(1)
    void slotsLifecycleOverlapReserveReleaseAndDelete() {
        slot1PubId = uuid();
        slot1Id = slots.guardar(new DisponibilidadSlot(
                slot1PubId, profId, 999L, specialtyId, t0, t0.plusSeconds(1200), "PRESENCIAL", "LIBRE"));
        assertThat(slot1Id).isPositive();

        DisponibilidadSlot loaded = slots.buscarPorId(slot1Id).orElseThrow();
        assertThat(loaded.sedeId()).isEqualTo(siteId);
        assertThat(slots.buscarEntidadPorPublicId(slot1PubId)).contains(loaded);
        assertThat(slots.buscarPorPublicId(slot1PubId)).get()
                .extracting(SlotResponse::profesionalNombre).isEqualTo("Pedro Medico");
        assertThat(slots.buscarPorPublicId("no-existe")).isEmpty();

        String slot2PubId = uuid();
        String slot3PubId = uuid();
        String slotDeletePubId = uuid();
        slots.guardarLote(List.of(
                new DisponibilidadSlot(slot2PubId, profId, siteId, specialtyId,
                        t0.plusSeconds(1200), t0.plusSeconds(2400), "TELEMEDICINA", "LIBRE"),
                new DisponibilidadSlot(slot3PubId, profId, siteId, specialtyId,
                        t0.plusSeconds(2400), t0.plusSeconds(3600), "PRESENCIAL", "LIBRE"),
                new DisponibilidadSlot(slotDeletePubId, profId, siteId, specialtyId,
                        t0.plusSeconds(3600), t0.plusSeconds(4800), "PRESENCIAL", "LIBRE")));
        slot2Id = slots.buscarEntidadPorPublicId(slot2PubId).orElseThrow().id();
        slot3Id = slots.buscarEntidadPorPublicId(slot3PubId).orElseThrow().id();

        // Solape: [t0 + 600, t0 + 1800) cruza con slot1 y slot2; [t0 + 4800, t0 + 6000) es adyacente sin solape.
        assertThat(slots.existeSolape(profId, t0.plusSeconds(600), t0.plusSeconds(1800))).isTrue();
        assertThat(slots.existeSolape(profId, t0.plusSeconds(4800), t0.plusSeconds(6000))).isFalse();

        // Cambio de estado y eliminación condicional (solo LIBRE)
        slots.cambiarEstado(slotDeletePubId, "BLOQUEADO");
        assertThat(slots.eliminar(slotDeletePubId)).isZero();
        slots.cambiarEstado(slotDeletePubId, "LIBRE");
        assertThat(slots.eliminar(slotDeletePubId)).isEqualTo(1);

        // Listados y conteos (administración y disponibles)
        assertThat(slots.contar("fx-prof-1", sitePublicId, "fx-spec-1", t0, t0.plusSeconds(3600), " libre "))
                .isEqualTo(3);
        assertThat(slots.listar(0, 2, "fx-prof-1", null, null, null, null, "LIBRE")).hasSize(2);
        assertThat(slots.contarDisponibles("fx-spec-1", "fx-prof-1", sitePublicId, "telemedicina", t0, null))
                .isEqualTo(1);
        List<DisponibilidadSlotResponse> available =
                slots.consultarDisponibles("fx-spec-1", sitePublicId, "PRESENCIAL", t0, null, 0, 10);
        assertThat(available).hasSize(2);
        assertThat(available.get(0).duracionMinutos()).isEqualTo(20);

        // Reserva atómica y liberación
        assertThat(slots.reservarSlot(slot1Id)).isEqualTo(1);
        assertThat(slots.reservarSlot(slot1Id)).isZero();
        assertThat(slots.liberarSlot(slot1Id)).isEqualTo(1);
        assertThat(slots.liberarSlot(slot1Id)).isZero();
    }

    @Test
    @Order(2)
    void appointmentsLifecycleConcurrencyAndAdminUnpaginated() {
        assertThat(slots.reservarSlot(slot1Id)).isEqualTo(1);
        cita1PubId = uuid();
        cita1Id = appointments.crear(
                new Cita(cita1PubId, slot1Id, patientId, triageId, null, "PROGRAMADA", null));
        assertThat(cita1Id).isPositive();
        assertThat(appointments.existeCitaActivaEnSlot(slot1Id)).isTrue();
        assertThat(appointments.existeCitaActivaFutura(profId, patientId, Instant.now())).isTrue();

        // Segunda cita activa en el mismo slot: UQ_CITA_SLOT_ACTIVA lanza ORA-00001 -> ConflictoOperacionException
        assertThatThrownBy(() -> appointments.crear(
                        new Cita(uuid(), slot1Id, patient2Id, null, null, "PROGRAMADA", null)))
                .isInstanceOf(ConflictoOperacionException.class);

        Cita entity = appointments.buscarEntidadPorId(cita1Id).orElseThrow();
        assertThat(appointments.buscarEntidadPorPublicId(cita1PubId)).contains(entity);
        CitaResponse detail = appointments.buscarPorPublicId(cita1PubId).orElseThrow();
        assertThat(detail.triajePublicId()).isEqualTo(triagePublicId);
        assertThat(detail.triajeNivel()).isEqualTo("II");
        assertThat(detail.motivoConsulta()).isEqualTo("Dolor agudo");
        assertThat(detail.tieneAtencion()).isFalse();

        // Reprogramación a slot2 y tercera cita en slot3 para probar orden, paginación y sin paginación
        assertThat(slots.reservarSlot(slot2Id)).isEqualTo(1);
        String cita2PubId = uuid();
        Long cita2Id = appointments.crear(
                new Cita(cita2PubId, slot2Id, patientId, null, cita1Id, "PROGRAMADA", null));
        appointments.actualizarEstado(cita1Id, "REPROGRAMADA", "Cambio de horario");
        assertThat(appointments.buscarEntidadPorId(cita1Id).orElseThrow().motivoCancelacion())
                .isEqualTo("Cambio de horario");

        assertThat(slots.reservarSlot(slot3Id)).isEqualTo(1);
        String cita3PubId = uuid();
        appointments.crear(new Cita(cita3PubId, slot3Id, patient2Id, null, null, "CONFIRMADA", null));
        assertThat(appointments.buscarPorPublicId(cita2PubId).orElseThrow().citaOrigenPublicId())
                .isEqualTo(cita1PubId);

        // Agenda profesional y citas del paciente
        assertThat(appointments.contarAgendaProfesional(profId, t0, t0.plusSeconds(3600), null)).isEqualTo(3);
        assertThat(appointments.listarAgendaProfesional(profId, null, null, " programada ", 0, 10))
                .extracting(CitaResponse::publicId).containsExactly(cita2PubId);
        assertThat(appointments.contarPorPacienteId(patientId)).isEqualTo(2);
        assertThat(appointments.listarPorPacienteId(patientId, 0, 10))
                .extracting(CitaResponse::publicId).containsExactly(cita2PubId, cita1PubId);

        // Administración: paginado (size > 0) y sin paginar (size <= 0 -> p_limit IS NULL)
        assertThat(appointments.contarCitasAdmin(null, null, "fx-prof-1", "fx-spec-1", null)).isEqualTo(3);
        List<AdminCitaDetalleResponse> page0 =
                appointments.listarCitasAdmin(null, null, "fx-prof-1", "fx-spec-1", null, 0, 2);
        assertThat(page0).hasSize(2);
        List<AdminCitaDetalleResponse> unpaginated =
                appointments.listarCitasAdmin(null, null, "fx-prof-1", null, null, 0, 0);
        assertThat(unpaginated).extracting(AdminCitaDetalleResponse::citaPublicId)
                .containsExactly(cita1PubId, cita2PubId, cita3PubId);
        assertThat(unpaginated.get(1).motivoConsulta()).isEqualTo("Consulta médica general");

        // Actualizar sin motivo (null) verifica parámetro VARCHAR nullable
        appointments.actualizarEstado(cita2Id, "ATENDIDA", null);
        assertThat(appointments.buscarEntidadPorId(cita2Id).orElseThrow().motivoCancelacion()).isNull();
    }

    @Test
    @Order(3)
    void medicalAbsencesAndAppointmentReminders() {
        Instant absStart = t0.plus(1, ChronoUnit.DAYS);
        Instant absEnd = absStart.plus(4, ChronoUnit.HOURS);
        AusenciaMedica saved = absences.guardar(new AusenciaMedica(
                null, uuid(), profId, absStart, absEnd, "Congreso medico", null, staffUserId, null));
        assertThat(saved.id()).isNotNull();
        assertThat(saved.estado()).isEqualTo("ACTIVA");
        assertThat(absences.buscarPorId(null)).isEmpty();
        assertThat(absences.buscarPorId(saved.id())).contains(saved);

        assertThat(absences.existeTraslapeAusencia(profId, absStart.plusSeconds(60), absEnd.plusSeconds(60))).isTrue();
        assertThat(absences.existeTraslapeAusencia(profId, absEnd, absEnd.plusSeconds(3600))).isFalse();

        List<AusenciaResponse> list = absences.listarPorProfesionalId(profId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).profesionalNombre()).isEqualTo("Pedro Medico");

        Long rem1Id = reminders.guardar(new RecordatorioCita(
                null, uuid(), cita1Id, patientId, "CONFIRMACION_RESERVA", "EMAIL",
                "ana@pkg.test", "ENVIADO", null, null));
        Long rem2Id = reminders.guardar(new RecordatorioCita(
                null, uuid(), cita1Id, patientId, "RECORDATORIO_PREVIO", "EMAIL",
                "ana@pkg.test", "FALLIDO", "SMTP timeout", Instant.now()));
        assertThat(rem1Id).isPositive();
        assertThat(rem2Id).isGreaterThan(rem1Id);

        assertThat(reminders.contarPorCitaIdYTipo(cita1Id, "CONFIRMACION_RESERVA")).isEqualTo(1);
        assertThat(reminders.contarPorCitaIdYTipo(cita1Id, "CANCELACION")).isZero();
        List<RecordatorioCita> listRem = reminders.listarPorCitaId(cita1Id);
        assertThat(listRem).hasSize(2);
        assertThat(listRem.get(0).errorMensaje()).isNull();
        assertThat(listRem.get(1).errorMensaje()).isEqualTo("SMTP timeout");
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}
