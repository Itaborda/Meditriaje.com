package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.admin.AdminAppointmentDetailResponse;
import com.meditriaje.dto.admin.SlotResponse;
import com.meditriaje.dto.affiliation.AbsenceResponse;
import com.meditriaje.dto.appointment.AppointmentResponse;
import com.meditriaje.dto.availability.AvailabilitySlotResponse;
import com.meditriaje.exception.OperationConflictException;
import com.meditriaje.model.MedicalAbsence;
import com.meditriaje.model.Appointment;
import com.meditriaje.model.AvailabilitySlot;
import com.meditriaje.model.AppointmentReminder;
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
    private static AvailabilitySlotRepository slots;
    private static AppointmentRepository appointments;
    private static MedicalAbsenceRepository absences;
    private static AppointmentReminderRepository reminders;

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
        slots = new AvailabilitySlotRepository(gateway);
        appointments = new AppointmentRepository(gateway);
        absences = new MedicalAbsenceRepository(gateway);
        reminders = new AppointmentReminderRepository(gateway);

        siteId = owner.queryForObject("SELECT MIN(ID) FROM CONFIGURACION_HOSPITAL", Long.class);
        sitePublicId = owner.queryForObject("SELECT PUBLIC_ID FROM CONFIGURACION_HOSPITAL WHERE ID = ?", String.class, siteId);
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
        slot1Id = slots.save(new AvailabilitySlot(
                slot1PubId, profId, 999L, specialtyId, t0, t0.plusSeconds(1200), "PRESENCIAL", "LIBRE"));
        assertThat(slot1Id).isPositive();

        AvailabilitySlot loaded = slots.findById(slot1Id).orElseThrow();
        assertThat(loaded.siteId()).isEqualTo(siteId);
        assertThat(slots.findEntityByPublicId(slot1PubId)).contains(loaded);
        assertThat(slots.findByPublicId(slot1PubId)).get()
                .extracting(SlotResponse::professionalName).isEqualTo("Pedro Medico");
        assertThat(slots.findByPublicId("no-existe")).isEmpty();

        String slot2PubId = uuid();
        String slot3PubId = uuid();
        String slotDeletePubId = uuid();
        slots.saveBatch(List.of(
                new AvailabilitySlot(slot2PubId, profId, siteId, specialtyId,
                        t0.plusSeconds(1200), t0.plusSeconds(2400), "TELEMEDICINA", "LIBRE"),
                new AvailabilitySlot(slot3PubId, profId, siteId, specialtyId,
                        t0.plusSeconds(2400), t0.plusSeconds(3600), "PRESENCIAL", "LIBRE"),
                new AvailabilitySlot(slotDeletePubId, profId, siteId, specialtyId,
                        t0.plusSeconds(3600), t0.plusSeconds(4800), "PRESENCIAL", "LIBRE")));
        slot2Id = slots.findEntityByPublicId(slot2PubId).orElseThrow().id();
        slot3Id = slots.findEntityByPublicId(slot3PubId).orElseThrow().id();

        // Solape: [t0 + 600, t0 + 1800) cruza con slot1 y slot2; [t0 + 4800, t0 + 6000) es adyacente sin solape.
        assertThat(slots.existsOverlap(profId, t0.plusSeconds(600), t0.plusSeconds(1800))).isTrue();
        assertThat(slots.existsOverlap(profId, t0.plusSeconds(4800), t0.plusSeconds(6000))).isFalse();

        // Cambio de estado y eliminación condicional (solo LIBRE)
        slots.changeStatus(slotDeletePubId, "BLOQUEADO");
        assertThat(slots.delete(slotDeletePubId)).isZero();
        slots.changeStatus(slotDeletePubId, "LIBRE");
        assertThat(slots.delete(slotDeletePubId)).isEqualTo(1);

        // Listados y conteos (administración y disponibles)
        assertThat(slots.count("fx-prof-1", sitePublicId, "fx-spec-1", t0, t0.plusSeconds(3600), " libre "))
                .isEqualTo(3);
        assertThat(slots.list(0, 2, "fx-prof-1", null, null, null, null, "LIBRE")).hasSize(2);
        assertThat(slots.countAvailable("fx-spec-1", "fx-prof-1", sitePublicId, "telemedicina", t0, null))
                .isEqualTo(1);
        List<AvailabilitySlotResponse> available =
                slots.findAvailable("fx-spec-1", sitePublicId, "PRESENCIAL", t0, null, 0, 10);
        assertThat(available).hasSize(2);
        assertThat(available.get(0).durationMinutes()).isEqualTo(20);

        // Reserva atómica y liberación
        assertThat(slots.reserveSlot(slot1Id)).isEqualTo(1);
        assertThat(slots.reserveSlot(slot1Id)).isZero();
        assertThat(slots.releaseSlot(slot1Id)).isEqualTo(1);
        assertThat(slots.releaseSlot(slot1Id)).isZero();
    }

    @Test
    @Order(2)
    void appointmentsLifecycleConcurrencyAndAdminUnpaginated() {
        assertThat(slots.reserveSlot(slot1Id)).isEqualTo(1);
        cita1PubId = uuid();
        cita1Id = appointments.create(
                new Appointment(cita1PubId, slot1Id, patientId, triageId, null, "PROGRAMADA", null));
        assertThat(cita1Id).isPositive();
        assertThat(appointments.existsActiveAppointmentInSlot(slot1Id)).isTrue();
        assertThat(appointments.existsActiveFutureAppointment(profId, patientId, Instant.now())).isTrue();

        // Segunda cita activa en el mismo slot: UQ_CITA_SLOT_ACTIVA lanza ORA-00001 -> ConflictoOperacionException
        assertThatThrownBy(() -> appointments.create(
                        new Appointment(uuid(), slot1Id, patient2Id, null, null, "PROGRAMADA", null)))
                .isInstanceOf(OperationConflictException.class);

        Appointment entity = appointments.findEntityById(cita1Id).orElseThrow();
        assertThat(appointments.findEntityByPublicId(cita1PubId)).contains(entity);
        AppointmentResponse detail = appointments.findByPublicId(cita1PubId).orElseThrow();
        assertThat(detail.triagePublicId()).isEqualTo(triagePublicId);
        assertThat(detail.triageLevel()).isEqualTo("II");
        assertThat(detail.consultationReason()).isEqualTo("Dolor agudo");
        assertThat(detail.hasAttention()).isFalse();

        // Reprogramación a slot2 y tercera cita en slot3 para probar orden, paginación y sin paginación
        assertThat(slots.reserveSlot(slot2Id)).isEqualTo(1);
        String cita2PubId = uuid();
        Long cita2Id = appointments.create(
                new Appointment(cita2PubId, slot2Id, patientId, null, cita1Id, "PROGRAMADA", null));
        appointments.updateStatus(cita1Id, "REPROGRAMADA", "Cambio de horario");
        assertThat(appointments.findEntityById(cita1Id).orElseThrow().cancellationReason())
                .isEqualTo("Cambio de horario");

        assertThat(slots.reserveSlot(slot3Id)).isEqualTo(1);
        String cita3PubId = uuid();
        appointments.create(new Appointment(cita3PubId, slot3Id, patient2Id, null, null, "CONFIRMADA", null));
        assertThat(appointments.findByPublicId(cita2PubId).orElseThrow().originAppointmentPublicId())
                .isEqualTo(cita1PubId);

        // Agenda profesional y citas del paciente
        assertThat(appointments.countProfessionalAgenda(profId, t0, t0.plusSeconds(3600), null)).isEqualTo(3);
        assertThat(appointments.listProfessionalAgenda(profId, null, null, " programada ", 0, 10))
                .extracting(AppointmentResponse::publicId).containsExactly(cita2PubId);
        assertThat(appointments.countByPatientId(patientId)).isEqualTo(2);
        assertThat(appointments.listByPatientId(patientId, 0, 10))
                .extracting(AppointmentResponse::publicId).containsExactly(cita2PubId, cita1PubId);

        // Administración: paginado (size > 0) y sin paginar (size <= 0 -> p_limit IS NULL)
        assertThat(appointments.countAdminAppointments(null, null, "fx-prof-1", "fx-spec-1", null)).isEqualTo(3);
        List<AdminAppointmentDetailResponse> page0 =
                appointments.listAdminAppointments(null, null, "fx-prof-1", "fx-spec-1", null, 0, 2);
        assertThat(page0).hasSize(2);
        List<AdminAppointmentDetailResponse> unpaginated =
                appointments.listAdminAppointments(null, null, "fx-prof-1", null, null, 0, 0);
        assertThat(unpaginated).extracting(AdminAppointmentDetailResponse::appointmentPublicId)
                .containsExactly(cita1PubId, cita2PubId, cita3PubId);
        assertThat(unpaginated.get(1).consultationReason()).isEqualTo("Consulta médica general");

        // Actualizar sin motivo (null) verifica parámetro VARCHAR nullable
        appointments.updateStatus(cita2Id, "ATENDIDA", null);
        assertThat(appointments.findEntityById(cita2Id).orElseThrow().cancellationReason()).isNull();
    }

    @Test
    @Order(3)
    void medicalAbsencesAndAppointmentReminders() {
        Instant absStart = t0.plus(1, ChronoUnit.DAYS);
        Instant absEnd = absStart.plus(4, ChronoUnit.HOURS);
        MedicalAbsence saved = absences.save(new MedicalAbsence(
                null, uuid(), profId, absStart, absEnd, "Congreso medico", null, staffUserId, null));
        assertThat(saved.id()).isNotNull();
        assertThat(saved.status()).isEqualTo("ACTIVA");
        assertThat(absences.findById(null)).isEmpty();
        assertThat(absences.findById(saved.id())).contains(saved);

        assertThat(absences.existsAbsenceOverlap(profId, absStart.plusSeconds(60), absEnd.plusSeconds(60))).isTrue();
        assertThat(absences.existsAbsenceOverlap(profId, absEnd, absEnd.plusSeconds(3600))).isFalse();

        List<AbsenceResponse> list = absences.listByProfessionalId(profId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).professionalName()).isEqualTo("Pedro Medico");

        Long rem1Id = reminders.save(new AppointmentReminder(
                null, uuid(), cita1Id, patientId, "CONFIRMACION_RESERVA", "EMAIL",
                "ana@pkg.test", "ENVIADO", null, null));
        Long rem2Id = reminders.save(new AppointmentReminder(
                null, uuid(), cita1Id, patientId, "RECORDATORIO_PREVIO", "EMAIL",
                "ana@pkg.test", "FALLIDO", "SMTP timeout", Instant.now()));
        assertThat(rem1Id).isPositive();
        assertThat(rem2Id).isGreaterThan(rem1Id);

        assertThat(reminders.countByAppointmentIdAndType(cita1Id, "CONFIRMACION_RESERVA")).isEqualTo(1);
        assertThat(reminders.countByAppointmentIdAndType(cita1Id, "CANCELACION")).isZero();
        List<AppointmentReminder> listRem = reminders.listByAppointmentId(cita1Id);
        assertThat(listRem).hasSize(2);
        assertThat(listRem.get(0).errorMessage()).isNull();
        assertThat(listRem.get(1).errorMessage()).isEqualTo("SMTP timeout");
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}
