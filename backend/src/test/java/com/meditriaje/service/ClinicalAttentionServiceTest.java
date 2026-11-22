package com.meditriaje.service;

import com.meditriaje.dto.clinical.ClinicalAttentionResponse;
import com.meditriaje.dto.clinical.CloseAttentionRequest;
import com.meditriaje.dto.clinical.CreateAmendmentRequest;
import com.meditriaje.dto.clinical.AmendmentResponse;
import com.meditriaje.dto.clinical.StartAttentionRequest;
import com.meditriaje.dto.clinical.VitalSignsDto;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.ClinicalAttention;
import com.meditriaje.model.Appointment;
import com.meditriaje.model.Cie10Diagnosis;
import com.meditriaje.model.AvailabilitySlot;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.Patient;
import com.meditriaje.model.Professional;
import com.meditriaje.model.VitalSign;
import com.meditriaje.model.User;
import com.meditriaje.repository.ClinicalAttentionRepository;
import com.meditriaje.repository.AppointmentRepository;
import com.meditriaje.repository.Cie10DiagnosisRepository;
import com.meditriaje.repository.AvailabilitySlotRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.ProfessionalRepository;
import com.meditriaje.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para {@link ClinicalAttentionService}.
 * Verifica el ciclo de vida de atención médica, inmutabilidad, reglas asistenciales y auditoría (HU-07, HU-09).
 */
@ExtendWith(MockitoExtension.class)
class ClinicalAttentionServiceTest {

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private static final Instant AHORA = Instant.parse("2026-10-03T18:30:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private ProfessionalRepository professionalRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AvailabilitySlotRepository availabilitySlotRepository;

    @Mock
    private ClinicalAttentionRepository attentionRepository;

    @Mock
    private Cie10DiagnosisRepository cie10DiagnosisRepository;

    @Mock
    private ClinicalAccessService clinicalAccessService;

    @Mock
    private AuditService auditService;

    private ClinicalAttentionService clinicalAttentionService;

    private final Clock clock = Clock.fixed(AHORA, BOGOTA_ZONE);

    private final User usuarioMedico = new User(10L, "usr-med-1", "medico@test.com", "hash", "ACTIVO", 0, null, AHORA, false);
    private final Professional profesionalMedico = new Professional(50L, 10L, "prof-1", 1L, "RM-12345", "Carlos", "Gomez", AHORA, null);
    private final Appointment citaValida = new Appointment(100L, "cita-uuid-1", 200L, 300L, null, null, "PROGRAMADA", null, AHORA, null);
    private final AvailabilitySlot slotValido = new AvailabilitySlot(200L, "slot-uuid-1", 50L, 1L, 1L, AHORA, AHORA.plusSeconds(1800), "PRESENCIAL", "OCUPADO");

    @BeforeEach
    void setUp() {
        clinicalAttentionService = new ClinicalAttentionService(
                userRepository,
                patientRepository,
                professionalRepository,
                appointmentRepository,
                availabilitySlotRepository,
                attentionRepository,
                cie10DiagnosisRepository,
                clinicalAccessService,
                auditService,
                clock
        );
    }

    @Test
    @DisplayName("iniciarAtencion - éxito: crea atención en estado ABIERTA, mantiene cita en PROGRAMADA y audita (D2)")
    void startAttention_success() {
        StartAttentionRequest request = new StartAttentionRequest("cita-uuid-1");

        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(appointmentRepository.findEntityByPublicId("cita-uuid-1")).thenReturn(Optional.of(citaValida));
        when(availabilitySlotRepository.findById(200L)).thenReturn(Optional.of(slotValido));
        when(attentionRepository.existsByAppointmentId(100L)).thenReturn(false);
        when(attentionRepository.create(any(ClinicalAttention.class))).thenReturn(1L);

        ClinicalAttentionResponse mockResponse = new ClinicalAttentionResponse(
                "atencion-uuid-1", "cita-uuid-1", "pac-uuid-1", "Juan Perez",
                "prof-1", "Carlos Gomez", "Medicina General",
                "ABIERTA", AHORA, null, null, null, null, null, null, null
        );
        when(attentionRepository.findDetailByPublicId(any())).thenReturn(Optional.of(mockResponse));

        ClinicalAttentionResponse response = clinicalAttentionService.startAttention(request, "usr-med-1", "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("ABIERTA");

        // Verifica que la cita NO cambia de estado al iniciar atención (Decisión D2)
        verify(appointmentRepository, never()).updateStatus(anyLong(), anyString(), any());

        // Verifica guardado de atención
        ArgumentCaptor<ClinicalAttention> atencionCaptor = ArgumentCaptor.forClass(ClinicalAttention.class);
        verify(attentionRepository).create(atencionCaptor.capture());
        ClinicalAttention atencionGuardada = atencionCaptor.getValue();
        assertThat(atencionGuardada.appointmentId()).isEqualTo(100L);
        assertThat(atencionGuardada.patientId()).isEqualTo(300L);
        assertThat(atencionGuardada.professionalId()).isEqualTo(50L);
        assertThat(atencionGuardada.status()).isEqualTo("ABIERTA");

        // Verifica auditoría sin datos clínicos
        ArgumentCaptor<AuditEvent> eventoCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).audit(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().action()).isEqualTo(AuditableAction.CREACION_ATENCION);
        assertThat(eventoCaptor.getValue().resourceType()).isEqualTo("ATENCION");
    }

    @Test
    @DisplayName("iniciarAtencion - rechaza si el usuario no es profesional asistencial (403)")
    void startAttention_rejectsWhenUserNotProfessional() {
        StartAttentionRequest request = new StartAttentionRequest("cita-uuid-1");
        when(userRepository.findByPublicId("usr-pac-1")).thenReturn(Optional.of(usuarioMedico));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clinicalAttentionService.startAttention(request, "usr-pac-1", "127.0.0.1"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Usuario no registrado como profesional asistencial.");
    }

    @Test
    @DisplayName("iniciarAtencion - rechaza si el profesional no está asignado a la cita (403)")
    void startAttention_rejectsWhenProfessionalDoesNotMatchAppointment() {
        StartAttentionRequest request = new StartAttentionRequest("cita-uuid-1");
        AvailabilitySlot slotOtroMedico = new AvailabilitySlot(200L, "slot-uuid-1", 999L, 1L, 1L, AHORA, AHORA.plusSeconds(1800), "PRESENCIAL", "OCUPADO");

        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(appointmentRepository.findEntityByPublicId("cita-uuid-1")).thenReturn(Optional.of(citaValida));
        when(availabilitySlotRepository.findById(200L)).thenReturn(Optional.of(slotOtroMedico));

        assertThatThrownBy(() -> clinicalAttentionService.startAttention(request, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("El profesional no esta asignado a esta cita medica.");
    }

    @Test
    @DisplayName("iniciarAtencion - rechaza si la cita no está en estado válida (400)")
    void startAttention_rejectsWhenAppointmentInvalidStatus() {
        StartAttentionRequest request = new StartAttentionRequest("cita-uuid-1");
        Appointment citaCancelada = new Appointment(100L, "cita-uuid-1", 200L, 300L, null, null, "CANCELADA", null, AHORA, null);

        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(appointmentRepository.findEntityByPublicId("cita-uuid-1")).thenReturn(Optional.of(citaCancelada));
        when(availabilitySlotRepository.findById(200L)).thenReturn(Optional.of(slotValido));

        assertThatThrownBy(() -> clinicalAttentionService.startAttention(request, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("La cita no se encuentra en un estado valido");
    }

    @Test
    @DisplayName("iniciarAtencion - rechaza si ya existe una atención para la cita (400)")
    void startAttention_rejectsWhenAttentionExistsForAppointment() {
        StartAttentionRequest request = new StartAttentionRequest("cita-uuid-1");

        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(appointmentRepository.findEntityByPublicId("cita-uuid-1")).thenReturn(Optional.of(citaValida));
        when(availabilitySlotRepository.findById(200L)).thenReturn(Optional.of(slotValido));
        when(attentionRepository.existsByAppointmentId(100L)).thenReturn(true);

        assertThatThrownBy(() -> clinicalAttentionService.startAttention(request, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Ya existe una atencion clinica registrada");
    }

    @Test
    @DisplayName("cerrarAtencion - éxito con diagnóstico CIE-10 y signos vitales: cierra atención, cita pasa a ATENDIDA y audita")
    void closeAttention_success_withSignsVitals() {
        String atencionPublicId = "atencion-uuid-1";
        VitalSignsDto signos = new VitalSignsDto(120, 80, 75, 16, new BigDecimal("36.5"), 98, new BigDecimal("70.5"), new BigDecimal("172.0"));
        CloseAttentionRequest request = new CloseAttentionRequest("J00", "Cefalea y congestion nasal", "Cuadro viral agudo", "Reposo e hidratacion oral", signos);

        ClinicalAttention atencionAbierta = new ClinicalAttention(1L, atencionPublicId, 100L, 300L, 50L, "ABIERTA");
        Cie10Diagnosis cie10 = new Cie10Diagnosis(15L, "J00", "Rinofaringitis aguda (resfriado comun)", "ACTIVO");
        Appointment citaProgramada = new Appointment(100L, "cita-uuid-1", 200L, 300L, null, null, "PROGRAMADA", null, AHORA, null);

        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(attentionRepository.findEntityByPublicId(atencionPublicId)).thenReturn(Optional.of(atencionAbierta));
        when(cie10DiagnosisRepository.findByCode("J00")).thenReturn(Optional.of(cie10));
        when(appointmentRepository.findEntityById(100L)).thenReturn(Optional.of(citaProgramada));

        ClinicalAttentionResponse mockResponse = new ClinicalAttentionResponse(
                atencionPublicId, "cita-uuid-1", "pac-uuid-1", "Juan Perez",
                "prof-1", "Carlos Gomez", "Medicina General",
                "CERRADA", AHORA.minusSeconds(1200), AHORA, "J00", "Rinofaringitis aguda (resfriado comun)",
                "Cefalea y congestion nasal", "Cuadro viral agudo", "Reposo e hidratacion oral", signos
        );
        when(attentionRepository.findDetailByPublicId(atencionPublicId)).thenReturn(Optional.of(mockResponse));

        ClinicalAttentionResponse response = clinicalAttentionService.closeAttention(atencionPublicId, request, "usr-med-1", "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("CERRADA");

        // Verifica inserción de signos vitales
        ArgumentCaptor<VitalSign> svCaptor = ArgumentCaptor.forClass(VitalSign.class);
        verify(attentionRepository).saveVitalSigns(svCaptor.capture());
        VitalSign sv = svCaptor.getValue();
        assertThat(sv.attentionId()).isEqualTo(1L);
        assertThat(sv.systolicPressure()).isEqualTo(120);
        assertThat(sv.diastolicPressure()).isEqualTo(80);

        // Verifica cierre de atención en BD
        verify(attentionRepository).closeAttention(1L, 15L, "Cefalea y congestion nasal", "Cuadro viral agudo", "Reposo e hidratacion oral", AHORA);

        // Verifica transición de cita a ATENDIDA desde PROGRAMADA
        verify(appointmentRepository).updateStatus(100L, "ATENDIDA", null);

        // Verifica auditoría sin datos clínicos
        ArgumentCaptor<AuditEvent> eventoCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).audit(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().action()).isEqualTo(AuditableAction.CIERRE_ATENCION);
        assertThat(eventoCaptor.getValue().resourceType()).isEqualTo("ATENCION");
        assertThat(eventoCaptor.getValue().resourcePublicId()).isEqualTo(atencionPublicId);
    }

    @Test
    @DisplayName("cerrarAtencion - éxito con cita legacy en CONFIRMADA: transiciona válidamente a ATENDIDA (D2)")
    void closeAttention_success_appointmentLegacyConfirmed() {
        String atencionPublicId = "atencion-uuid-legacy";
        CloseAttentionRequest request = new CloseAttentionRequest("J00", "Motivo", "Evolucion", "Indicaciones", null);
        ClinicalAttention atencionAbierta = new ClinicalAttention(2L, atencionPublicId, 101L, 300L, 50L, "ABIERTA");
        Cie10Diagnosis cie10 = new Cie10Diagnosis(15L, "J00", "Rinofaringitis aguda", "ACTIVO");
        Appointment citaConfirmadaLegacy = new Appointment(101L, "cita-uuid-legacy", 201L, 300L, null, null, "CONFIRMADA", null, AHORA, null);

        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(attentionRepository.findEntityByPublicId(atencionPublicId)).thenReturn(Optional.of(atencionAbierta));
        when(cie10DiagnosisRepository.findByCode("J00")).thenReturn(Optional.of(cie10));
        when(appointmentRepository.findEntityById(101L)).thenReturn(Optional.of(citaConfirmadaLegacy));

        ClinicalAttentionResponse mockResponse = new ClinicalAttentionResponse(
                atencionPublicId, "cita-uuid-legacy", "pac-uuid-1", "Juan Perez",
                "prof-1", "Carlos Gomez", "Medicina General",
                "CERRADA", AHORA.minusSeconds(600), AHORA, "J00", "Rinofaringitis aguda",
                "Motivo", "Evolucion", "Indicaciones", null
        );
        when(attentionRepository.findDetailByPublicId(atencionPublicId)).thenReturn(Optional.of(mockResponse));

        ClinicalAttentionResponse response = clinicalAttentionService.closeAttention(atencionPublicId, request, "usr-med-1", "127.0.0.1");

        assertThat(response).isNotNull();
        verify(appointmentRepository).updateStatus(101L, "ATENDIDA", null);
    }

    @Test
    @DisplayName("cerrarAtencion - rechaza si la atención ya está CERRADA (inmutabilidad ADR-008)")
    void closeAttention_rejectsWhenAttentionAlreadyClosed() {
        String atencionPublicId = "atencion-uuid-cerrada";
        CloseAttentionRequest request = new CloseAttentionRequest("J00", "Motivo", "Evolucion", "Indicaciones", null);
        ClinicalAttention atencionCerrada = new ClinicalAttention(1L, atencionPublicId, 100L, 300L, 50L, "CERRADA");

        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(attentionRepository.findEntityByPublicId(atencionPublicId)).thenReturn(Optional.of(atencionCerrada));

        assertThatThrownBy(() -> clinicalAttentionService.closeAttention(atencionPublicId, request, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("ya se encuentra CERRADA y es inmutable");
    }

    @Test
    @DisplayName("cerrarAtencion - rechaza si el profesional autenticado no es el asignado a la atención (403)")
    void closeAttention_rejectsWhenProfessionalNotAssigned() {
        String atencionPublicId = "atencion-uuid-1";
        CloseAttentionRequest request = new CloseAttentionRequest("J00", "Motivo", "Evolucion", "Indicaciones", null);
        ClinicalAttention atencionOtroMedico = new ClinicalAttention(1L, atencionPublicId, 100L, 300L, 999L, "ABIERTA");

        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(attentionRepository.findEntityByPublicId(atencionPublicId)).thenReturn(Optional.of(atencionOtroMedico));

        assertThatThrownBy(() -> clinicalAttentionService.closeAttention(atencionPublicId, request, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("No tiene autorizacion para cerrar la atencion de otro profesional.");
    }

    @Test
    @DisplayName("cerrarAtencion - rechaza si el código CIE-10 no existe o está inactivo (400)")
    void closeAttention_rejectsWhenDiagnosisInvalidOrInactive() {
        String atencionPublicId = "atencion-uuid-1";
        CloseAttentionRequest request = new CloseAttentionRequest("COD_INEXISTENTE", "Motivo", "Evolucion", "Indicaciones", null);
        ClinicalAttention atencionAbierta = new ClinicalAttention(1L, atencionPublicId, 100L, 300L, 50L, "ABIERTA");

        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(attentionRepository.findEntityByPublicId(atencionPublicId)).thenReturn(Optional.of(atencionAbierta));
        when(cie10DiagnosisRepository.findByCode("COD_INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clinicalAttentionService.closeAttention(atencionPublicId, request, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Codigo de diagnostico CIE-10 no encontrado");

        // Caso inactivo
        CloseAttentionRequest reqInactivo = new CloseAttentionRequest("INACTIVO", "Motivo", "Evolucion", "Indicaciones", null);
        Cie10Diagnosis diagInactivo = new Cie10Diagnosis(99L, "INACTIVO", "Inactivo", "INACTIVO");
        when(cie10DiagnosisRepository.findByCode("INACTIVO")).thenReturn(Optional.of(diagInactivo));

        assertThatThrownBy(() -> clinicalAttentionService.closeAttention(atencionPublicId, reqInactivo, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("se encuentra inactivo");
    }

    @Test
    @DisplayName("cerrarAtencion - rechaza si presión arterial sistólica es menor o igual a diastólica (400)")
    void closeAttention_rejectsWhenSystolicNotGreaterThanDiastolic() {
        String atencionPublicId = "atencion-uuid-1";
        VitalSignsDto signosInvalidos = new VitalSignsDto(80, 120, null, null, null, null, null, null);
        CloseAttentionRequest request = new CloseAttentionRequest("J00", "Motivo", "Evolucion", "Indicaciones", signosInvalidos);
        ClinicalAttention atencionAbierta = new ClinicalAttention(1L, atencionPublicId, 100L, 300L, 50L, "ABIERTA");
        Cie10Diagnosis cie10 = new Cie10Diagnosis(15L, "J00", "Rinofaringitis", "ACTIVO");

        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(attentionRepository.findEntityByPublicId(atencionPublicId)).thenReturn(Optional.of(atencionAbierta));
        when(cie10DiagnosisRepository.findByCode("J00")).thenReturn(Optional.of(cie10));

        assertThatThrownBy(() -> clinicalAttentionService.closeAttention(atencionPublicId, request, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("La presion arterial sistolica debe ser estrictamente mayor");

        verify(attentionRepository, never()).saveVitalSigns(any());
        verify(attentionRepository, never()).closeAttention(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("obtenerPorPublicId - valida relación asistencial, audita consulta y retorna detalle")
    void getByPublicId_success() {
        String atencionPublicId = "atencion-uuid-1";
        ClinicalAttention atencion = new ClinicalAttention(1L, atencionPublicId, 100L, 300L, 50L, "CERRADA");
        ClinicalAttentionResponse mockResponse = new ClinicalAttentionResponse(
                atencionPublicId, "cita-uuid-1", "pac-uuid-1", "Juan Perez",
                "prof-1", "Carlos Gomez", "Medicina General",
                "CERRADA", AHORA.minusSeconds(1000), AHORA, "J00", "Rinofaringitis aguda",
                "Motivo", "Evolucion", "Indicaciones", null
        );

        when(attentionRepository.findEntityByPublicId(atencionPublicId)).thenReturn(Optional.of(atencion));
        when(userRepository.findByPublicId("usr-pac-1")).thenReturn(Optional.of(usuarioMedico));
        when(attentionRepository.findDetailByPublicId(atencionPublicId)).thenReturn(Optional.of(mockResponse));

        ClinicalAttentionResponse response = clinicalAttentionService.getByPublicId(
                atencionPublicId,
                "usr-pac-1",
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")),
                "127.0.0.1"
        );

        assertThat(response).isNotNull();
        assertThat(response.publicId()).isEqualTo(atencionPublicId);

        // Valida que invocó el servicio centralizado de acceso clínico (ADR-007)
        verify(clinicalAccessService).validateClinicalHistoryAccess(
                eq("usr-pac-1"),
                eq(300L),
                eq(List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")))
        );

        // Valida auditoría inmutable de consulta de historia clínica
        ArgumentCaptor<AuditEvent> eventoCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).audit(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().action()).isEqualTo(AuditableAction.CONSULTA_HISTORIA);
        assertThat(eventoCaptor.getValue().resourceType()).isEqualTo("ATENCION");
        assertThat(eventoCaptor.getValue().resourcePublicId()).isEqualTo(atencionPublicId);
    }

    @Test
    @DisplayName("crearEnmienda - éxito con el mismo profesional autor: registra enmienda inmutable y audita")
    void createAmendment_success_sameAuthorProfessional() {
        String atencionPublicId = "atencion-uuid-1";
        CreateAmendmentRequest request = new CreateAmendmentRequest("Aclaracion de dosis", "Se precisa que la dosis es cada 12 horas.");
        ClinicalAttention atencionCerrada = new ClinicalAttention(1L, atencionPublicId, 100L, 300L, 50L, "CERRADA");

        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(attentionRepository.findEntityByPublicId(atencionPublicId)).thenReturn(Optional.of(atencionCerrada));
        when(attentionRepository.createAmendment(eq(1L), eq(50L), eq("Aclaracion de dosis"), anyString(), eq(AHORA))).thenReturn(10L);

        AmendmentResponse response = clinicalAttentionService.createAmendment(atencionPublicId, request, "usr-med-1", "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.professionalPublicId()).isEqualTo("prof-1");
        assertThat(response.reason()).isEqualTo("Aclaracion de dosis");
        assertThat(response.content()).isEqualTo("Se precisa que la dosis es cada 12 horas.");
        assertThat(response.amendedAt()).isEqualTo(AHORA);

        // Verifica guardado
        verify(attentionRepository).createAmendment(1L, 50L, "Aclaracion de dosis", "Se precisa que la dosis es cada 12 horas.", AHORA);

        // Verifica auditoría inmutable
        ArgumentCaptor<AuditEvent> eventoCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).audit(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().action()).isEqualTo(AuditableAction.ENMIENDA_ATENCION);
        assertThat(eventoCaptor.getValue().resourceType()).isEqualTo("ATENCION");
        assertThat(eventoCaptor.getValue().resourcePublicId()).isEqualTo(atencionPublicId);
    }

    @Test
    @DisplayName("crearEnmienda - éxito con otro profesional que tiene relación asistencial activa")
    void createAmendment_success_otherProfessionalWithRelationshipCare() {
        String atencionPublicId = "atencion-uuid-1";
        CreateAmendmentRequest request = new CreateAmendmentRequest("Seguimiento", "Paciente evaluado en control posterior.");
        ClinicalAttention atencionCerrada = new ClinicalAttention(1L, atencionPublicId, 100L, 300L, 999L, "CERRADA"); // Otro médico fue el autor

        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(attentionRepository.findEntityByPublicId(atencionPublicId)).thenReturn(Optional.of(atencionCerrada));
        when(attentionRepository.createAmendment(eq(1L), eq(50L), anyString(), anyString(), eq(AHORA))).thenReturn(11L);

        AmendmentResponse response = clinicalAttentionService.createAmendment(atencionPublicId, request, "usr-med-1", "127.0.0.1");

        assertThat(response).isNotNull();
        // Verifica que se validó la relación asistencial del nuevo médico con el paciente
        verify(clinicalAccessService).validateCareRelationship(50L, 300L);
        verify(attentionRepository).createAmendment(1L, 50L, "Seguimiento", "Paciente evaluado en control posterior.", AHORA);
    }

    @Test
    @DisplayName("crearEnmienda - rechaza si la atención está ABIERTA (inmutabilidad ADR-008)")
    void createAmendment_rejectsWhenAttentionIsOpen() {
        String atencionPublicId = "atencion-uuid-1";
        CreateAmendmentRequest request = new CreateAmendmentRequest("Aclaracion", "Contenido");
        ClinicalAttention atencionAbierta = new ClinicalAttention(1L, atencionPublicId, 100L, 300L, 50L, "ABIERTA");

        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(attentionRepository.findEntityByPublicId(atencionPublicId)).thenReturn(Optional.of(atencionAbierta));

        assertThatThrownBy(() -> clinicalAttentionService.createAmendment(atencionPublicId, request, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Solo es posible registrar enmiendas sobre atenciones clinicas CERRADAS.");

        verify(attentionRepository, never()).createAmendment(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("crearEnmienda - rechaza si el usuario autenticado no es profesional asistencial (403)")
    void createAmendment_rejectsWhenUserNotProfessional() {
        String atencionPublicId = "atencion-uuid-1";
        CreateAmendmentRequest request = new CreateAmendmentRequest("Aclaracion", "Contenido");

        when(userRepository.findByPublicId("usr-pac-1")).thenReturn(Optional.of(usuarioMedico));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clinicalAttentionService.createAmendment(atencionPublicId, request, "usr-pac-1", "127.0.0.1"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Usuario no registrado como profesional asistencial.");
    }

    @Test
    @DisplayName("crearEnmienda - rechaza si el profesional ajeno no tiene relación asistencial activa (403)")
    void createAmendment_rejectsWhenOtherProfessionalHasNotCareRelationship() {
        String atencionPublicId = "atencion-uuid-1";
        CreateAmendmentRequest request = new CreateAmendmentRequest("Aclaracion", "Contenido");
        ClinicalAttention atencionCerrada = new ClinicalAttention(1L, atencionPublicId, 100L, 300L, 999L, "CERRADA");

        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMedico));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.of(profesionalMedico));
        when(attentionRepository.findEntityByPublicId(atencionPublicId)).thenReturn(Optional.of(atencionCerrada));

        org.mockito.Mockito.doThrow(new UnauthorizedAccessException("El profesional no cuenta con una relacion asistencial activa con el paciente."))
                .when(clinicalAccessService).validateCareRelationship(50L, 300L);

        assertThatThrownBy(() -> clinicalAttentionService.createAmendment(atencionPublicId, request, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("El profesional no cuenta con una relacion asistencial activa");

        verify(attentionRepository, never()).createAmendment(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("obtenerMiHistoriaClinica - éxito: paciente consulta su propio historial, retorna paginado y audita")
    void getMyHistoryClinical_success() {
        String usuarioPublicId = "usr-pac-1";
        Long usuarioId = 20L;
        Long pacienteId = 300L;
        String pacientePublicId = "pac-uuid-1";

        User usuarioPaciente = new User(usuarioId, usuarioPublicId, "paciente@test.com", "hash", "ACTIVO", 0, null, AHORA, false);
        Patient paciente = new Patient(pacienteId, usuarioId, pacientePublicId, "CC", "10101010", "Maria", "Lopez", java.time.LocalDate.of(1995, 5, 20), "3001234567");

        ClinicalAttentionResponse atencion = new ClinicalAttentionResponse(
                "atencion-uuid-1",
                "cita-uuid-1",
                pacientePublicId,
                "Maria Lopez",
                "prof-1",
                "Carlos Gomez",
                "Medicina General",
                "CERRADA",
                AHORA.minusSeconds(3600),
                AHORA,
                "J00",
                "Rinofaringitis aguda",
                "Congestion nasal",
                "Paciente estable",
                "Reposo e hidratacion",
                null
        );

        when(userRepository.findByPublicId(usuarioPublicId)).thenReturn(Optional.of(usuarioPaciente));
        when(patientRepository.findByUserId(usuarioId)).thenReturn(Optional.of(paciente));
        when(attentionRepository.listPatientHistory(pacienteId, 0, 10)).thenReturn(List.of(atencion));
        when(attentionRepository.countPatientHistory(pacienteId)).thenReturn(1);

        PaginatedResponse<ClinicalAttentionResponse> respuesta = clinicalAttentionService.getMyClinicalHistory(usuarioPublicId, 0, 10, "192.168.1.50");

        assertThat(respuesta).isNotNull();
        assertThat(respuesta.content()).hasSize(1);
        assertThat(respuesta.content().get(0).publicId()).isEqualTo("atencion-uuid-1");
        assertThat(respuesta.totalElements()).isEqualTo(1L);
        assertThat(respuesta.page()).isEqualTo(0);
        assertThat(respuesta.size()).isEqualTo(10);

        ArgumentCaptor<AuditEvent> captorAuditoria = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).audit(captorAuditoria.capture());
        AuditEvent evento = captorAuditoria.getValue();
        assertThat(evento.userId()).isEqualTo(usuarioId);
        assertThat(evento.action()).isEqualTo(AuditableAction.CONSULTA_HISTORIA);
        assertThat(evento.resourceType()).isEqualTo("HISTORIA_CLINICA");
        assertThat(evento.resourcePublicId()).isEqualTo(pacientePublicId);
        assertThat(evento.sourceIp()).isEqualTo("192.168.1.50");
    }

    @Test
    @DisplayName("obtenerMiHistoriaClinica - rechaza si usuario no está registrado como paciente (403)")
    void getMyHistoryClinical_rejectsWhenUserNotPatient() {
        String usuarioPublicId = "usr-med-1";
        when(userRepository.findByPublicId(usuarioPublicId)).thenReturn(Optional.of(usuarioMedico));
        when(patientRepository.findByUserId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clinicalAttentionService.getMyClinicalHistory(usuarioPublicId, 0, 10, "127.0.0.1"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Usuario no registrado como paciente.");

        verify(attentionRepository, never()).listPatientHistory(any(), eq(0), eq(10));
    }

    @Test
    @DisplayName("obtenerMiHistoriaClinica - rechaza si usuario autenticado no existe en BD (404)")
    void getMyHistoryClinical_rejectsWhenUserNotFound() {
        String usuarioPublicId = "usr-inexistente";
        when(userRepository.findByPublicId(usuarioPublicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clinicalAttentionService.getMyClinicalHistory(usuarioPublicId, 0, 10, "127.0.0.1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Usuario autenticado no encontrado.");
    }

    @Test
    @DisplayName("obtenerHistoriaClinicaPaciente - médico autorizado por relación o break-glass consulta historia y audita")
    void getHistoryClinicalPatient_success() {
        String pacientePublicId = "pac-100";
        String usuarioMedPublicId = "usr-med-1";
        Long pacienteId = 300L;
        Patient paciente = new Patient(
                pacienteId, 30L, pacientePublicId, "CC", "12345678", "Carlos", "Sanchez",
                LocalDate.of(1985, 5, 20), "3001234567", AHORA, AHORA
        );

        when(userRepository.findByPublicId(usuarioMedPublicId)).thenReturn(Optional.of(usuarioMedico));
        when(patientRepository.findByPublicId(pacientePublicId)).thenReturn(Optional.of(paciente));

        ClinicalAttentionResponse atencion = new ClinicalAttentionResponse(
                "atn-uuid-1", "cita-uuid-1", pacientePublicId, "Carlos Sanchez",
                "prof-1", "Carlos Gomez", "Medicina General",
                "CERRADA", AHORA.minusSeconds(1000), AHORA, "J00", "Rinofaringitis aguda",
                "Motivo", "Evolucion", "Indicaciones", null
        );

        when(attentionRepository.listPatientHistory(pacienteId, 0, 10)).thenReturn(List.of(atencion));
        when(attentionRepository.countPatientHistory(pacienteId)).thenReturn(1);

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"));

        PaginatedResponse<ClinicalAttentionResponse> response = clinicalAttentionService.getPatientClinicalHistory(
                pacientePublicId,
                usuarioMedPublicId,
                authorities,
                0,
                10,
                "192.168.1.50"
        );

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content().get(0).publicId()).isEqualTo("atn-uuid-1");

        // Verifica autorización centralizada (ADR-007, ADR-017)
        verify(clinicalAccessService).validateClinicalHistoryAccess(usuarioMedPublicId, pacientePublicId, authorities);

        // Verifica auditoría
        ArgumentCaptor<AuditEvent> captorAuditoria = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).audit(captorAuditoria.capture());
        AuditEvent evento = captorAuditoria.getValue();
        assertThat(evento.action()).isEqualTo(AuditableAction.CONSULTA_HISTORIA);
        assertThat(evento.resourceType()).isEqualTo("HISTORIA_CLINICA");
        assertThat(evento.resourcePublicId()).isEqualTo(pacientePublicId);
    }

    @Test
    @DisplayName("obtenerHistoriaClinicaPaciente - rechaza si acceso clínico no autoriza al médico")
    void getHistoryClinicalPatient_withoutAuth_throwsException() {
        String pacientePublicId = "pac-100";
        String usuarioMedPublicId = "usr-med-1";
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"));

        org.mockito.Mockito.doThrow(new UnauthorizedAccessException("No existe una relacion asistencial activa con el paciente."))
                .when(clinicalAccessService).validateClinicalHistoryAccess(usuarioMedPublicId, pacientePublicId, authorities);

        assertThatThrownBy(() -> clinicalAttentionService.getPatientClinicalHistory(
                pacientePublicId, usuarioMedPublicId, authorities, 0, 10, "127.0.0.1"
        )).isInstanceOf(UnauthorizedAccessException.class)
          .hasMessageContaining("No existe una relacion asistencial activa");

        verify(attentionRepository, never()).listPatientHistory(any(), anyInt(), anyInt());
    }
}
