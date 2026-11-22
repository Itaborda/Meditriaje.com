package com.meditriaje.service;

import com.meditriaje.dto.appointment.CancelAppointmentRequest;
import com.meditriaje.dto.appointment.AppointmentResponse;
import com.meditriaje.dto.appointment.BookAppointmentRequest;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.AppointmentUnavailableException;
import com.meditriaje.exception.OperationConflictException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.Appointment;
import com.meditriaje.model.AvailabilitySlot;
import com.meditriaje.model.AppointmentStatus;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.Patient;
import com.meditriaje.model.Professional;
import com.meditriaje.model.AuditResult;
import com.meditriaje.model.Triage;
import com.meditriaje.model.User;
import com.meditriaje.repository.AppointmentRepository;
import com.meditriaje.repository.AvailabilitySlotRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.ProfessionalRepository;
import com.meditriaje.repository.TriageRepository;
import com.meditriaje.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private AvailabilitySlotRepository availabilitySlotRepository;

    @Mock
    private ProfessionalRepository professionalRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private TriageRepository triageRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private com.meditriaje.repository.ClinicalAttentionRepository attentionRepository;

    private final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");
    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    private AppointmentService appointmentService;

    private static final String USUARIO_PUBLIC_ID = "usr-uuid-1";
    private static final String SLOT_PUBLIC_ID = "slot-uuid-1";
    private static final String IP_CLIENTE = "192.168.1.100";

    private User usuarioMock;
    private Patient pacienteMock;
    private AvailabilitySlot slotMock;
    private Professional profesionalMock;

    @BeforeEach
    void setUp() {
        appointmentService = new AppointmentService(
                userRepository,
                patientRepository,
                availabilitySlotRepository,
                professionalRepository,
                appointmentRepository,
                triageRepository,
                auditService,
                null,
                attentionRepository,
                fixedClock
        );

        usuarioMock = new User(
                1L, USUARIO_PUBLIC_ID, "paciente@test.com", "hash", "ACTIVO", 0, null, NOW, false
        );

        pacienteMock = new Patient(
                10L, 1L, "pac-uuid-1", "CC", "12345678", "Pepito", "Perez",
                LocalDate.of(1990, 5, 20), "3001234567", NOW, null
        );

        slotMock = new AvailabilitySlot(
                50L,
                SLOT_PUBLIC_ID,
                100L, // profesionalId
                200L, // sedeId
                300L, // especialidadId
                NOW.plusSeconds(3600),
                NOW.plusSeconds(4800),
                "PRESENCIAL",
                "LIBRE"
        );

        profesionalMock = new Professional(
                100L,
                2L,
                "prof-uuid-1",
                300L, // especialidadId coincidente
                "RM-12345",
                "Gregory",
                "House",
                NOW,
                null
        );
    }

    @Test
    void bookAppointment_success_bookingSlotCreatesAppointmentAndAudits() {
        BookAppointmentRequest request = new BookAppointmentRequest(SLOT_PUBLIC_ID);

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(pacienteMock));
        when(availabilitySlotRepository.findEntityByPublicId(SLOT_PUBLIC_ID)).thenReturn(Optional.of(slotMock));
        when(professionalRepository.findById(100L)).thenReturn(Optional.of(profesionalMock));
        when(availabilitySlotRepository.reserveSlot(50L)).thenReturn(1);
        when(appointmentRepository.create(any(Appointment.class))).thenReturn(500L);

        AppointmentResponse mockResponse = new AppointmentResponse(
                "cita-uuid-generada", SLOT_PUBLIC_ID, "pac-uuid-1", "Pepito Perez",
                "prof-uuid-1", "Gregory House", "esp-uuid-1", "Medicina Interna",
                "sede-uuid-1", "Sede Central", "Carrera 7 # 40-62",
                slotMock.startDateTime(), slotMock.endDateTime(),
                "PRESENCIAL", "PROGRAMADA", null, null, NOW
        );
        when(appointmentRepository.findByPublicId(anyString())).thenReturn(Optional.of(mockResponse));

        AppointmentResponse resultado = appointmentService.bookAppointment(request, USUARIO_PUBLIC_ID, IP_CLIENTE);

        assertThat(resultado).isNotNull();
        assertThat(resultado.status()).isEqualTo("PROGRAMADA");
        assertThat(resultado.slotPublicId()).isEqualTo(SLOT_PUBLIC_ID);

        // Verifica cambio de estado atómico del slot a OCUPADO
        verify(availabilitySlotRepository).reserveSlot(50L);

        // Verifica inserción de la cita
        ArgumentCaptor<Appointment> citaCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).create(citaCaptor.capture());
        Appointment citaGuardada = citaCaptor.getValue();
        assertThat(citaGuardada.slotId()).isEqualTo(50L);
        assertThat(citaGuardada.patientId()).isEqualTo(10L);
        assertThat(citaGuardada.status()).isEqualTo("PROGRAMADA");

        // Verifica auditoría obligatoria inmutable (ADR-011)
        ArgumentCaptor<AuditEvent> eventoCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).audit(eventoCaptor.capture());
        AuditEvent evento = eventoCaptor.getValue();
        assertThat(evento.userId()).isEqualTo(1L);
        assertThat(evento.action()).isEqualTo(AuditableAction.RESERVA_CITA);
        assertThat(evento.resourceType()).isEqualTo("CITA");
        assertThat(evento.result()).isEqualTo(AuditResult.EXITO);
        assertThat(evento.sourceIp()).isEqualTo(IP_CLIENTE);
    }

    @Test
    void bookAppointment_userNotExists_throwsResourceNotFoundException() {
        BookAppointmentRequest request = new BookAppointmentRequest(SLOT_PUBLIC_ID);
        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.bookAppointment(request, USUARIO_PUBLIC_ID, IP_CLIENTE))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Usuario no encontrado");

        verify(availabilitySlotRepository, never()).reserveSlot(anyLong());
        verify(appointmentRepository, never()).create(any());
    }

    @Test
    void bookAppointment_userNotIsPatient_throwsUnauthorizedAccessException() {
        BookAppointmentRequest request = new BookAppointmentRequest(SLOT_PUBLIC_ID);
        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.bookAppointment(request, USUARIO_PUBLIC_ID, IP_CLIENTE))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Solo pacientes registrados pueden agendar citas");

        verify(availabilitySlotRepository, never()).reserveSlot(anyLong());
        verify(appointmentRepository, never()).create(any());
    }

    @Test
    void bookAppointment_slotNotFound_throwsResourceNotFoundException() {
        BookAppointmentRequest request = new BookAppointmentRequest("slot-fantasma");
        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(pacienteMock));
        when(availabilitySlotRepository.findEntityByPublicId("slot-fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.bookAppointment(request, USUARIO_PUBLIC_ID, IP_CLIENTE))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Slot de disponibilidad no encontrado");

        verify(availabilitySlotRepository, never()).reserveSlot(anyLong());
        verify(appointmentRepository, never()).create(any());
    }

    @Test
    void bookAppointment_slotInPast_throwsInvalidDataException() {
        BookAppointmentRequest request = new BookAppointmentRequest(SLOT_PUBLIC_ID);
        AvailabilitySlot slotPasado = new AvailabilitySlot(
                50L, SLOT_PUBLIC_ID, 100L, 200L, 300L,
                NOW.minusSeconds(3600), NOW.minusSeconds(2400), "PRESENCIAL", "LIBRE"
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(pacienteMock));
        when(availabilitySlotRepository.findEntityByPublicId(SLOT_PUBLIC_ID)).thenReturn(Optional.of(slotPasado));

        assertThatThrownBy(() -> appointmentService.bookAppointment(request, USUARIO_PUBLIC_ID, IP_CLIENTE))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("No es posible agendar una cita en un horario pasado");

        verify(availabilitySlotRepository, never()).reserveSlot(anyLong());
        verify(appointmentRepository, never()).create(any());
    }

    @Test
    void bookAppointment_specialtyNotCoincideWithProfessional_throwsInvalidDataException() {
        BookAppointmentRequest request = new BookAppointmentRequest(SLOT_PUBLIC_ID);
        Professional profesionalOtraEspecialidad = new Professional(
                100L, 2L, "prof-uuid-1", 999L, // diferente especialidad a 300L
                "RM-12345", "Gregory", "House", NOW, null
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(pacienteMock));
        when(availabilitySlotRepository.findEntityByPublicId(SLOT_PUBLIC_ID)).thenReturn(Optional.of(slotMock));
        when(professionalRepository.findById(100L)).thenReturn(Optional.of(profesionalOtraEspecialidad));

        assertThatThrownBy(() -> appointmentService.bookAppointment(request, USUARIO_PUBLIC_ID, IP_CLIENTE))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("El profesional no corresponde a la especialidad del slot");

        verify(availabilitySlotRepository, never()).reserveSlot(anyLong());
        verify(appointmentRepository, never()).create(any());
    }

    @Test
    void bookAppointment_slotAlreadyOcupado_throwsAppointmentUnavailableException() {
        BookAppointmentRequest request = new BookAppointmentRequest(SLOT_PUBLIC_ID);

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(pacienteMock));
        when(availabilitySlotRepository.findEntityByPublicId(SLOT_PUBLIC_ID)).thenReturn(Optional.of(slotMock));
        when(professionalRepository.findById(100L)).thenReturn(Optional.of(profesionalMock));
        when(availabilitySlotRepository.reserveSlot(50L)).thenReturn(0); // 0 filas = no estaba libre

        assertThatThrownBy(() -> appointmentService.bookAppointment(request, USUARIO_PUBLIC_ID, IP_CLIENTE))
                .isInstanceOf(AppointmentUnavailableException.class)
                .hasMessageContaining("El slot de atencion ya ha sido reservado o no esta disponible");

        verify(appointmentRepository, never()).create(any());
        verify(auditService, never()).audit(any());
    }

    @Test
    void bookAppointment_violationUnicidadIndexOracle_throwsAppointmentUnavailableException() {
        BookAppointmentRequest request = new BookAppointmentRequest(SLOT_PUBLIC_ID);

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(pacienteMock));
        when(availabilitySlotRepository.findEntityByPublicId(SLOT_PUBLIC_ID)).thenReturn(Optional.of(slotMock));
        when(professionalRepository.findById(100L)).thenReturn(Optional.of(profesionalMock));
        when(availabilitySlotRepository.reserveSlot(50L)).thenReturn(1);
        when(appointmentRepository.create(any(Appointment.class)))
                .thenThrow(new DataIntegrityViolationException("ORA-00001: unique constraint UQ_CITA_SLOT_ACTIVA violated"));

        assertThatThrownBy(() -> appointmentService.bookAppointment(request, USUARIO_PUBLIC_ID, IP_CLIENTE))
                .isInstanceOf(AppointmentUnavailableException.class)
                .hasMessageContaining("El slot de atencion ya cuenta con una cita activa");

        verify(auditService, never()).audit(any());
    }

    @Test
    void bookAppointment_withTriageOwn_success_linksTriageIdAndSavesAppointment() {
        String triajePublicId = "triaje-uuid-propio";
        BookAppointmentRequest request = new BookAppointmentRequest(SLOT_PUBLIC_ID, triajePublicId);

        Triage triajeMock = new Triage(
                888L, triajePublicId, 10L, "v1-prototipo", "III", "CITA_PRESENCIAL", false, "Dolor leve", NOW
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(pacienteMock));
        when(availabilitySlotRepository.findEntityByPublicId(SLOT_PUBLIC_ID)).thenReturn(Optional.of(slotMock));
        when(professionalRepository.findById(100L)).thenReturn(Optional.of(profesionalMock));
        when(triageRepository.findEntityByPublicId(triajePublicId)).thenReturn(Optional.of(triajeMock));
        when(availabilitySlotRepository.reserveSlot(50L)).thenReturn(1);
        when(appointmentRepository.create(any(Appointment.class))).thenReturn(500L);

        AppointmentResponse mockResponse = new AppointmentResponse(
                "cita-uuid-generada", SLOT_PUBLIC_ID, "pac-uuid-1", "Pepito Perez",
                "prof-uuid-1", "Gregory House", "esp-uuid-1", "Medicina Interna",
                "sede-uuid-1", "Sede Central", "Carrera 7 # 40-62",
                slotMock.startDateTime(), slotMock.endDateTime(),
                "PRESENCIAL", "PROGRAMADA", triajePublicId, null, NOW
        );
        when(appointmentRepository.findByPublicId(anyString())).thenReturn(Optional.of(mockResponse));

        AppointmentResponse resultado = appointmentService.bookAppointment(request, USUARIO_PUBLIC_ID, IP_CLIENTE);

        assertThat(resultado).isNotNull();
        assertThat(resultado.triagePublicId()).isEqualTo(triajePublicId);

        ArgumentCaptor<Appointment> citaCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).create(citaCaptor.capture());
        Appointment citaGuardada = citaCaptor.getValue();
        assertThat(citaGuardada.triageId()).isEqualTo(888L);
        assertThat(citaGuardada.patientId()).isEqualTo(10L);
    }

    @Test
    void bookAppointment_withTriageInexistente_throwsResourceNotFoundException() {
        String triajePublicId = "triaje-no-existe";
        BookAppointmentRequest request = new BookAppointmentRequest(SLOT_PUBLIC_ID, triajePublicId);

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(pacienteMock));
        when(availabilitySlotRepository.findEntityByPublicId(SLOT_PUBLIC_ID)).thenReturn(Optional.of(slotMock));
        when(professionalRepository.findById(100L)).thenReturn(Optional.of(profesionalMock));
        when(triageRepository.findEntityByPublicId(triajePublicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.bookAppointment(request, USUARIO_PUBLIC_ID, IP_CLIENTE))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Triaje no encontrado");

        verify(availabilitySlotRepository, never()).reserveSlot(anyLong());
        verify(appointmentRepository, never()).create(any());
    }

    @Test
    void bookAppointment_withTriageOfOtherPatient_throwsInvalidDataException() {
        String triajePublicId = "triaje-ajeno";
        BookAppointmentRequest request = new BookAppointmentRequest(SLOT_PUBLIC_ID, triajePublicId);

        // triaje con pacienteId 999L != 10L (pacienteMock)
        Triage triajeAjeno = new Triage(
                888L, triajePublicId, 999L, "v1-prototipo", "III", "CITA_PRESENCIAL", false, null, NOW
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(pacienteMock));
        when(availabilitySlotRepository.findEntityByPublicId(SLOT_PUBLIC_ID)).thenReturn(Optional.of(slotMock));
        when(professionalRepository.findById(100L)).thenReturn(Optional.of(profesionalMock));
        when(triageRepository.findEntityByPublicId(triajePublicId)).thenReturn(Optional.of(triajeAjeno));

        assertThatThrownBy(() -> appointmentService.bookAppointment(request, USUARIO_PUBLIC_ID, IP_CLIENTE))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("El triaje no corresponde al paciente de la cita");

        verify(availabilitySlotRepository, never()).reserveSlot(anyLong());
        verify(appointmentRepository, never()).create(any());
    }

    @Test
    void bookAppointment_withTriageOfEmergency_throwsInvalidDataException() {
        String triajePublicId = "triaje-emergencia";
        BookAppointmentRequest request = new BookAppointmentRequest(SLOT_PUBLIC_ID, triajePublicId);

        Triage triajeEmergencia = new Triage(
                888L, triajePublicId, 10L, "v1-prototipo", "I", "URGENCIAS", true, null, NOW
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(pacienteMock));
        when(availabilitySlotRepository.findEntityByPublicId(SLOT_PUBLIC_ID)).thenReturn(Optional.of(slotMock));
        when(professionalRepository.findById(100L)).thenReturn(Optional.of(profesionalMock));
        when(triageRepository.findEntityByPublicId(triajePublicId)).thenReturn(Optional.of(triajeEmergencia));

        assertThatThrownBy(() -> appointmentService.bookAppointment(request, USUARIO_PUBLIC_ID, IP_CLIENTE))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("No se permite agendar cita para un triaje clasificado como emergencia");

        verify(availabilitySlotRepository, never()).reserveSlot(anyLong());
        verify(appointmentRepository, never()).create(any());
    }

    // =========================================================================
    // CANCELACIÓN DE CITAS Y MÁQUINA DE ESTADOS (ADR-006, HU-05, D2)
    // =========================================================================

    @Test
    void cancelAppointment_withLinkedAttention_throwsOperationConflictException409() {
        Appointment citaProgramada = new Appointment(
                500L, "cita-uuid-1", 50L, 10L, null, null, "PROGRAMADA", null, NOW, NOW
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(appointmentRepository.findEntityByPublicId("cita-uuid-1")).thenReturn(Optional.of(citaProgramada));
        when(attentionRepository.existsByAppointmentId(500L)).thenReturn(true);

        assertThatThrownBy(() -> appointmentService.cancelAppointment(
                "cita-uuid-1",
                new CancelAppointmentRequest("Motivo"),
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")),
                IP_CLIENTE
        ))
                .isInstanceOf(OperationConflictException.class)
                .hasMessage("No es posible cancelar una cita que ya cuenta con una atencion clinica vinculada.");

        verify(availabilitySlotRepository, never()).releaseSlot(anyLong());
        verify(appointmentRepository, never()).updateStatus(anyLong(), anyString(), any());
        verify(auditService, never()).audit(any());
    }

    @Test
    void cancelAppointment_patientWithMasOfTwoHours_success_updatesStatusReleasesSlotAndAudits() {
        // Slot a 3 horas de distancia (más de 2 horas)
        AvailabilitySlot slotFuturo = new AvailabilitySlot(
                50L, SLOT_PUBLIC_ID, 100L, 200L, 300L,
                NOW.plusSeconds(10800), NOW.plusSeconds(12000), "PRESENCIAL", "OCUPADO"
        );

        Appointment citaProgramada = new Appointment(
                500L, "cita-uuid-1", 50L, 10L, null, null, "PROGRAMADA", null, NOW, NOW
        );

        AppointmentResponse mockResponse = new AppointmentResponse(
                "cita-uuid-1", SLOT_PUBLIC_ID, "pac-uuid-1", "Pepito Perez",
                "prof-uuid-1", "Gregory House", "esp-uuid-1", "Medicina Interna",
                "sede-uuid-1", "Sede Central", "Carrera 7 # 40-62",
                slotFuturo.startDateTime(), slotFuturo.endDateTime(),
                "PRESENCIAL", "CANCELADA", null, null, NOW
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(appointmentRepository.findEntityByPublicId("cita-uuid-1")).thenReturn(Optional.of(citaProgramada));
        when(availabilitySlotRepository.findById(50L)).thenReturn(Optional.of(slotFuturo));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(pacienteMock));
        when(appointmentRepository.findByPublicId("cita-uuid-1")).thenReturn(Optional.of(mockResponse));

        CancelAppointmentRequest req = new CancelAppointmentRequest("Imprevisto laboral");
        AppointmentResponse resultado = appointmentService.cancelAppointment(
                "cita-uuid-1",
                req,
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")),
                IP_CLIENTE
        );

        assertThat(resultado).isNotNull();
        assertThat(resultado.status()).isEqualTo("CANCELADA");

        // 1. Verifica actualización en BD
        verify(appointmentRepository).updateStatus(500L, "CANCELADA", "Imprevisto laboral");

        // 2. Verifica liberación de slot a LIBRE
        verify(availabilitySlotRepository).releaseSlot(50L);

        // 3. Verifica auditoría inmutable
        ArgumentCaptor<AuditEvent> eventoCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).audit(eventoCaptor.capture());
        AuditEvent evento = eventoCaptor.getValue();
        assertThat(evento.userId()).isEqualTo(1L);
        assertThat(evento.action()).isEqualTo(AuditableAction.CANCELACION_CITA);
        assertThat(evento.resourceType()).isEqualTo("CITA");
        assertThat(evento.resourcePublicId()).isEqualTo("cita-uuid-1");
        assertThat(evento.result()).isEqualTo(AuditResult.EXITO);
        assertThat(evento.sourceIp()).isEqualTo(IP_CLIENTE);
    }

    @Test
    void cancelAppointment_patientWithLessOfTwoHours_throwsInvalidDataException() {
        // Slot inicia en 1 hora (menos de 2 horas)
        AvailabilitySlot slotCercano = new AvailabilitySlot(
                50L, SLOT_PUBLIC_ID, 100L, 200L, 300L,
                NOW.plusSeconds(3600), NOW.plusSeconds(4800), "PRESENCIAL", "OCUPADO"
        );

        Appointment citaProgramada = new Appointment(
                500L, "cita-uuid-1", 50L, 10L, null, null, "PROGRAMADA", null, NOW, NOW
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(appointmentRepository.findEntityByPublicId("cita-uuid-1")).thenReturn(Optional.of(citaProgramada));
        when(availabilitySlotRepository.findById(50L)).thenReturn(Optional.of(slotCercano));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(pacienteMock));

        assertThatThrownBy(() -> appointmentService.cancelAppointment(
                "cita-uuid-1",
                new CancelAppointmentRequest("Motivo"),
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")),
                IP_CLIENTE
        ))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("La cancelacion por parte del paciente solo esta permitida hasta 2 horas antes de la cita.");

        verify(appointmentRepository, never()).updateStatus(anyLong(), anyString(), any());
        verify(availabilitySlotRepository, never()).releaseSlot(anyLong());
        verify(auditService, never()).audit(any());
    }

    @Test
    void cancelAppointment_patientAppointmentInThePast_throwsInvalidDataException() {
        AvailabilitySlot slotPasado = new AvailabilitySlot(
                50L, SLOT_PUBLIC_ID, 100L, 200L, 300L,
                NOW.minusSeconds(1800), NOW.minusSeconds(600), "PRESENCIAL", "OCUPADO"
        );

        Appointment citaProgramada = new Appointment(
                500L, "cita-uuid-1", 50L, 10L, null, null, "PROGRAMADA", null, NOW, NOW
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(appointmentRepository.findEntityByPublicId("cita-uuid-1")).thenReturn(Optional.of(citaProgramada));
        when(availabilitySlotRepository.findById(50L)).thenReturn(Optional.of(slotPasado));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(pacienteMock));

        assertThatThrownBy(() -> appointmentService.cancelAppointment(
                "cita-uuid-1",
                new CancelAppointmentRequest("Motivo"),
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")),
                IP_CLIENTE
        ))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("La cancelacion por parte del paciente solo esta permitida hasta 2 horas antes de la cita.");

        verify(appointmentRepository, never()).updateStatus(anyLong(), anyString(), any());
        verify(availabilitySlotRepository, never()).releaseSlot(anyLong());
        verify(auditService, never()).audit(any());
    }

    @Test
    void cancelAppointment_patientAttemptsCancelAppointmentOther_throwsUnauthorizedAccessException() {
        AvailabilitySlot slotFuturo = new AvailabilitySlot(
                50L, SLOT_PUBLIC_ID, 100L, 200L, 300L,
                NOW.plusSeconds(10800), NOW.plusSeconds(12000), "PRESENCIAL", "OCUPADO"
        );

        // Cita con pacienteId = 999L (ajena al paciente autenticado con id = 10L)
        Appointment citaAjena = new Appointment(
                500L, "cita-uuid-1", 50L, 999L, null, null, "PROGRAMADA", null, NOW, NOW
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(appointmentRepository.findEntityByPublicId("cita-uuid-1")).thenReturn(Optional.of(citaAjena));
        when(availabilitySlotRepository.findById(50L)).thenReturn(Optional.of(slotFuturo));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(pacienteMock));

        assertThatThrownBy(() -> appointmentService.cancelAppointment(
                "cita-uuid-1",
                new CancelAppointmentRequest("Motivo"),
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")),
                IP_CLIENTE
        ))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessage("No tiene autorizacion para cancelar una cita ajena.");

        verify(appointmentRepository, never()).updateStatus(anyLong(), anyString(), any());
        verify(availabilitySlotRepository, never()).releaseSlot(anyLong());
        verify(auditService, never()).audit(any());
    }

    @Test
    void cancelAppointment_invalidTransitionFromAttended_throwsInvalidDataException() {
        Appointment citaAtendida = new Appointment(
                500L, "cita-uuid-1", 50L, 10L, null, null, "ATENDIDA", null, NOW, NOW
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(appointmentRepository.findEntityByPublicId("cita-uuid-1")).thenReturn(Optional.of(citaAtendida));

        assertThatThrownBy(() -> appointmentService.cancelAppointment(
                "cita-uuid-1",
                null,
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")),
                IP_CLIENTE
        ))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Transicion de estado no permitida de ATENDIDA a CANCELADA.");

        verify(availabilitySlotRepository, never()).findById(anyLong());
        verify(appointmentRepository, never()).updateStatus(anyLong(), anyString(), any());
        verify(auditService, never()).audit(any());
    }

    @Test
    void cancelAppointment_invalidTransitionFromCancelled_throwsInvalidDataException() {
        Appointment citaCancelada = new Appointment(
                500L, "cita-uuid-1", 50L, 10L, null, null, "CANCELADA", "Cancelada previamente", NOW, NOW
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(appointmentRepository.findEntityByPublicId("cita-uuid-1")).thenReturn(Optional.of(citaCancelada));

        assertThatThrownBy(() -> appointmentService.cancelAppointment(
                "cita-uuid-1",
                null,
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")),
                IP_CLIENTE
        ))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Transicion de estado no permitida de CANCELADA a CANCELADA.");

        verify(availabilitySlotRepository, never()).findById(anyLong());
        verify(appointmentRepository, never()).updateStatus(anyLong(), anyString(), any());
    }

    @Test
    void cancelAppointment_professionalOfItsOwnSlotWithoutRestriccionTwoHours_success() {
        // Slot inicia en solo 30 minutos (menos de 2 horas)
        AvailabilitySlot slotInmediato = new AvailabilitySlot(
                50L, SLOT_PUBLIC_ID, 100L, 200L, 300L,
                NOW.plusSeconds(1800), NOW.plusSeconds(3000), "PRESENCIAL", "OCUPADO"
        );

        Appointment citaConfirmada = new Appointment(
                500L, "cita-uuid-1", 50L, 10L, null, null, "CONFIRMADA", null, NOW, NOW
        );

        User usuarioProfesional = new User(
                2L, "prof-usr-uuid", "drhouse@test.com", "hash", "ACTIVO", 0, null, NOW, false
        );

        AppointmentResponse mockResponse = new AppointmentResponse(
                "cita-uuid-1", SLOT_PUBLIC_ID, "pac-uuid-1", "Pepito Perez",
                "prof-uuid-1", "Gregory House", "esp-uuid-1", "Medicina Interna",
                "sede-uuid-1", "Sede Central", "Carrera 7 # 40-62",
                slotInmediato.startDateTime(), slotInmediato.endDateTime(),
                "PRESENCIAL", "CANCELADA", null, null, NOW
        );

        when(userRepository.findByPublicId("prof-usr-uuid")).thenReturn(Optional.of(usuarioProfesional));
        when(appointmentRepository.findEntityByPublicId("cita-uuid-1")).thenReturn(Optional.of(citaConfirmada));
        when(availabilitySlotRepository.findById(50L)).thenReturn(Optional.of(slotInmediato));
        when(professionalRepository.findByUserId(2L)).thenReturn(Optional.of(profesionalMock));
        when(appointmentRepository.findByPublicId("cita-uuid-1")).thenReturn(Optional.of(mockResponse));

        AppointmentResponse res = appointmentService.cancelAppointment(
                "cita-uuid-1",
                new CancelAppointmentRequest("Emergencia medica del profesional"),
                "prof-usr-uuid",
                List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL")),
                IP_CLIENTE
        );

        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo("CANCELADA");

        verify(appointmentRepository).updateStatus(500L, "CANCELADA", "Emergencia medica del profesional");
        verify(availabilitySlotRepository).releaseSlot(50L);
        verify(auditService).audit(any(AuditEvent.class));
    }

    @Test
    void cancelAppointment_professionalOfSlotOther_throwsUnauthorizedAccessException() {
        // Slot asignado al profesional 999L
        AvailabilitySlot slotDeOtroProfesional = new AvailabilitySlot(
                50L, SLOT_PUBLIC_ID, 999L, 200L, 300L,
                NOW.plusSeconds(3600), NOW.plusSeconds(4800), "PRESENCIAL", "OCUPADO"
        );

        Appointment citaProgramada = new Appointment(
                500L, "cita-uuid-1", 50L, 10L, null, null, "PROGRAMADA", null, NOW, NOW
        );

        User usuarioProfesional = new User(
                2L, "prof-usr-uuid", "drhouse@test.com", "hash", "ACTIVO", 0, null, NOW, false
        );

        when(userRepository.findByPublicId("prof-usr-uuid")).thenReturn(Optional.of(usuarioProfesional));
        when(appointmentRepository.findEntityByPublicId("cita-uuid-1")).thenReturn(Optional.of(citaProgramada));
        when(availabilitySlotRepository.findById(50L)).thenReturn(Optional.of(slotDeOtroProfesional));
        when(professionalRepository.findByUserId(2L)).thenReturn(Optional.of(profesionalMock));

        assertThatThrownBy(() -> appointmentService.cancelAppointment(
                "cita-uuid-1",
                new CancelAppointmentRequest("Motivo"),
                "prof-usr-uuid",
                List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL")),
                IP_CLIENTE
        ))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessage("El profesional no tiene autorizacion para cancelar citas de otro colega.");

        verify(appointmentRepository, never()).updateStatus(anyLong(), anyString(), any());
        verify(availabilitySlotRepository, never()).releaseSlot(anyLong());
    }

    @Test
    void cancelAppointment_administratorWithoutRestriccionTwoHours_success() {
        // Slot inicia en 10 minutos
        AvailabilitySlot slotInmediato = new AvailabilitySlot(
                50L, SLOT_PUBLIC_ID, 100L, 200L, 300L,
                NOW.plusSeconds(600), NOW.plusSeconds(1800), "PRESENCIAL", "OCUPADO"
        );

        Appointment citaProgramada = new Appointment(
                500L, "cita-uuid-1", 50L, 10L, null, null, "PROGRAMADA", null, NOW, NOW
        );

        User usuarioAdmin = new User(
                3L, "admin-usr-uuid", "admin@test.com", "hash", "ACTIVO", 0, null, NOW, false
        );

        AppointmentResponse mockResponse = new AppointmentResponse(
                "cita-uuid-1", SLOT_PUBLIC_ID, "pac-uuid-1", "Pepito Perez",
                "prof-uuid-1", "Gregory House", "esp-uuid-1", "Medicina Interna",
                "sede-uuid-1", "Sede Central", "Carrera 7 # 40-62",
                slotInmediato.startDateTime(), slotInmediato.endDateTime(),
                "PRESENCIAL", "CANCELADA", null, null, NOW
        );

        when(userRepository.findByPublicId("admin-usr-uuid")).thenReturn(Optional.of(usuarioAdmin));
        when(appointmentRepository.findEntityByPublicId("cita-uuid-1")).thenReturn(Optional.of(citaProgramada));
        when(availabilitySlotRepository.findById(50L)).thenReturn(Optional.of(slotInmediato));
        when(appointmentRepository.findByPublicId("cita-uuid-1")).thenReturn(Optional.of(mockResponse));

        AppointmentResponse res = appointmentService.cancelAppointment(
                "cita-uuid-1",
                new CancelAppointmentRequest("Cancelacion administrativa por cierre de sede"),
                "admin-usr-uuid",
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")),
                IP_CLIENTE
        );

        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo("CANCELADA");

        verify(appointmentRepository).updateStatus(500L, "CANCELADA", "Cancelacion administrativa por cierre de sede");
        verify(availabilitySlotRepository).releaseSlot(50L);
        verify(auditService).audit(any(AuditEvent.class));
    }

    @Test
    void cancelAppointment_appointmentNotFound_throwsResourceNotFoundException() {
        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(appointmentRepository.findEntityByPublicId("cita-inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.cancelAppointment(
                "cita-inexistente",
                null,
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")),
                IP_CLIENTE
        ))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cita no encontrada");

        verify(availabilitySlotRepository, never()).findById(anyLong());
        verify(appointmentRepository, never()).updateStatus(anyLong(), anyString(), any());
    }

    @Test
    void cancelAppointment_userNotExists_throwsResourceNotFoundException() {
        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.cancelAppointment(
                "cita-uuid-1",
                null,
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")),
                IP_CLIENTE
        ))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Usuario no encontrado");

        verify(appointmentRepository, never()).findEntityByPublicId(anyString());
    }

    @Test
    void cancelAppointment_patientWithoutRecordPatient_throwsUnauthorizedAccessException() {
        AvailabilitySlot slotFuturo = new AvailabilitySlot(
                50L, SLOT_PUBLIC_ID, 100L, 200L, 300L,
                NOW.plusSeconds(10800), NOW.plusSeconds(12000), "PRESENCIAL", "OCUPADO"
        );

        Appointment citaProgramada = new Appointment(
                500L, "cita-uuid-1", 50L, 10L, null, null, "PROGRAMADA", null, NOW, NOW
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(appointmentRepository.findEntityByPublicId("cita-uuid-1")).thenReturn(Optional.of(citaProgramada));
        when(availabilitySlotRepository.findById(50L)).thenReturn(Optional.of(slotFuturo));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.cancelAppointment(
                "cita-uuid-1",
                null,
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")),
                IP_CLIENTE
        ))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessage("Solo pacientes registrados pueden cancelar citas.");
    }

    @Test
    void cancelAppointment_professionalWithoutRecordProfessional_throwsUnauthorizedAccessException() {
        AvailabilitySlot slotFuturo = new AvailabilitySlot(
                50L, SLOT_PUBLIC_ID, 100L, 200L, 300L,
                NOW.plusSeconds(10800), NOW.plusSeconds(12000), "PRESENCIAL", "OCUPADO"
        );

        Appointment citaProgramada = new Appointment(
                500L, "cita-uuid-1", 50L, 10L, null, null, "PROGRAMADA", null, NOW, NOW
        );

        User usrProf = new User(2L, "prof-usr-uuid", "prof@test.com", "hash", "ACTIVO", 0, null, NOW, false);

        when(userRepository.findByPublicId("prof-usr-uuid")).thenReturn(Optional.of(usrProf));
        when(appointmentRepository.findEntityByPublicId("cita-uuid-1")).thenReturn(Optional.of(citaProgramada));
        when(availabilitySlotRepository.findById(50L)).thenReturn(Optional.of(slotFuturo));
        when(professionalRepository.findByUserId(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.cancelAppointment(
                "cita-uuid-1",
                null,
                "prof-usr-uuid",
                List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL")),
                IP_CLIENTE
        ))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessage("El profesional no tiene autorizacion para cancelar citas de otro colega.");
    }

    // =========================================================================
    // AGENDA DEL PROFESIONAL ASISTENCIAL (HU-06, ADR-007)
    // =========================================================================

    @Test
    void getMyAgenda_withProfessionalValid_returnsPageAppointments() {
        User usrProf = new User(2L, "prof-usr-uuid", "dr.mendoza@test.com", "hash", "ACTIVO", 0, null, NOW, false);
        Professional prof = new Professional(5L, 2L, "prof-uuid-5", 1L, "RM-123", "Carlos", "Mendoza", NOW, null);
        AppointmentResponse citaResp = new AppointmentResponse(
                "cita-1", "slot-1", "pac-1", "Ana Gomez",
                "prof-uuid-5", "Carlos Mendoza", "esp-1", "Medicina General",
                "sede-1", "Sede Centro", "Calle 10", NOW.plusSeconds(3600), NOW.plusSeconds(4800),
                "PRESENCIAL", "PROGRAMADA", null, null, NOW
        );

        when(userRepository.findByPublicId("prof-usr-uuid")).thenReturn(Optional.of(usrProf));
        when(professionalRepository.findByUserId(2L)).thenReturn(Optional.of(prof));
        when(appointmentRepository.listProfessionalAgenda(5L, null, null, null, 0, 10))
                .thenReturn(List.of(citaResp));
        when(appointmentRepository.countProfessionalAgenda(5L, null, null, null)).thenReturn(1);

        PaginatedResponse<AppointmentResponse> resultado = appointmentService.getMyAgenda(
                "prof-usr-uuid", null, null, 0, 10
        );

        assertThat(resultado.content()).hasSize(1);
        assertThat(resultado.totalElements()).isEqualTo(1L);
        assertThat(resultado.content().get(0).publicId()).isEqualTo("cita-1");
        assertThat(resultado.content().get(0).professionalName()).isEqualTo("Carlos Mendoza");
    }

    @Test
    void getMyAgenda_withFilterDateAndStatus_convertsInstantsBogotaAndFilters() {
        User usrProf = new User(2L, "prof-usr-uuid", "dr.mendoza@test.com", "hash", "ACTIVO", 0, null, NOW, false);
        Professional prof = new Professional(5L, 2L, "prof-uuid-5", 1L, "RM-123", "Carlos", "Mendoza", NOW, null);
        LocalDate fecha = LocalDate.of(2026, 10, 15);

        when(userRepository.findByPublicId("prof-usr-uuid")).thenReturn(Optional.of(usrProf));
        when(professionalRepository.findByUserId(2L)).thenReturn(Optional.of(prof));

        ArgumentCaptor<Instant> desdeCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> hastaCaptor = ArgumentCaptor.forClass(Instant.class);

        when(appointmentRepository.listProfessionalAgenda(eq(5L), desdeCaptor.capture(), hastaCaptor.capture(), eq("PROGRAMADA"), eq(0), eq(10)))
                .thenReturn(List.of());
        when(appointmentRepository.countProfessionalAgenda(eq(5L), any(Instant.class), any(Instant.class), eq("PROGRAMADA")))
                .thenReturn(0);

        PaginatedResponse<AppointmentResponse> resultado = appointmentService.getMyAgenda(
                "prof-usr-uuid", fecha, "programada", 0, 10
        );

        assertThat(resultado.content()).isEmpty();
        assertThat(resultado.totalElements()).isZero();

        // En America/Bogota (UTC-5), el 2026-10-15 00:00:00 equivale a 2026-10-15T05:00:00Z
        assertThat(desdeCaptor.getValue()).isEqualTo(Instant.parse("2026-10-15T05:00:00Z"));
        // Y 2026-10-15 23:59:59.999999999 equivale a 2026-10-16T04:59:59.999999999Z
        assertThat(hastaCaptor.getValue()).isEqualTo(Instant.parse("2026-10-16T04:59:59.999999999Z"));
    }

    @Test
    void getMyAgenda_whenUserNotExists_throwsResourceNotFound() {
        when(userRepository.findByPublicId("usuario-fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.getMyAgenda("usuario-fantasma", null, null, 0, 10))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Usuario no encontrado.");
    }

    @Test
    void getMyAgenda_whenUserNotIsProfessional_throwsUnauthorizedAccess() {
        User usr = new User(10L, "usr-paciente", "paciente@test.com", "hash", "ACTIVO", 0, null, NOW, false);
        when(userRepository.findByPublicId("usr-paciente")).thenReturn(Optional.of(usr));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.getMyAgenda("usr-paciente", null, null, 0, 10))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessage("Solo profesionales registrados pueden consultar su agenda.");
    }

    @Test
    void getMyAgenda_whenPaginationInvalid_throwsInvalidData() {
        assertThatThrownBy(() -> appointmentService.getMyAgenda("prof-usr", null, null, -1, 10))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("El número de página no puede ser menor a 0.");

        assertThatThrownBy(() -> appointmentService.getMyAgenda("prof-usr", null, null, 0, 0))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("El tamaño de página debe estar entre 1 y 100.");

        assertThatThrownBy(() -> appointmentService.getMyAgenda("prof-usr", null, null, 0, 101))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("El tamaño de página debe estar entre 1 y 100.");
    }

    @Test
    void getMyAgenda_whenStatusInvalid_throwsInvalidData() {
        assertThatThrownBy(() -> appointmentService.getMyAgenda("prof-usr", null, "ESTADO_INEXISTENTE", 0, 10))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Estado de cita no válido: ESTADO_INEXISTENTE");
    }

    @Test
    void getMyAgenda_aislamiento_physicianOnlyVeItsAppointments() {
        User usrProfA = new User(2L, "prof-usr-A", "dr.a@test.com", "hash", "ACTIVO", 0, null, NOW, false);
        Professional profA = new Professional(5L, 2L, "prof-A", 1L, "RM-101", "Dr", "A", NOW, null);

        when(userRepository.findByPublicId("prof-usr-A")).thenReturn(Optional.of(usrProfA));
        when(professionalRepository.findByUserId(2L)).thenReturn(Optional.of(profA));
        when(appointmentRepository.listProfessionalAgenda(5L, null, null, null, 0, 10)).thenReturn(List.of());
        when(appointmentRepository.countProfessionalAgenda(5L, null, null, null)).thenReturn(0);

        appointmentService.getMyAgenda("prof-usr-A", null, null, 0, 10);

        // Se verifica que la consulta se hace estrictamente con el id del profesional autenticado (5L)
        verify(appointmentRepository).listProfessionalAgenda(eq(5L), eq(null), eq(null), eq(null), eq(0), eq(10));
        verify(appointmentRepository).countProfessionalAgenda(eq(5L), eq(null), eq(null), eq(null));
    }

    // =========================================================================
    // CITAS DEL PACIENTE (HU-09)
    // =========================================================================

    @Test
    void getMyAppointments_withPatientValid_returnsPageAppointments() {
        User usrPac = new User(1L, "pac-usr-uuid", "carlos@test.com", "hash", "ACTIVO", 0, null, NOW, false);
        Patient pac = new Patient(10L, 1L, "pac-public-uuid", "CC", "1098765432", "Carlos", "Perez",
                LocalDate.of(1990, 5, 20), "3001234567", NOW, null);

        AppointmentResponse citaResp = new AppointmentResponse(
                "cita-1", "slot-1", "pac-public-uuid", "Carlos Perez",
                "prof-uuid-5", "Carlos Mendoza", "esp-1", "Medicina General",
                "sede-1", "Sede Centro", "Calle 10", NOW.plusSeconds(3600), NOW.plusSeconds(4800),
                "PRESENCIAL", "PROGRAMADA", null, null, NOW
        );

        when(userRepository.findByPublicId("pac-usr-uuid")).thenReturn(Optional.of(usrPac));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(pac));
        when(appointmentRepository.listByPatientId(10L, 0, 10)).thenReturn(List.of(citaResp));
        when(appointmentRepository.countByPatientId(10L)).thenReturn(1);

        PaginatedResponse<AppointmentResponse> resultado = appointmentService.getMyAppointments("pac-usr-uuid", 0, 10);

        assertThat(resultado).isNotNull();
        assertThat(resultado.content()).hasSize(1);
        assertThat(resultado.content().get(0).publicId()).isEqualTo("cita-1");
        assertThat(resultado.totalElements()).isEqualTo(1L);
        verify(appointmentRepository).listByPatientId(10L, 0, 10);
        verify(appointmentRepository).countByPatientId(10L);
    }

    @Test
    void getMyAppointments_withUserNotExistente_throwsResourceNotFound() {
        when(userRepository.findByPublicId("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.getMyAppointments("no-existe", 0, 10))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Usuario no encontrado.");
    }

    @Test
    void getMyAppointments_withUserNotPatient_throwsUnauthorizedAccess() {
        User usr = new User(99L, "usr-uuid", "admin@test.com", "hash", "ACTIVO", 0, null, NOW, false);
        when(userRepository.findByPublicId("usr-uuid")).thenReturn(Optional.of(usr));
        when(patientRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.getMyAppointments("usr-uuid", 0, 10))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessage("Solo pacientes registrados pueden consultar sus citas.");
    }

    @Test
    void getMyAppointments_withPaginationInvalid_throwsInvalidData() {
        assertThatThrownBy(() -> appointmentService.getMyAppointments("pac-usr", -1, 10))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("El número de página no puede ser menor a 0.");

        assertThatThrownBy(() -> appointmentService.getMyAppointments("pac-usr", 0, 0))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("El tamaño de página debe estar entre 1 y 100.");

        assertThatThrownBy(() -> appointmentService.getMyAppointments("pac-usr", 0, 101))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("El tamaño de página debe estar entre 1 y 100.");
    }

    // =========================================================================
    // Pruebas T5: Inasistencia (marcarNoAsistio - D4, T5)
    // =========================================================================

    @Test
    void markNotShow_success_byAssignedProfessional_withTimeExactStart() {
        String citaPubId = "cita-no-show-1";
        Appointment cita = new Appointment(10L, citaPubId, 50L, 10L, null, null, "PROGRAMADA", null, NOW.minusSeconds(7200), null);
        AvailabilitySlot slotIniciado = new AvailabilitySlot(
                50L, "slot-1", 100L, 200L, 300L, NOW, NOW.plusSeconds(1800), "PRESENCIAL", "OCUPADO"
        );
        User profUsuario = new User(2L, "prof-usr-1", "medico@test.com", "hash", "ACTIVO", 0, null, NOW, false);
        AppointmentResponse expectedResponse = new AppointmentResponse(
                citaPubId, "slot-1", "pac-1", "Pepito Perez", "prof-1", "Dr. House",
                "esp-1", "Medicina", "sede-1", "Sede Central", "Dir 1",
                NOW, NOW.plusSeconds(1800), "PRESENCIAL", "NO_ASISTIO", null, null, NOW
        );

        when(userRepository.findByPublicId("prof-usr-1")).thenReturn(Optional.of(profUsuario));
        when(appointmentRepository.findEntityByPublicId(citaPubId)).thenReturn(Optional.of(cita));
        when(availabilitySlotRepository.findById(50L)).thenReturn(Optional.of(slotIniciado));
        when(professionalRepository.findByUserId(2L)).thenReturn(Optional.of(profesionalMock));
        when(attentionRepository.existsByAppointmentId(10L)).thenReturn(false);
        when(appointmentRepository.findByPublicId(citaPubId)).thenReturn(Optional.of(expectedResponse));

        AppointmentResponse result = appointmentService.markNoShow(
                citaPubId,
                "prof-usr-1",
                List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL")),
                IP_CLIENTE
        );

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("NO_ASISTIO");
        verify(appointmentRepository).updateStatus(10L, "NO_ASISTIO", null);
        verify(availabilitySlotRepository, never()).releaseSlot(anyLong());

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).audit(auditCaptor.capture());
        AuditEvent evento = auditCaptor.getValue();
        assertThat(evento.action()).isEqualTo(AuditableAction.CITA_NO_ASISTIO);
        assertThat(evento.resourcePublicId()).isEqualTo(citaPubId);
    }

    @Test
    void markNotShow_success_byAssignedProfessional_afterStart() {
        String citaPubId = "cita-no-show-2";
        Appointment cita = new Appointment(10L, citaPubId, 50L, 10L, null, null, "PROGRAMADA", null, NOW.minusSeconds(7200), null);
        AvailabilitySlot slotPasado = new AvailabilitySlot(
                50L, "slot-1", 100L, 200L, 300L, NOW.minusSeconds(900), NOW.plusSeconds(900), "PRESENCIAL", "OCUPADO"
        );
        User profUsuario = new User(2L, "prof-usr-1", "medico@test.com", "hash", "ACTIVO", 0, null, NOW, false);
        AppointmentResponse expectedResponse = new AppointmentResponse(
                citaPubId, "slot-1", "pac-1", "Pepito Perez", "prof-1", "Dr. House",
                "esp-1", "Medicina", "sede-1", "Sede Central", "Dir 1",
                NOW.minusSeconds(900), NOW.plusSeconds(900), "PRESENCIAL", "NO_ASISTIO", null, null, NOW
        );

        when(userRepository.findByPublicId("prof-usr-1")).thenReturn(Optional.of(profUsuario));
        when(appointmentRepository.findEntityByPublicId(citaPubId)).thenReturn(Optional.of(cita));
        when(availabilitySlotRepository.findById(50L)).thenReturn(Optional.of(slotPasado));
        when(professionalRepository.findByUserId(2L)).thenReturn(Optional.of(profesionalMock));
        when(attentionRepository.existsByAppointmentId(10L)).thenReturn(false);
        when(appointmentRepository.findByPublicId(citaPubId)).thenReturn(Optional.of(expectedResponse));

        AppointmentResponse result = appointmentService.markNoShow(
                citaPubId,
                "prof-usr-1",
                List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL")),
                IP_CLIENTE
        );

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("NO_ASISTIO");
        verify(appointmentRepository).updateStatus(10L, "NO_ASISTIO", null);
        verify(availabilitySlotRepository, never()).releaseSlot(anyLong());
    }

    @Test
    void markNotShow_success_byAdministrator() {
        String citaPubId = "cita-admin-no-show";
        Appointment cita = new Appointment(10L, citaPubId, 50L, 10L, null, null, "PROGRAMADA", null, NOW.minusSeconds(7200), null);
        AvailabilitySlot slotIniciado = new AvailabilitySlot(
                50L, "slot-1", 100L, 200L, 300L, NOW.minusSeconds(300), NOW.plusSeconds(1500), "PRESENCIAL", "OCUPADO"
        );
        User adminUsuario = new User(99L, "admin-usr-1", "admin@hospital.com", "hash", "ACTIVO", 0, null, NOW, false);
        AppointmentResponse expectedResponse = new AppointmentResponse(
                citaPubId, "slot-1", "pac-1", "Pepito Perez", "prof-1", "Dr. House",
                "esp-1", "Medicina", "sede-1", "Sede Central", "Dir 1",
                NOW.minusSeconds(300), NOW.plusSeconds(1500), "PRESENCIAL", "NO_ASISTIO", null, null, NOW
        );

        when(userRepository.findByPublicId("admin-usr-1")).thenReturn(Optional.of(adminUsuario));
        when(appointmentRepository.findEntityByPublicId(citaPubId)).thenReturn(Optional.of(cita));
        when(availabilitySlotRepository.findById(50L)).thenReturn(Optional.of(slotIniciado));
        when(attentionRepository.existsByAppointmentId(10L)).thenReturn(false);
        when(appointmentRepository.findByPublicId(citaPubId)).thenReturn(Optional.of(expectedResponse));

        AppointmentResponse result = appointmentService.markNoShow(
                citaPubId,
                "admin-usr-1",
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")),
                IP_CLIENTE
        );

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("NO_ASISTIO");
        verify(professionalRepository, never()).findByUserId(anyLong());
    }

    @Test
    void markNotShow_fails_beforeTimeStart_throwsInvalidData() {
        String citaPubId = "cita-futura";
        Appointment cita = new Appointment(10L, citaPubId, 50L, 10L, null, null, "PROGRAMADA", null, NOW.minusSeconds(3600), null);
        AvailabilitySlot slotFuturo = new AvailabilitySlot(
                50L, "slot-1", 100L, 200L, 300L, NOW.plusSeconds(600), NOW.plusSeconds(2400), "PRESENCIAL", "OCUPADO"
        );
        User profUsuario = new User(2L, "prof-usr-1", "medico@test.com", "hash", "ACTIVO", 0, null, NOW, false);

        when(userRepository.findByPublicId("prof-usr-1")).thenReturn(Optional.of(profUsuario));
        when(appointmentRepository.findEntityByPublicId(citaPubId)).thenReturn(Optional.of(cita));
        when(availabilitySlotRepository.findById(50L)).thenReturn(Optional.of(slotFuturo));
        when(professionalRepository.findByUserId(2L)).thenReturn(Optional.of(profesionalMock));
        when(attentionRepository.existsByAppointmentId(10L)).thenReturn(false);

        assertThatThrownBy(() -> appointmentService.markNoShow(
                citaPubId,
                "prof-usr-1",
                List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL")),
                IP_CLIENTE
        )).isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("No es posible marcar inasistencia antes de la hora de inicio");
    }

    @Test
    void markNotShow_fails_withLinkedAttention_throwsOperationConflict() {
        String citaPubId = "cita-con-atencion";
        Appointment cita = new Appointment(10L, citaPubId, 50L, 10L, null, null, "PROGRAMADA", null, NOW.minusSeconds(7200), null);
        AvailabilitySlot slotIniciado = new AvailabilitySlot(
                50L, "slot-1", 100L, 200L, 300L, NOW.minusSeconds(600), NOW.plusSeconds(1200), "PRESENCIAL", "OCUPADO"
        );
        User profUsuario = new User(2L, "prof-usr-1", "medico@test.com", "hash", "ACTIVO", 0, null, NOW, false);

        when(userRepository.findByPublicId("prof-usr-1")).thenReturn(Optional.of(profUsuario));
        when(appointmentRepository.findEntityByPublicId(citaPubId)).thenReturn(Optional.of(cita));
        when(availabilitySlotRepository.findById(50L)).thenReturn(Optional.of(slotIniciado));
        when(professionalRepository.findByUserId(2L)).thenReturn(Optional.of(profesionalMock));
        when(attentionRepository.existsByAppointmentId(10L)).thenReturn(true);

        assertThatThrownBy(() -> appointmentService.markNoShow(
                citaPubId,
                "prof-usr-1",
                List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL")),
                IP_CLIENTE
        )).isInstanceOf(OperationConflictException.class)
                .hasMessageContaining("No es posible marcar inasistencia para una cita que ya cuenta con una atencion clinica vinculada");
    }

    @Test
    void markNotShow_fails_statusDifferentOfScheduled_throwsOperationConflict() {
        String citaPubId = "cita-ya-atendida";
        Appointment cita = new Appointment(10L, citaPubId, 50L, 10L, null, null, "ATENDIDA", null, NOW.minusSeconds(7200), null);
        AvailabilitySlot slotIniciado = new AvailabilitySlot(
                50L, "slot-1", 100L, 200L, 300L, NOW.minusSeconds(600), NOW.plusSeconds(1200), "PRESENCIAL", "OCUPADO"
        );
        User profUsuario = new User(2L, "prof-usr-1", "medico@test.com", "hash", "ACTIVO", 0, null, NOW, false);

        when(userRepository.findByPublicId("prof-usr-1")).thenReturn(Optional.of(profUsuario));
        when(appointmentRepository.findEntityByPublicId(citaPubId)).thenReturn(Optional.of(cita));
        when(availabilitySlotRepository.findById(50L)).thenReturn(Optional.of(slotIniciado));
        when(professionalRepository.findByUserId(2L)).thenReturn(Optional.of(profesionalMock));

        assertThatThrownBy(() -> appointmentService.markNoShow(
                citaPubId,
                "prof-usr-1",
                List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL")),
                IP_CLIENTE
        )).isInstanceOf(OperationConflictException.class)
                .hasMessageContaining("Solo es posible marcar inasistencia para citas en estado PROGRAMADA");
    }

    @Test
    void markNotShow_fails_otherProfessionalNotDuenio_throwsUnauthorizedAccess() {
        String citaPubId = "cita-otro-medico";
        Appointment cita = new Appointment(10L, citaPubId, 50L, 10L, null, null, "PROGRAMADA", null, NOW.minusSeconds(7200), null);
        AvailabilitySlot slotMedicoA = new AvailabilitySlot(
                50L, "slot-1", 100L, 200L, 300L, NOW.minusSeconds(600), NOW.plusSeconds(1200), "PRESENCIAL", "OCUPADO"
        );
        User profBUsuario = new User(3L, "prof-usr-b", "otromedico@test.com", "hash", "ACTIVO", 0, null, NOW, false);
        Professional profB = new Professional(
                101L, 3L, "prof-uuid-b", 300L, "RM-99999", "Gregory", "House Jr", NOW, null
        );

        when(userRepository.findByPublicId("prof-usr-b")).thenReturn(Optional.of(profBUsuario));
        when(appointmentRepository.findEntityByPublicId(citaPubId)).thenReturn(Optional.of(cita));
        when(availabilitySlotRepository.findById(50L)).thenReturn(Optional.of(slotMedicoA));
        when(professionalRepository.findByUserId(3L)).thenReturn(Optional.of(profB));

        assertThatThrownBy(() -> appointmentService.markNoShow(
                citaPubId,
                "prof-usr-b",
                List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL")),
                IP_CLIENTE
        )).isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("El profesional no tiene autorizacion para marcar inasistencia de citas de otro colega");
    }

    @Test
    void markNotShow_fails_patientRole_throwsUnauthorizedAccess() {
        String citaPubId = "cita-pac-intento";
        Appointment cita = new Appointment(10L, citaPubId, 50L, 10L, null, null, "PROGRAMADA", null, NOW.minusSeconds(7200), null);
        User pacUsuario = new User(1L, "pac-usr-1", "paciente@test.com", "hash", "ACTIVO", 0, null, NOW, false);

        when(userRepository.findByPublicId("pac-usr-1")).thenReturn(Optional.of(pacUsuario));
        when(appointmentRepository.findEntityByPublicId(citaPubId)).thenReturn(Optional.of(cita));

        assertThatThrownBy(() -> appointmentService.markNoShow(
                citaPubId,
                "pac-usr-1",
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")),
                IP_CLIENTE
        )).isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("No tiene autorizacion para marcar inasistencia de citas");
    }

    @Test
    void markNotShow_fails_appointmentNotFound_throwsResourceNotFound() {
        User profUsuario = new User(2L, "prof-usr-1", "medico@test.com", "hash", "ACTIVO", 0, null, NOW, false);
        when(userRepository.findByPublicId("prof-usr-1")).thenReturn(Optional.of(profUsuario));
        when(appointmentRepository.findEntityByPublicId("cita-inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.markNoShow(
                "cita-inexistente",
                "prof-usr-1",
                List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL")),
                IP_CLIENTE
        )).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cita no encontrada.");
    }
}
