package com.meditriaje.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.meditriaje.dto.AuthTokens;
import com.meditriaje.dto.LoginRequest;
import com.meditriaje.dto.clinical.ClinicalAttentionResponse;
import com.meditriaje.dto.clinical.CloseAttentionRequest;
import com.meditriaje.dto.clinical.StartAttentionRequest;
import com.meditriaje.dto.emergency.GenerateQrRequest;
import com.meditriaje.dto.prescription.CreatePrescriptionDetailRequest;
import com.meditriaje.dto.prescription.CreatePrescriptionRequest;
import com.meditriaje.model.ClinicalAttention;
import com.meditriaje.model.Appointment;
import com.meditriaje.model.Cie10Diagnosis;
import com.meditriaje.model.AvailabilitySlot;
import com.meditriaje.model.Medication;
import com.meditriaje.model.Patient;
import com.meditriaje.model.Professional;
import com.meditriaje.model.User;
import com.meditriaje.repository.TemporaryQrAccessRepository;
import com.meditriaje.repository.AllergyRepository;
import com.meditriaje.repository.ClinicalAttentionRepository;
import com.meditriaje.repository.AppointmentRepository;
import com.meditriaje.repository.VerificationCodeRepository;
import com.meditriaje.repository.ConsentRepository;
import com.meditriaje.repository.Cie10DiagnosisRepository;
import com.meditriaje.repository.AvailabilitySlotRepository;
import com.meditriaje.repository.MedicationRepository;
import com.meditriaje.repository.MfaBackupCodeRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.ProfessionalRepository;
import com.meditriaje.repository.PrescriptionRepository;
import com.meditriaje.repository.RefreshTokenRepository;
import com.meditriaje.repository.UserRepository;
import com.meditriaje.service.ClinicalAccessService;
import com.meditriaje.service.AuditService;
import com.meditriaje.service.AuthService;
import com.meditriaje.service.ClinicalAttentionService;
import com.meditriaje.service.EmergencyQrService;
import com.meditriaje.service.PrescriptionService;
import com.meditriaje.service.email.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Suite 8: Pruebas de sanitización de logs (cero PHI, datos clínicos, contraseñas ni tokens en logs) (ADR-011, T6).
 * Valida mediante captura estricta con ListAppender que los flujos de autenticación, atención, receta y QR no filtren datos sensibles.
 */
@ExtendWith(MockitoExtension.class)
class SecurityLogSanitizationTest {

    private ListAppender<ILoggingEvent> listAppender;
    private Logger meditriajeLogger;

    @Mock
    private UserRepository userRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private ProfessionalRepository professionalRepository;
    @Mock
    private ConsentRepository consentRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private VerificationCodeRepository verificationCodeRepository;
    @Mock
    private MfaBackupCodeRepository mfaBackupCodeRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private ClinicalAttentionRepository attentionRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private Cie10DiagnosisRepository cie10Repository;
    @Mock
    private AvailabilitySlotRepository slotRepository;
    @Mock
    private ClinicalAccessService clinicalAccessService;
    @Mock
    private MedicationRepository medicationRepository;
    @Mock
    private PrescriptionRepository prescriptionRepository;
    @Mock
    private TemporaryQrAccessRepository temporaryQrAccessRepository;
    @Mock
    private AllergyRepository allergyRepository;

    private JwtService jwtService;
    private AuthService authService;
    private ClinicalAttentionService attentionService;
    private PrescriptionService prescriptionService;
    private EmergencyQrService emergencyQrService;

    private static final String SENSITIVE_PASSWORD = "VerySecretPassword!2026";
    private static final String SENSITIVE_CLINICAL_NOTE = "Paciente con sospecha de infarto agudo de miocardio y dolor retroesternal";
    private static final String SENSITIVE_MEDICATION = "Morfina Solucion Inyectable 10mg/ml";
    private static final String SENSITIVE_INDICATION = "Administrar via intravenosa lenta cada 4 horas";
    private static final String SENSITIVE_PIN = "8492";
    private static final String SENSITIVE_DOC = "CC-9988776655";

    @BeforeEach
    void setUp() {
        meditriajeLogger = (Logger) LoggerFactory.getLogger("com.meditriaje");
        listAppender = new ListAppender<>();
        listAppender.start();
        meditriajeLogger.addAppender(listAppender);

        jwtService = new JwtService("c2VjdXJpdHktand0LXNlY3JldC1rZXktbXVzdC1iZS1hdC1sZWFzdC0yNTYtYml0cy1sb25n", 15);
        authService = new AuthService(
                userRepository,
                patientRepository,
                professionalRepository,
                consentRepository,
                refreshTokenRepository,
                verificationCodeRepository,
                mfaBackupCodeRepository,
                auditService,
                passwordEncoder,
                jwtService,
                new TotpService(),
                emailService,
                7
        );

        Clock clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneId.of("America/Bogota"));
        attentionService = new ClinicalAttentionService(
                userRepository,
                patientRepository,
                professionalRepository,
                appointmentRepository,
                slotRepository,
                attentionRepository,
                cie10Repository,
                clinicalAccessService,
                auditService,
                clock
        );

        prescriptionService = new PrescriptionService(
                userRepository,
                professionalRepository,
                patientRepository,
                attentionRepository,
                medicationRepository,
                prescriptionRepository,
                clinicalAccessService,
                auditService,
                clock
        );

        emergencyQrService = new EmergencyQrService(
                temporaryQrAccessRepository,
                patientRepository,
                userRepository,
                auditService,
                passwordEncoder,
                clock
        );
    }

    @AfterEach
    void tearDown() {
        if (meditriajeLogger != null && listAppender != null) {
            meditriajeLogger.detachAppender(listAppender);
            listAppender.stop();
        }
    }

    private void assertLogsDoNotContain(String... forbiddenStrings) {
        for (ILoggingEvent event : listAppender.list) {
            String logMsg = event.getFormattedMessage();
            for (String forbidden : forbiddenStrings) {
                assertThat(logMsg)
                        .as("El log no debe filtrar información sensible: '%s'. Log encontrado: %s", forbidden, logMsg)
                        .doesNotContain(forbidden);
            }
        }
    }

    @Test
    @DisplayName("Sanitización: Flujo de login no registra contraseña en claro ni tokens en los logs")
    void login_notFiltersPasswordNorTokens() {
        User usuario = new User(
                1L, "usr-uuid-1", "usuario@test.com", "argon2id$hashed",
                "ACTIVO", 0, null, Instant.now(), false, false, null, null
        );

        when(userRepository.findByEmail("usuario@test.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(SENSITIVE_PASSWORD, "argon2id$hashed")).thenReturn(true);
        when(userRepository.getRoles(1L)).thenReturn(List.of("ROLE_PACIENTE"));

        LoginRequest request = new LoginRequest("usuario@test.com", SENSITIVE_PASSWORD);
        AuthTokens response = authService.login(request, "192.168.1.1");

        assertThat(response).isNotNull();
        assertLogsDoNotContain(SENSITIVE_PASSWORD, response.accessToken());
    }

    @Test
    @DisplayName("Sanitización: Apertura y cierre de atención clínica no filtra diagnósticos ni evoluciones en los logs")
    void clinicalAttention_notFiltersProgressNorDiagnosis() {
        User usuarioProf = new User(10L, "prof-user-uuid", "doc@test.com", "hash", "ACTIVO", 0, null, Instant.now(), false, false, null, null);
        Professional prof = new Professional(100L, 10L, "prof-uuid", 1L, "RM-1010", "Dra. Ana", "Ruiz", Instant.now(), Instant.now());
        AvailabilitySlot slot = new AvailabilitySlot(300L, "slot-uuid", 100L, 1L, 1L, Instant.now(), Instant.now().plus(20, ChronoUnit.MINUTES), "PRESENCIAL", "OCUPADO");
        Appointment cita = new Appointment(400L, "cita-uuid", 300L, 200L, null, null, "PROGRAMADA", null, Instant.now(), Instant.now());

        when(userRepository.findByPublicId("prof-user-uuid")).thenReturn(Optional.of(usuarioProf));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.of(prof));
        when(appointmentRepository.findEntityByPublicId("cita-uuid")).thenReturn(Optional.of(cita));
        when(appointmentRepository.findEntityById(400L)).thenReturn(Optional.of(cita));
        when(slotRepository.findById(300L)).thenReturn(Optional.of(slot));
        when(attentionRepository.existsByAppointmentId(400L)).thenReturn(false);
        when(attentionRepository.create(any())).thenReturn(500L);

        ClinicalAttentionResponse mockResponse = new ClinicalAttentionResponse(
                "atn-uuid-500", "cita-uuid", "pac-uuid", "Juan Perez", "prof-uuid", "Dra. Ana Ruiz",
                "Medicina General", "ABIERTA", Instant.now(), null, null, null, null, null, null, null, List.of()
        );
        when(attentionRepository.findDetailByPublicId(anyString())).thenReturn(Optional.of(mockResponse));

        // 1. Iniciar atención
        StartAttentionRequest iniciarReq = new StartAttentionRequest("cita-uuid");
        attentionService.startAttention(iniciarReq, "prof-user-uuid", "127.0.0.1");

        // 2. Cerrar atención con CIE-10 y evolución clínica sensible
        ClinicalAttention atencionIniciada = new ClinicalAttention(500L, "atn-uuid-500", 400L, 200L, 100L, "ABIERTA");
        when(attentionRepository.findEntityByPublicId("atn-uuid-500")).thenReturn(Optional.of(atencionIniciada));

        Cie10Diagnosis cie10 = new Cie10Diagnosis(1L, "I20.0", "Angina inestable aguda", "ACTIVO");
        when(cie10Repository.findByCode("I20.0")).thenReturn(Optional.of(cie10));

        CloseAttentionRequest cerrarReq = new CloseAttentionRequest(
                "I20.0",
                "Motivo de consulta general",
                "Evolución clínica crítica: " + SENSITIVE_CLINICAL_NOTE,
                SENSITIVE_INDICATION,
                null
        );
        attentionService.closeAttention("atn-uuid-500", cerrarReq, "prof-user-uuid", "127.0.0.1");

        // Verificar que ningún log contenga notas clínicas, diagnósticos o documentos del paciente
        assertLogsDoNotContain(
                SENSITIVE_CLINICAL_NOTE,
                "Angina inestable",
                "I20.0"
        );
    }

    @Test
    @DisplayName("Sanitización: Prescripción médica no filtra nombres de fármacos, dosis ni indicaciones en los logs")
    void prescription_notFiltersMedicationsNorInstructions() {
        User usuarioProf = new User(10L, "prof-user-uuid", "doc@test.com", "hash", "ACTIVO", 0, null, Instant.now(), false, false, null, null);
        Professional prof = new Professional(100L, 10L, "prof-uuid", 1L, "RM-1010", "Dra. Ana", "Ruiz", Instant.now(), Instant.now());
        ClinicalAttention atencion = new ClinicalAttention(500L, "atn-uuid", 400L, 200L, 100L, "CERRADA");
        Medication med = new Medication(1L, "med-uuid", "MED-MORF", SENSITIVE_MEDICATION, "Morfina", "Ampolla", "10mg/ml", "ACTIVO");

        when(userRepository.findByPublicId("prof-user-uuid")).thenReturn(Optional.of(usuarioProf));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.of(prof));
        when(attentionRepository.findEntityByPublicId("atn-uuid")).thenReturn(Optional.of(atencion));
        when(medicationRepository.findByPublicId("med-uuid")).thenReturn(Optional.of(med));
        when(prescriptionRepository.createPrescription(any())).thenReturn(77L);
        when(prescriptionRepository.findByPublicId(anyString())).thenReturn(Optional.of(
                new com.meditriaje.dto.prescription.PrescriptionResponse(
                        "rx-pub-1", "atn-uuid", "pac-uuid", "Juan Perez", "prof-uuid", "Dra. Ana Ruiz",
                        "Medicina General", 30, Instant.now(), List.of()
                )
        ));

        CreatePrescriptionDetailRequest detalle = new CreatePrescriptionDetailRequest(
                "med-uuid", "10mg", "Cada 4h", 3, 6, SENSITIVE_INDICATION
        );
        CreatePrescriptionRequest request = new CreatePrescriptionRequest("atn-uuid", 30, List.of(detalle));

        prescriptionService.issuePrescription(request, "prof-user-uuid", "127.0.0.1");

        assertLogsDoNotContain(
                SENSITIVE_MEDICATION,
                "Morfina",
                SENSITIVE_INDICATION
        );
    }

    @Test
    @DisplayName("Sanitización: Generación de QR de emergencia no filtra PIN ni documentos en logs")
    void qrEmergency_notFiltersPinNorDocumentosInLogs() {
        User usuarioPac = new User(20L, "pac-user-uuid", "pac@test.com", "hash", "ACTIVO", 0, null, Instant.now(), false, false, null, null);
        Patient pac = new Patient(200L, 20L, "pac-uuid", "CC", SENSITIVE_DOC, "Juan", "Perez", LocalDate.of(1990, 1, 1), "3001234567", Instant.now(), Instant.now());

        when(userRepository.findByPublicId("pac-user-uuid")).thenReturn(Optional.of(usuarioPac));
        when(patientRepository.findByUserId(20L)).thenReturn(Optional.of(pac));
        when(passwordEncoder.encode(SENSITIVE_PIN)).thenReturn("argon2id$hashedpin");
        when(temporaryQrAccessRepository.create(any())).thenReturn(99L);

        GenerateQrRequest request = new GenerateQrRequest(SENSITIVE_PIN, true, true, true, true);
        emergencyQrService.generateQrAccess(request, "pac-user-uuid", "192.168.1.10");

        assertLogsDoNotContain(
                SENSITIVE_PIN,
                SENSITIVE_DOC
        );
    }
}
