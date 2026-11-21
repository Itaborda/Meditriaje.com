package com.meditriaje.service;

import com.meditriaje.dto.emergency.AssignTeamRequest;
import com.meditriaje.dto.emergency.EmergencyEpisodeResponse;
import com.meditriaje.dto.emergency.EmergencyQueueItemResponse;
import com.meditriaje.dto.emergency.ReconcileIdentityRequest;
import com.meditriaje.dto.emergency.RegisterEmergencyAdmissionRequest;
import com.meditriaje.dto.emergency.RegisterTriageAssessmentRequest;
import com.meditriaje.dto.emergency.TriageAssessmentResponse;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.OperationConflictException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.CareEpisode;
import com.meditriaje.model.ProvisionalIdentity;
import com.meditriaje.model.EmergencyAdmission;
import com.meditriaje.model.Patient;
import com.meditriaje.model.Professional;
import com.meditriaje.model.Site;
import com.meditriaje.model.User;
import com.meditriaje.model.TriageAssessment;
import com.meditriaje.repository.CareAssignmentRepository;
import com.meditriaje.repository.CareEpisodeRepository;
import com.meditriaje.repository.ProvisionalIdentityRepository;
import com.meditriaje.repository.EmergencyAdmissionRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.ProfessionalRepository;
import com.meditriaje.repository.SiteRepository;
import com.meditriaje.repository.UserRepository;
import com.meditriaje.repository.TriageAssessmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmergencyServiceTest {

    @Mock
    private CareEpisodeRepository episodeRepository;
    @Mock
    private EmergencyAdmissionRepository admissionRepository;
    @Mock
    private ProvisionalIdentityRepository identityRepository;
    @Mock
    private TriageAssessmentRepository assessmentRepository;
    @Mock
    private CareAssignmentRepository careAssignmentRepository;
    @Mock
    private SiteRepository siteRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProfessionalRepository professionalRepository;
    @Mock
    private AuditService auditService;

    private EmergencyService emergencyService;

    private static final Instant AHORA = Instant.parse("2026-10-10T14:30:00Z");
    private static final Clock CLOCK_FIJO = Clock.fixed(AHORA, ZoneId.of("America/Bogota"));

    private User enfermeroMock;
    private Site sedeMock;
    private Patient pacienteMock;

    @BeforeEach
    void setUp() {
        emergencyService = new EmergencyService(
                episodeRepository,
                admissionRepository,
                identityRepository,
                assessmentRepository,
                careAssignmentRepository,
                siteRepository,
                patientRepository,
                userRepository,
                professionalRepository,
                auditService,
                CLOCK_FIJO
        );

        enfermeroMock = new User(
                10L, "enf-pub-id", "enfermera@hospital.com", "hash",
                "ACTIVO", 0, null, AHORA, false, false, null, null
        );

        sedeMock = new Site(
                1L, 1L, "sede-valledupar-pub", "Hospital Rosario Pumarejo",
                "Calle 16 # 15-20", "Valledupar", "ACTIVO"
        );

        pacienteMock = new Patient(
                50L, 20L, "pac-pub-id", "CC", "12345678",
                "Juan", "Gomez", LocalDate.of(1995, 5, 20), "3001234567",
                AHORA, AHORA
        );
    }

    @Test
    @DisplayName("U02: Admisión de urgencia de paciente identificado exitosa")
    void registerAdmission_patientIdentified_success() {
        RegisterEmergencyAdmissionRequest req = new RegisterEmergencyAdmissionRequest(
                "pac-pub-id", false,
                "ESPONTANEO", "Dolor toracico agudo", "Pedro Perez", "3111111111",
                "Sin observaciones", null, null, null, null
        );

        when(userRepository.findByPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(siteRepository.getSingleSite()).thenReturn(sedeMock);
        when(patientRepository.findByPublicId("pac-pub-id")).thenReturn(Optional.of(pacienteMock));

        CareEpisode epCreado = new CareEpisode(
                100L, "ep-pub-id", 50L, "URGENCIA", 1L, "REGISTRADO", AHORA, null, "Dolor toracico agudo", AHORA, AHORA
        );
        when(episodeRepository.save(any())).thenReturn(epCreado);

        EmergencyEpisodeResponse detalleMock = new EmergencyEpisodeResponse(
                "ep-pub-id", "pac-pub-id", "Juan Gomez", "sede-valledupar-pub", "Hospital Rosario Pumarejo",
                "URGENCIA", "REGISTRADO", false, null, null, "ESPONTANEO", "Dolor toracico agudo",
                "PENDIENTE_VALORACION", null, AHORA, null, AHORA
        );
        when(episodeRepository.findDetailByPublicId(anyString())).thenReturn(Optional.of(detalleMock));

        EmergencyEpisodeResponse resp = emergencyService.registerEmergencyAdmission(req, "enf-pub-id", "127.0.0.1");

        assertThat(resp).isNotNull();
        assertThat(resp.episodePublicId()).isEqualTo("ep-pub-id");
        assertThat(resp.status()).isEqualTo("REGISTRADO");
        assertThat(resp.isProvisionalIdentity()).isFalse();
        verify(admissionRepository).save(any());
        verify(auditService).audit(any());
    }

    @Test
    @DisplayName("U02/U03: Admisión de paciente no identificado (NN) genera código provisional opaco")
    void registerAdmission_patientProvisionalNN_success() {
        RegisterEmergencyAdmissionRequest req = new RegisterEmergencyAdmissionRequest(
                null, true,
                "AMBULANCIA", "Paciente inconsciente encontrado en via publica",
                null, null, "Trauma craneoencefalico aparente",
                "Tatuaje en brazo izquierdo, contextura media", 35, "MASCULINO", "INCONSCIENTE"
        );

        when(userRepository.findByPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(siteRepository.getSingleSite()).thenReturn(sedeMock);

        CareEpisode epCreado = new CareEpisode(
                101L, "ep-nn-id", null, "URGENCIA", 1L, "REGISTRADO", AHORA, null,
                "Paciente inconsciente", AHORA, AHORA
        );
        when(episodeRepository.save(any())).thenReturn(epCreado);

        EmergencyEpisodeResponse detalleMock = new EmergencyEpisodeResponse(
                "ep-nn-id", null, null, "sede-valledupar-pub", "Hospital Rosario Pumarejo",
                "URGENCIA", "REGISTRADO", true, "NN-20261010-ABCD", "PROVISIONAL",
                "AMBULANCIA", "Paciente inconsciente", "PENDIENTE_VALORACION", null, AHORA, null, AHORA
        );
        when(episodeRepository.findDetailByPublicId(anyString())).thenReturn(Optional.of(detalleMock));

        EmergencyEpisodeResponse resp = emergencyService.registerEmergencyAdmission(req, "enf-pub-id", "127.0.0.1");

        assertThat(resp).isNotNull();
        assertThat(resp.isProvisionalIdentity()).isTrue();
        assertThat(resp.provisionalCode()).startsWith("NN-");
        verify(identityRepository).save(any());
    }

    @Test
    @DisplayName("U03: Reconciliación de identidad provisional hacia paciente verificado")
    void reconcileIdentity_patientNN_linksCorrectly() {
        ReconcileIdentityRequest req = new ReconcileIdentityRequest("pac-pub-id", "Llego familiar con cedula original");

        CareEpisode epExistente = new CareEpisode(
                101L, "ep-nn-id", null, "URGENCIA", 1L, "REGISTRADO", AHORA, null, "Motivo", AHORA, AHORA
        );
        ProvisionalIdentity idProv = new ProvisionalIdentity(
                5L, "id-prov-pub", 101L, "NN-20261010-ABCD", "Descripcion", 30, "MASCULINO",
                "INCONSCIENTE", "PROVISIONAL", null, null, null, AHORA
        );

        when(userRepository.findByPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(episodeRepository.findByPublicId("ep-nn-id")).thenReturn(Optional.of(epExistente));
        when(identityRepository.findByEpisodeId(101L)).thenReturn(Optional.of(idProv));
        when(patientRepository.findByPublicId("pac-pub-id")).thenReturn(Optional.of(pacienteMock));

        EmergencyEpisodeResponse detalleReconciliado = new EmergencyEpisodeResponse(
                "ep-nn-id", "pac-pub-id", "Juan Gomez", "sede-pub", "Hospital", "URGENCIA",
                "REGISTRADO", true, "NN-20261010-ABCD", "VINCULADA", "AMBULANCIA",
                "Motivo", "PENDIENTE_VALORACION", null, AHORA, null, AHORA
        );
        when(episodeRepository.findDetailByPublicId("ep-nn-id")).thenReturn(Optional.of(detalleReconciliado));

        EmergencyEpisodeResponse resp = emergencyService.reconcileIdentity("ep-nn-id", req, "enf-pub-id", "127.0.0.1");

        assertThat(resp).isNotNull();
        assertThat(resp.identityStatus()).isEqualTo("VINCULADA");
        verify(identityRepository).reconcileIdentity(eq(5L), eq(50L), eq(10L), any());
        verify(episodeRepository).linkPatient(101L, 50L);
    }

    @Test
    @DisplayName("U03: Reconciliar identidad ya vinculada lanza ConflictoOperacionException")
    void reconcileIdentity_alreadyLinked_throwsConflict() {
        ReconcileIdentityRequest req = new ReconcileIdentityRequest("pac-pub-id", "Segunda verificacion");
        CareEpisode epExistente = new CareEpisode(101L, "ep-nn-id", 50L, "URGENCIA", 1L, "REGISTRADO", AHORA, null, "Motivo", AHORA, AHORA);
        ProvisionalIdentity idProv = new ProvisionalIdentity(5L, "id-prov-pub", 101L, "NN-20261010-ABCD", null, null, null, null, "VINCULADA", 50L, 10L, AHORA, AHORA);

        when(userRepository.findByPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(episodeRepository.findByPublicId("ep-nn-id")).thenReturn(Optional.of(epExistente));
        when(identityRepository.findByEpisodeId(101L)).thenReturn(Optional.of(idProv));

        assertThatThrownBy(() -> emergencyService.reconcileIdentity("ep-nn-id", req, "enf-pub-id", "127.0.0.1"))
                .isInstanceOf(OperationConflictException.class)
                .hasMessageContaining("ya fue reconciliada");
    }

    @Test
    @DisplayName("U04: Registro de valoración de triaje presencial actualiza estado a EN_ATENCION para Nivel I")
    void registerAssessmentTriage_levelI_updatesStatusToInAttention() {
        RegisterTriageAssessmentRequest req = new RegisterTriageAssessmentRequest(
                "I", "Paro cardiorrespiratorio inminente", "Cianosis severa",
                "70/40", 140, 35, 82, new BigDecimal("35.5"), 5, false, null
        );

        CareEpisode ep = new CareEpisode(100L, "ep-pub-id", 50L, "URGENCIA", 1L, "REGISTRADO", AHORA, null, "Motivo", AHORA, AHORA);
        when(userRepository.findByPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(episodeRepository.findByPublicId("ep-pub-id")).thenReturn(Optional.of(ep));
        when(assessmentRepository.getNextVersion(100L)).thenReturn(1);

        TriageAssessmentResponse resp = emergencyService.recordTriageAssessment(
                "ep-pub-id", req, "enf-pub-id", List.of(new SimpleGrantedAuthority("ROLE_ENFERMERIA")), "127.0.0.1"
        );

        assertThat(resp).isNotNull();
        assertThat(resp.level()).isEqualTo("I");
        verify(assessmentRepository).save(any());
        verify(episodeRepository).updateStatus(100L, "EN_ATENCION");
    }

    @Test
    @DisplayName("U04: Usuario sin rol clínico (ej. ADMIN o PACIENTE) es rechazado con 403 al intentar triaje presencial")
    void registerAssessmentTriage_unauthorizedRole_throwsUnauthorizedAccess() {
        RegisterTriageAssessmentRequest req = new RegisterTriageAssessmentRequest(
                "III", "Cefalea", null, null, null, null, null, null, null, false, null
        );

        assertThatThrownBy(() -> emergencyService.recordTriageAssessment(
                "ep-pub-id", req, "admin-pub-id", List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")), "127.0.0.1"
        ))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Solo personal de enfermería o profesionales asistenciales");
    }

    @Test
    @DisplayName("U06: Asignación de equipo asistencial (médico tratante) inactiva asignación previa")
    void assignTeamCare_attendingPhysician_inactivePreviousAndAsigna() {
        AssignTeamRequest req = new AssignTeamRequest("prof-pub-id", "MEDICO_TRATANTE");
        CareEpisode ep = new CareEpisode(100L, "ep-pub-id", 50L, "URGENCIA", 1L, "EN_TRIAJE", AHORA, null, "Motivo", AHORA, AHORA);
        Professional profMock = new Professional(30L, 15L, "prof-pub-id", 1L, "CC", "998877", "RM-1234", "Carlos", "Medina", "311", AHORA, AHORA);

        when(userRepository.findByPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(episodeRepository.findByPublicId("ep-pub-id")).thenReturn(Optional.of(ep));
        when(professionalRepository.findByPublicId("prof-pub-id")).thenReturn(Optional.of(profMock));

        emergencyService.assignCareTeam("ep-pub-id", req, "enf-pub-id", "127.0.0.1");

        verify(careAssignmentRepository).inactivatePreviousAssignmentByRoleFunction(100L, "MEDICO_TRATANTE");
        verify(careAssignmentRepository).save(any());
        verify(episodeRepository).updateStatus(100L, "EN_ATENCION");
    }

    @Test
    @DisplayName("U05: Listar cola de urgencias ordena por nivel y tiempo de espera")
    void listQueueEmergency_returnsItemsOrdenados() {
        EmergencyQueueItemResponse item1 = new EmergencyQueueItemResponse(
                "ep-1", "pac-1", "Carlos Sanchez", null, "sede-pub", "Hospital",
                "URGENCIA", "EN_ATENCION", "I", "Paro", 5L, AHORA, "Dr. Medina"
        );
        EmergencyQueueItemResponse item2 = new EmergencyQueueItemResponse(
                "ep-2", null, "NN (NN-20261010-ABCD)", "NN-20261010-ABCD", "sede-pub", "Hospital",
                "URGENCIA", "EN_TRIAJE", "II", "Dolor pecho", 12L, AHORA, null
        );

        when(episodeRepository.listEmergencyQueueBySite(null, null)).thenReturn(List.of(item1, item2));

        List<EmergencyQueueItemResponse> cola = emergencyService.listEmergencyQueue(null);

        assertThat(cola).hasSize(2);
        assertThat(cola.get(0).triageLevel()).isEqualTo("I");
        assertThat(cola.get(1).provisionalCode()).isEqualTo("NN-20261010-ABCD");
    }

    @Test
    @DisplayName("U06: Cierre de episodio marca estado EGRESADO exitosamente")
    void closeEpisode_success() {
        CareEpisode ep = new CareEpisode(100L, "ep-pub-id", 50L, "URGENCIA", 1L, "EN_ATENCION", AHORA, null, "Motivo", AHORA, AHORA);
        when(userRepository.findByPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(episodeRepository.findByPublicId("ep-pub-id")).thenReturn(Optional.of(ep));

        emergencyService.closeEpisode("ep-pub-id", "enf-pub-id", "127.0.0.1");

        verify(episodeRepository).closeEpisode(eq(100L), any());
    }
}
