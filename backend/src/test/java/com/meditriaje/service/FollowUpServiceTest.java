package com.meditriaje.service;

import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.dto.followup.CreateFollowUpRequest;
import com.meditriaje.dto.followup.ReportProgressRequest;
import com.meditriaje.dto.followup.FollowUpResponse;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.ClinicalAttention;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.Patient;
import com.meditriaje.model.Professional;
import com.meditriaje.model.PostAttentionFollowUp;
import com.meditriaje.model.User;
import com.meditriaje.repository.ClinicalAttentionRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.ProfessionalRepository;
import com.meditriaje.repository.FollowUpRepository;
import com.meditriaje.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowUpServiceTest {

    @Mock
    private FollowUpRepository followUpRepository;

    @Mock
    private ClinicalAttentionRepository attentionRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private ProfessionalRepository professionalRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClinicalAccessService clinicalAccessService;

    @Mock
    private AuditService auditService;

    @Mock
    private AppointmentNotificationService notificationService;

    @InjectMocks
    private FollowUpService followUpService;

    private User usuarioProf;
    private Professional profesional;
    private User usuarioPac;
    private Patient paciente;
    private ClinicalAttention atencionCerrada;

    @BeforeEach
    void setUp() {
        usuarioProf = new User(1L, "usr-prof-1", "medico@example.com", "hash", "ACTIVO", 0, null, Instant.now());
        profesional = new Professional(10L, 1L, "prof-1", 100L, "RM-123", "Laura", "Perez", Instant.now(), Instant.now());

        usuarioPac = new User(2L, "usr-pac-1", "paciente@example.com", "hash", "ACTIVO", 0, null, Instant.now());
        paciente = new Patient(20L, 2L, "pac-1", "CC", "12345678", "Carlos", "Gomez", LocalDate.of(1990, 5, 10), "3001234567", Instant.now(), Instant.now());

        atencionCerrada = new ClinicalAttention(
                50L, "at-1", 1000L, 20L, 10L, 5L,
                "Faringitis", "Evolución favorable", "Reposo",
                "CERRADA", Instant.now(), Instant.now()
        );
    }

    @Test
    @DisplayName("prescribirSeguimiento - Éxito: médico autor prescribe seguimiento, audita y notifica")
    void prescribeFollowUp_success() {
        when(userRepository.findByPublicId("usr-prof-1")).thenReturn(Optional.of(usuarioProf));
        when(professionalRepository.findByUserId(1L)).thenReturn(Optional.of(profesional));
        when(attentionRepository.findEntityByPublicId("at-1")).thenReturn(Optional.of(atencionCerrada));

        when(followUpRepository.save(any(PostAttentionFollowUp.class))).thenReturn(1L);

        FollowUpResponse resp = new FollowUpResponse(
                "seg-new-1", "at-1", "pac-1", "Carlos Gomez",
                "prof-1", "Laura Perez", "Medicina General",
                "EVOLUCION_SINTOMAS", "Monitorear fiebre",
                LocalDate.now().plusDays(2), "PENDIENTE",
                null, null, Instant.now(), Instant.now()
        );
        when(followUpRepository.findByPublicId(anyString())).thenReturn(Optional.of(resp));

        CreateFollowUpRequest request = new CreateFollowUpRequest(
                "EVOLUCION_SINTOMAS",
                "Monitorear fiebre",
                LocalDate.now().plusDays(2)
        );

        FollowUpResponse resultado = followUpService.prescribeFollowUp("at-1", request, "usr-prof-1", "127.0.0.1");

        assertThat(resultado).isNotNull();
        assertThat(resultado.type()).isEqualTo("EVOLUCION_SINTOMAS");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).audit(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditableAction.CREACION_SEGUIMIENTO);
    }

    @Test
    @DisplayName("prescribirSeguimiento - Tipo inválido arroja DatosInvalidosException")
    void prescribeFollowUp_typeInvalid() {
        when(userRepository.findByPublicId("usr-prof-1")).thenReturn(Optional.of(usuarioProf));
        when(professionalRepository.findByUserId(1L)).thenReturn(Optional.of(profesional));

        CreateFollowUpRequest request = new CreateFollowUpRequest(
                "TIPO_DESCONOCIDO",
                "Indicaciones",
                null
        );

        assertThatThrownBy(() -> followUpService.prescribeFollowUp("at-1", request, "usr-prof-1", "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Tipo de seguimiento invalido");
    }

    @Test
    @DisplayName("prescribirSeguimiento - Fecha sugerida en el pasado arroja DatosInvalidosException")
    void prescribeFollowUp_pastDate() {
        when(userRepository.findByPublicId("usr-prof-1")).thenReturn(Optional.of(usuarioProf));
        when(professionalRepository.findByUserId(1L)).thenReturn(Optional.of(profesional));

        CreateFollowUpRequest request = new CreateFollowUpRequest(
                "CONTROL_MEDICO",
                "Indicaciones",
                LocalDate.now().minusDays(1)
        );

        assertThatThrownBy(() -> followUpService.prescribeFollowUp("at-1", request, "usr-prof-1", "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("no puede ser anterior a la fecha actual");
    }

    @Test
    @DisplayName("prescribirSeguimiento - Atención no cerrada arroja DatosInvalidosException")
    void prescribeFollowUp_attentionNotClosed() {
        ClinicalAttention atencionAbierta = new ClinicalAttention(
                50L, "at-1", 1000L, 20L, 10L, null,
                "Motivo", null, null,
                "ABIERTA", null, Instant.now()
        );

        when(userRepository.findByPublicId("usr-prof-1")).thenReturn(Optional.of(usuarioProf));
        when(professionalRepository.findByUserId(1L)).thenReturn(Optional.of(profesional));
        when(attentionRepository.findEntityByPublicId("at-1")).thenReturn(Optional.of(atencionAbierta));

        CreateFollowUpRequest request = new CreateFollowUpRequest(
                "CONTROL_MEDICO",
                "Indicaciones",
                LocalDate.now().plusDays(5)
        );

        assertThatThrownBy(() -> followUpService.prescribeFollowUp("at-1", request, "usr-prof-1", "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Solo se pueden prescribir tareas de seguimiento sobre atenciones medicas cerradas");
    }

    @Test
    @DisplayName("reportarEvolucion - Éxito: paciente reporta evolución sobre seguimiento propio")
    void reportProgress_success() {
        PostAttentionFollowUp seg = new PostAttentionFollowUp(
                100L, "seg-1", 50L, 20L, 10L,
                "EVOLUCION_SINTOMAS", "Monitorear dolor",
                LocalDate.now().plusDays(2), "PENDIENTE",
                null, null, Instant.now(), Instant.now()
        );

        when(userRepository.findByPublicId("usr-pac-1")).thenReturn(Optional.of(usuarioPac));
        when(patientRepository.findByUserId(2L)).thenReturn(Optional.of(paciente));
        when(followUpRepository.findEntityByPublicId("seg-1")).thenReturn(Optional.of(seg));

        FollowUpResponse respActualizada = new FollowUpResponse(
                "seg-1", "at-1", "pac-1", "Carlos Gomez",
                "prof-1", "Laura Perez", "Medicina General",
                "EVOLUCION_SINTOMAS", "Monitorear dolor",
                LocalDate.now().plusDays(2), "COMPLETADO",
                Instant.now(), "Me encuentro sin síntomas.", Instant.now(), Instant.now()
        );
        when(followUpRepository.findByPublicId("seg-1")).thenReturn(Optional.of(respActualizada));

        ReportProgressRequest req = new ReportProgressRequest("Me encuentro sin síntomas.");

        FollowUpResponse res = followUpService.reportProgress("seg-1", req, "usr-pac-1", "127.0.0.1");

        assertThat(res.status()).isEqualTo("COMPLETADO");
        assertThat(res.patientReport()).isEqualTo("Me encuentro sin síntomas.");

        verify(followUpRepository).recordPatientReport(eq(100L), eq("Me encuentro sin síntomas."), any());
        verify(auditService).audit(any(AuditEvent.class));
    }

    @Test
    @DisplayName("reportarEvolucion - Paciente ajeno arroja AccesoNoAutorizadoException")
    void reportProgress_otherPatient() {
        PostAttentionFollowUp segDeOtro = new PostAttentionFollowUp(
                100L, "seg-1", 50L, 999L, 10L, // pacienteId distinto a 20L
                "EVOLUCION_SINTOMAS", "Monitorear dolor",
                LocalDate.now().plusDays(2), "PENDIENTE",
                null, null, Instant.now(), Instant.now()
        );

        when(userRepository.findByPublicId("usr-pac-1")).thenReturn(Optional.of(usuarioPac));
        when(patientRepository.findByUserId(2L)).thenReturn(Optional.of(paciente));
        when(followUpRepository.findEntityByPublicId("seg-1")).thenReturn(Optional.of(segDeOtro));

        ReportProgressRequest req = new ReportProgressRequest("Reporte");

        assertThatThrownBy(() -> followUpService.reportProgress("seg-1", req, "usr-pac-1", "127.0.0.1"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("No tiene autorizacion para reportar evolucion en un seguimiento ajeno");
    }

    @Test
    @DisplayName("reportarEvolucion - Seguimiento ya COMPLETADO arroja DatosInvalidosException")
    void reportProgress_alreadyCompletado() {
        PostAttentionFollowUp segCompletado = new PostAttentionFollowUp(
                100L, "seg-1", 50L, 20L, 10L,
                "EVOLUCION_SINTOMAS", "Monitorear dolor",
                LocalDate.now().plusDays(2), "COMPLETADO",
                Instant.now(), "Reporte previo", Instant.now(), Instant.now()
        );

        when(userRepository.findByPublicId("usr-pac-1")).thenReturn(Optional.of(usuarioPac));
        when(patientRepository.findByUserId(2L)).thenReturn(Optional.of(paciente));
        when(followUpRepository.findEntityByPublicId("seg-1")).thenReturn(Optional.of(segCompletado));

        ReportProgressRequest req = new ReportProgressRequest("Nuevo reporte");

        assertThatThrownBy(() -> followUpService.reportProgress("seg-1", req, "usr-pac-1", "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("ya se encuentra completado");
    }

    @Test
    @DisplayName("obtenerPorPublicId - Administrador recibe 403 Forbidden")
    void getByPublicId_admin_returns403() {
        assertThatThrownBy(() -> followUpService.getByPublicId(
                "seg-1", "usr-admin-1", List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))
        )).isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("El personal administrativo no tiene acceso a contenido clinico");
    }

    @Test
    @DisplayName("listarMisSeguimientos - Paciente autenticado obtiene respuesta paginada")
    void listMyFollowUps_success() {
        when(userRepository.findByPublicId("usr-pac-1")).thenReturn(Optional.of(usuarioPac));
        when(patientRepository.findByUserId(2L)).thenReturn(Optional.of(paciente));

        FollowUpResponse item = new FollowUpResponse(
                "seg-1", "at-1", "pac-1", "Carlos Gomez",
                "prof-1", "Laura Perez", "Medicina General",
                "CONTROL_MEDICO", "Control de TA",
                LocalDate.now().plusDays(5), "PENDIENTE",
                null, null, Instant.now(), Instant.now()
        );
        when(followUpRepository.listByPatientId(eq(20L), any(), eq(0), eq(10)))
                .thenReturn(List.of(item));
        when(followUpRepository.countByPatientId(eq(20L), any()))
                .thenReturn(1);

        PaginatedResponse<FollowUpResponse> res = followUpService.listMyFollowUps(
                "usr-pac-1", null, 0, 10
        );

        assertThat(res.content()).hasSize(1);
        assertThat(res.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("cancelarSeguimiento - Éxito: médico cancela seguimiento pendiente")
    void cancelFollowUp_success() {
        PostAttentionFollowUp seg = new PostAttentionFollowUp(
                100L, "seg-1", 50L, 20L, 10L,
                "EXAMEN_PENDIENTE", "Cuadro hemático",
                LocalDate.now().plusDays(3), "PENDIENTE",
                null, null, Instant.now(), Instant.now()
        );

        when(userRepository.findByPublicId("usr-prof-1")).thenReturn(Optional.of(usuarioProf));
        when(professionalRepository.findByUserId(1L)).thenReturn(Optional.of(profesional));
        when(followUpRepository.findEntityByPublicId("seg-1")).thenReturn(Optional.of(seg));

        FollowUpResponse cancelado = new FollowUpResponse(
                "seg-1", "at-1", "pac-1", "Carlos Gomez",
                "prof-1", "Laura Perez", "Medicina General",
                "EXAMEN_PENDIENTE", "Cuadro hemático",
                LocalDate.now().plusDays(3), "CANCELADO",
                null, null, Instant.now(), Instant.now()
        );
        when(followUpRepository.findByPublicId("seg-1")).thenReturn(Optional.of(cancelado));

        FollowUpResponse res = followUpService.cancelFollowUp(
                "seg-1", "usr-prof-1", List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"))
        );

        assertThat(res.status()).isEqualTo("CANCELADO");
        verify(followUpRepository).updateStatus(100L, "CANCELADO");
    }
}
