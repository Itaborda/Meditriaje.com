package com.meditriaje.service;

import com.meditriaje.dto.emergency.AssignTeamRequest;
import com.meditriaje.dto.emergency.CalculateTriageLevelRequest;
import com.meditriaje.dto.emergency.EmergencyEpisodeResponse;
import com.meditriaje.dto.emergency.EmergencyPatientLookupResponse;
import com.meditriaje.dto.emergency.EmergencyQueueItemResponse;
import com.meditriaje.dto.emergency.ReconcileIdentityRequest;
import com.meditriaje.dto.emergency.RegisterEmergencyAdmissionRequest;
import com.meditriaje.dto.emergency.RegisterTriageAssessmentRequest;
import com.meditriaje.dto.emergency.TriageAssessmentResponse;
import com.meditriaje.dto.emergency.TriageCalculationResponse;
import com.meditriaje.dto.triage.SymptomCatalogResponse;
import com.meditriaje.dto.triage.SymptomItemRequest;
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
import com.meditriaje.repository.TriageAssessmentRepository;
import com.meditriaje.repository.TriageRepository;
import com.meditriaje.repository.UserRepository;
import com.meditriaje.triage.PriorityLevel;
import com.meditriaje.triage.RuleBasedTriageEngine;
import com.meditriaje.triage.TriageEngineFactory;
import com.meditriaje.triage.TriageRule;
import com.meditriaje.triage.TriageSymptom;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private TriageEngineFactory triageEngineFactory;
    @Mock
    private TriageRepository triageRepository;
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
                triageEngineFactory,
                triageRepository,
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

    private static RuleBasedTriageEngine realEngine() {
        List<TriageSymptom> catalog = List.of(
                new TriageSymptom("DOLOR_TORACICO_OPRESIVO", true),
                new TriageSymptom("FIEBRE", false),
                new TriageSymptom("CONGESTION_NASAL", false)
        );
        List<TriageRule> rules = new ArrayList<>();
        for (String code : List.of("FIEBRE", "CONGESTION_NASAL")) {
            rules.add(new TriageRule(code, BigDecimal.ZERO, null, 7, 10, PriorityLevel.II));
            rules.add(new TriageRule(code, BigDecimal.ZERO, null, 4, 6, PriorityLevel.III));
            rules.add(new TriageRule(code, BigDecimal.ZERO, null, 0, 3, PriorityLevel.IV));
        }
        return new RuleBasedTriageEngine("v1-prototipo", catalog, rules);
    }

    private void stubCatalogAndAssessmentSave() {
        when(triageEngineFactory.getEngine()).thenReturn(realEngine());
        when(triageRepository.getSymptomCodeToIdMap()).thenReturn(Map.of(
                "DOLOR_TORACICO_OPRESIVO", 1L,
                "FIEBRE", 7L,
                "CONGESTION_NASAL", 20L
        ));
        when(triageRepository.listActiveSymptomCatalog()).thenReturn(List.of(
                new SymptomCatalogResponse("s-1", "DOLOR_TORACICO_OPRESIVO", "Dolor torácico opresivo", "CARDIOVASCULAR", true),
                new SymptomCatalogResponse("s-7", "FIEBRE", "Fiebre", "GENERAL", false),
                new SymptomCatalogResponse("s-20", "CONGESTION_NASAL", "Congestión nasal", "RESPIRATORIO", false)
        ));
        when(assessmentRepository.save(any())).thenAnswer(inv -> {
            TriageAssessment arg = inv.getArgument(0);
            return new TriageAssessment(
                    99L, arg.publicId(), arg.episodeId(), arg.version(), arg.level(),
                    arg.consultationReason(), arg.clinicalFindings(), arg.bloodPressure(),
                    arg.heartRate(), arg.respiratoryRate(), arg.oxygenSaturation(),
                    arg.temperature(), arg.glasgowScale(), arg.evaluatorUserId(),
                    arg.isReevaluation(), arg.reevaluationReason(), AHORA
            );
        });
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
    @DisplayName("U02: Búsqueda de pacientes por documento retorna si tienen o no cuenta web MediTriaje")
    void searchPatientsByDocument_returnsMappedResults() {
        Patient walkIn = new Patient(
                51L, null, "pac-walkin-id", "CC", "1134169348",
                "Maria", "Lopez", LocalDate.of(1992, 3, 15), "3007654321",
                AHORA, AHORA
        );
        when(patientRepository.searchByDocument("1134169348", "CC"))
                .thenReturn(List.of(pacienteMock, walkIn));

        List<EmergencyPatientLookupResponse> results = emergencyService.searchPatientsByDocument(" 1134169348 ", "cc");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).hasAccount()).isTrue();
        assertThat(results.get(1).publicId()).isEqualTo("pac-walkin-id");
        assertThat(results.get(1).hasAccount()).isFalse();
        assertThat(emergencyService.searchPatientsByDocument("   ", null)).isEmpty();
    }

    @Test
    @DisplayName("U02: Admisión con cédula en pacientePublicId resuelve paciente registrado mediante fallback por documento")
    void registerAdmission_rawDocumentInPatientPublicId_fallsBackToSearchByDocument() {
        Patient pacienteDoc = new Patient(
                51L, 21L, "pac-uuid-doc", "CC", "1134169348",
                "Maria", "Lopez", LocalDate.of(1992, 3, 15), "3007654321",
                AHORA, AHORA
        );
        RegisterEmergencyAdmissionRequest req = new RegisterEmergencyAdmissionRequest(
                "1134169348", false,
                "ESPONTANEO", "Fiebre alta", null, null, null,
                null, null, null, null
        );

        when(userRepository.findByPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(siteRepository.getSingleSite()).thenReturn(sedeMock);
        when(patientRepository.findByPublicId("1134169348")).thenReturn(Optional.empty());
        when(patientRepository.searchByDocument("1134169348", null)).thenReturn(List.of(pacienteDoc));
        when(episodeRepository.save(any())).thenAnswer(inv -> {
            CareEpisode ep = inv.getArgument(0);
            assertThat(ep.patientId()).isEqualTo(51L);
            return new CareEpisode(102L, ep.publicId(), ep.patientId(), ep.type(), ep.siteId(), ep.status(), AHORA, null, ep.admissionReason(), AHORA, AHORA);
        });
        EmergencyEpisodeResponse detalleMock = new EmergencyEpisodeResponse(
                "ep-doc-id", "pac-uuid-doc", "Maria Lopez", "sede-valledupar-pub", "Hospital Rosario Pumarejo",
                "URGENCIA", "REGISTRADO", false, null, null, "ESPONTANEO", "Fiebre alta",
                "PENDIENTE_VALORACION", null, AHORA, null, AHORA
        );
        when(episodeRepository.findDetailByPublicId(anyString())).thenReturn(Optional.of(detalleMock));

        EmergencyEpisodeResponse resp = emergencyService.registerEmergencyAdmission(req, "enf-pub-id", "127.0.0.1");

        assertThat(resp.patientPublicId()).isEqualTo("pac-uuid-doc");
        verify(admissionRepository).save(any());
    }

    @Test
    @DisplayName("U02: Admisión de paciente presencial sin cuenta MediTriaje crea PACIENTE con USUARIO_ID nulo")
    void registerAdmission_walkInPatientWithoutAccount_createsPatientWithNullUserId() {
        RegisterEmergencyAdmissionRequest req = new RegisterEmergencyAdmissionRequest(
                null, false,
                "ESPONTANEO", "Dolor abdominal intenso", "Luis Gomez", "3109998877", null,
                null, null, null, null,
                "CC", "1134169348", "Maria", "Lopez", LocalDate.of(1992, 3, 15), "3007654321"
        );

        when(userRepository.findByPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(siteRepository.getSingleSite()).thenReturn(sedeMock);
        when(patientRepository.findByDocument("CC", "1134169348")).thenReturn(Optional.empty());
        when(patientRepository.create(
                eq(null), anyString(), eq("CC"), eq("1134169348"),
                eq("Maria"), eq("Lopez"), eq(LocalDate.of(1992, 3, 15)), eq("3007654321")
        )).thenReturn(77L);
        when(episodeRepository.save(any())).thenAnswer(inv -> {
            CareEpisode ep = inv.getArgument(0);
            assertThat(ep.patientId()).isEqualTo(77L);
            return new CareEpisode(103L, ep.publicId(), ep.patientId(), ep.type(), ep.siteId(), ep.status(), AHORA, null, ep.admissionReason(), AHORA, AHORA);
        });
        EmergencyEpisodeResponse detalleMock = new EmergencyEpisodeResponse(
                "ep-walkin-id", "pac-new-walkin", "Maria Lopez", "sede-valledupar-pub", "Hospital Rosario Pumarejo",
                "URGENCIA", "REGISTRADO", false, null, null, "ESPONTANEO", "Dolor abdominal intenso",
                "PENDIENTE_VALORACION", null, AHORA, null, AHORA
        );
        when(episodeRepository.findDetailByPublicId(anyString())).thenReturn(Optional.of(detalleMock));

        EmergencyEpisodeResponse resp = emergencyService.registerEmergencyAdmission(req, "enf-pub-id", "127.0.0.1");

        assertThat(resp.isProvisionalIdentity()).isFalse();
        verify(patientRepository).create(
                eq(null), anyString(), eq("CC"), eq("1134169348"),
                eq("Maria"), eq("Lopez"), eq(LocalDate.of(1992, 3, 15)), eq("3007654321")
        );
        verify(admissionRepository).save(any());
    }

    @Test
    @DisplayName("U02: Admisión con formulario manual para cédula ya registrada reutiliza paciente existente sin duplicarlo")
    void registerAdmission_walkInDocumentAlreadyExists_reusesExistingPatient() {
        RegisterEmergencyAdmissionRequest req = new RegisterEmergencyAdmissionRequest(
                null, false,
                "ESPONTANEO", "Cefalea intensa", null, null, null,
                null, null, null, null,
                "CC", "12345678", "Juan", "Gomez", LocalDate.of(1995, 5, 20), "3001234567"
        );

        when(userRepository.findByPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(siteRepository.getSingleSite()).thenReturn(sedeMock);
        when(patientRepository.findByDocument("CC", "12345678")).thenReturn(Optional.of(pacienteMock));
        when(episodeRepository.save(any())).thenAnswer(inv -> {
            CareEpisode ep = inv.getArgument(0);
            assertThat(ep.patientId()).isEqualTo(50L);
            return new CareEpisode(104L, ep.publicId(), ep.patientId(), ep.type(), ep.siteId(), ep.status(), AHORA, null, ep.admissionReason(), AHORA, AHORA);
        });
        EmergencyEpisodeResponse detalleMock = new EmergencyEpisodeResponse(
                "ep-reuse-id", "pac-pub-id", "Juan Gomez", "sede-valledupar-pub", "Hospital Rosario Pumarejo",
                "URGENCIA", "REGISTRADO", false, null, null, "ESPONTANEO", "Cefalea intensa",
                "PENDIENTE_VALORACION", null, AHORA, null, AHORA
        );
        when(episodeRepository.findDetailByPublicId(anyString())).thenReturn(Optional.of(detalleMock));

        EmergencyEpisodeResponse resp = emergencyService.registerEmergencyAdmission(req, "enf-pub-id", "127.0.0.1");

        assertThat(resp.patientPublicId()).isEqualTo("pac-pub-id");
        org.mockito.Mockito.verify(patientRepository, org.mockito.Mockito.never())
                .create(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
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
    @DisplayName("U04: Síntoma de alarma calcula Nivel I automáticamente, persiste síntomas y pasa episodio a EN_ATENCION")
    void registerAssessmentTriage_alarmSymptom_calculatesLevelIAndUpdatesStatusToInAttention() {
        RegisterTriageAssessmentRequest req = new RegisterTriageAssessmentRequest(
                List.of(new SymptomItemRequest("DOLOR_TORACICO_OPRESIVO", new BigDecimal("1"), 9)),
                "Dolor precordial opresivo", "Diaforesis severa",
                "70/40", 140, 35, 82, new BigDecimal("35.5"), 5, false, null
        );

        CareEpisode ep = new CareEpisode(100L, "ep-pub-id", 50L, "URGENCIA", 1L, "REGISTRADO", AHORA, null, "Motivo", AHORA, AHORA);
        when(userRepository.findByPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(episodeRepository.findByPublicId("ep-pub-id")).thenReturn(Optional.of(ep));
        when(assessmentRepository.getNextVersion(100L)).thenReturn(1);
        stubCatalogAndAssessmentSave();

        TriageAssessmentResponse resp = emergencyService.recordTriageAssessment(
                "ep-pub-id", req, "enf-pub-id", List.of(new SimpleGrantedAuthority("ROLE_ENFERMERIA")), "127.0.0.1"
        );

        assertThat(resp).isNotNull();
        assertThat(resp.level()).isEqualTo("I");
        assertThat(resp.symptoms()).hasSize(1);
        assertThat(resp.symptoms().get(0).code()).isEqualTo("DOLOR_TORACICO_OPRESIVO");
        assertThat(resp.symptoms().get(0).isAlarm()).isTrue();

        ArgumentCaptor<TriageAssessment> assessmentCaptor = ArgumentCaptor.forClass(TriageAssessment.class);
        verify(assessmentRepository).save(assessmentCaptor.capture());
        assertThat(assessmentCaptor.getValue().level()).isEqualTo("I");

        verify(assessmentRepository).saveSymptoms(eq(99L), any());
        verify(episodeRepository).updateStatus(100L, "EN_ATENCION");
    }

    @Test
    @DisplayName("U04: Síntoma no alarma con intensidad alta (8) calcula Nivel II y actualiza a EN_ATENCION")
    void registerAssessmentTriage_highIntensitySymptom_calculatesLevelIIAndUpdatesStatusToInAttention() {
        RegisterTriageAssessmentRequest req = new RegisterTriageAssessmentRequest(
                List.of(new SymptomItemRequest("FIEBRE", new BigDecimal("4"), 8)),
                "Fiebre alta", "Escalofríos",
                "120/80", 110, 22, 95, new BigDecimal("39.2"), 15, false, null
        );

        CareEpisode ep = new CareEpisode(100L, "ep-pub-id", 50L, "URGENCIA", 1L, "REGISTRADO", AHORA, null, "Motivo", AHORA, AHORA);
        when(userRepository.findByPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(episodeRepository.findByPublicId("ep-pub-id")).thenReturn(Optional.of(ep));
        when(assessmentRepository.getNextVersion(100L)).thenReturn(1);
        stubCatalogAndAssessmentSave();

        TriageAssessmentResponse resp = emergencyService.recordTriageAssessment(
                "ep-pub-id", req, "enf-pub-id", List.of(new SimpleGrantedAuthority("ROLE_ENFERMERIA")), "127.0.0.1"
        );

        assertThat(resp.level()).isEqualTo("II");
        verify(assessmentRepository).saveSymptoms(eq(99L), any());
        verify(episodeRepository).updateStatus(100L, "EN_ATENCION");
    }

    @Test
    @DisplayName("U04: Síntoma con intensidad moderada (5) calcula Nivel III y actualiza episodio REGISTRADO a EN_TRIAJE")
    void registerAssessmentTriage_moderateIntensitySymptom_calculatesLevelIIIAndUpdatesStatusToInTriage() {
        RegisterTriageAssessmentRequest req = new RegisterTriageAssessmentRequest(
                List.of(new SymptomItemRequest("FIEBRE", new BigDecimal("6"), 5)),
                "Fiebre moderada", null, null, null, null, null, null, null, false, null
        );

        CareEpisode ep = new CareEpisode(100L, "ep-pub-id", 50L, "URGENCIA", 1L, "REGISTRADO", AHORA, null, "Motivo", AHORA, AHORA);
        when(userRepository.findByPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(episodeRepository.findByPublicId("ep-pub-id")).thenReturn(Optional.of(ep));
        when(assessmentRepository.getNextVersion(100L)).thenReturn(1);
        stubCatalogAndAssessmentSave();

        TriageAssessmentResponse resp = emergencyService.recordTriageAssessment(
                "ep-pub-id", req, "enf-pub-id", List.of(new SimpleGrantedAuthority("ROLE_ENFERMERIA")), "127.0.0.1"
        );

        assertThat(resp.level()).isEqualTo("III");
        verify(episodeRepository).updateStatus(100L, "EN_TRIAJE");
    }

    @Test
    @DisplayName("U04: Síntoma leve (intensidad 2) calcula Nivel IV y actualiza episodio REGISTRADO a EN_TRIAJE")
    void registerAssessmentTriage_mildSymptom_calculatesLevelIVAndUpdatesStatusToInTriage() {
        RegisterTriageAssessmentRequest req = new RegisterTriageAssessmentRequest(
                List.of(new SymptomItemRequest("CONGESTION_NASAL", new BigDecimal("12"), 2)),
                "Congestión nasal leve", null, null, null, null, null, null, null, false, null
        );

        CareEpisode ep = new CareEpisode(100L, "ep-pub-id", 50L, "URGENCIA", 1L, "REGISTRADO", AHORA, null, "Motivo", AHORA, AHORA);
        when(userRepository.findByPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(episodeRepository.findByPublicId("ep-pub-id")).thenReturn(Optional.of(ep));
        when(assessmentRepository.getNextVersion(100L)).thenReturn(1);
        stubCatalogAndAssessmentSave();

        TriageAssessmentResponse resp = emergencyService.recordTriageAssessment(
                "ep-pub-id", req, "enf-pub-id", List.of(new SimpleGrantedAuthority("ROLE_ENFERMERIA")), "127.0.0.1"
        );

        assertThat(resp.level()).isEqualTo("IV");
        verify(episodeRepository).updateStatus(100L, "EN_TRIAJE");
    }

    @Test
    @DisplayName("U04: Síntoma desconocido lanza InvalidDataException y no persiste valoración")
    void registerAssessmentTriage_unknownSymptom_throwsInvalidDataException() {
        RegisterTriageAssessmentRequest req = new RegisterTriageAssessmentRequest(
                List.of(new SymptomItemRequest("SINTOMA_INEXISTENTE", new BigDecimal("2"), 5)),
                "Motivo", null, null, null, null, null, null, null, false, null
        );

        CareEpisode ep = new CareEpisode(100L, "ep-pub-id", 50L, "URGENCIA", 1L, "REGISTRADO", AHORA, null, "Motivo", AHORA, AHORA);
        when(userRepository.findByPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(episodeRepository.findByPublicId("ep-pub-id")).thenReturn(Optional.of(ep));
        when(triageEngineFactory.getEngine()).thenReturn(realEngine());

        assertThatThrownBy(() -> emergencyService.recordTriageAssessment(
                "ep-pub-id", req, "enf-pub-id", List.of(new SimpleGrantedAuthority("ROLE_ENFERMERIA")), "127.0.0.1"
        ))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Síntoma desconocido");
    }

    @Test
    @DisplayName("U04: Previsualización de cálculo por síntomas retorna nivel sin persistir datos")
    void calculateTriageLevel_returnsCalculatedLevelWithoutPersisting() {
        when(triageEngineFactory.getEngine()).thenReturn(realEngine());

        TriageCalculationResponse alarmCalc = emergencyService.calculateTriageLevel(
                new CalculateTriageLevelRequest(List.of(
                        new SymptomItemRequest("FIEBRE", new BigDecimal("4"), 3),
                        new SymptomItemRequest("DOLOR_TORACICO_OPRESIVO", new BigDecimal("1"), 8)
                ))
        );

        assertThat(alarmCalc.level()).isEqualTo("I");
        assertThat(alarmCalc.isEmergency()).isTrue();
        assertThat(alarmCalc.alarmSymptoms()).containsExactly("DOLOR_TORACICO_OPRESIVO");
        verifyNoInteractions(assessmentRepository, episodeRepository);
    }

    @Test
    @DisplayName("U04: Usuario sin rol clínico (ej. ADMIN o PACIENTE) es rechazado con 403 al intentar triaje presencial")
    void registerAssessmentTriage_unauthorizedRole_throwsUnauthorizedAccess() {
        RegisterTriageAssessmentRequest req = new RegisterTriageAssessmentRequest(
                List.of(new SymptomItemRequest("FIEBRE", new BigDecimal("6"), 5)),
                "Cefalea", null, null, null, null, null, null, null, false, null
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
