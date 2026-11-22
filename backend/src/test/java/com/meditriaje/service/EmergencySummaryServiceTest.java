package com.meditriaje.service;

import com.meditriaje.dto.clinical.ClinicalAttentionResponse;
import com.meditriaje.dto.emergency.ConsultSummaryRequest;
import com.meditriaje.dto.emergency.summary.HealthSummaryResponse;
import com.meditriaje.dto.prescription.PrescriptionDetailResponse;
import com.meditriaje.dto.prescription.PrescriptionResponse;
import com.meditriaje.exception.InvalidCredentialsException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.TemporaryQrAccess;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.Allergy;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.Patient;
import com.meditriaje.model.User;
import com.meditriaje.repository.TemporaryQrAccessRepository;
import com.meditriaje.repository.AllergyRepository;
import com.meditriaje.repository.ClinicalAttentionRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.PrescriptionRepository;
import com.meditriaje.repository.UserRepository;
import com.meditriaje.security.TokenHashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
class EmergencySummaryServiceTest {

    @Mock
    private TemporaryQrAccessRepository temporaryQrAccessRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AllergyRepository allergyRepository;

    @Mock
    private PrescriptionRepository prescriptionRepository;

    @Mock
    private ClinicalAttentionRepository attentionRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private Clock fixedClock;
    private Instant fixedInstant;

    private EmergencySummaryService emergencySummaryService;

    private User usuario;
    private Patient paciente;
    private String tokenPlano;
    private String tokenHash;

    @BeforeEach
    void setUp() {
        fixedInstant = Instant.parse("2026-10-04T12:00:00Z");
        fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        emergencySummaryService = new EmergencySummaryService(
                temporaryQrAccessRepository,
                patientRepository,
                userRepository,
                allergyRepository,
                prescriptionRepository,
                attentionRepository,
                auditService,
                passwordEncoder,
                fixedClock
        );

        tokenPlano = "token-emergencia-12345678901234567890";
        tokenHash = TokenHashUtil.hash(tokenPlano);

        usuario = new User(
                5L,
                "user-uuid-5",
                "paciente@test.com",
                "pwdHash",
                "ACTIVO",
                0,
                null,
                fixedInstant,
                false,
                false,
                null,
                null
        );

        paciente = new Patient(
                10L,
                5L,
                "pac-uuid-10",
                "CC",
                "1020304050",
                "Maria",
                "Gomez",
                LocalDate.of(1996, 10, 4), // 30 años exactos el 2026-10-04
                "3101234567",
                fixedInstant,
                fixedInstant
        );
    }

    @Test
    @DisplayName("consultarResumenPorToken: consulta exitosa con todos los módulos y auditoría")
    void getSummaryByToken_successComplete() {
        TemporaryQrAccess acceso = new TemporaryQrAccess(
                1L, "qr-pub-1", 10L, tokenHash, null,
                true, true, true, true,
                3, 0, false,
                fixedInstant.plus(15, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );

        when(temporaryQrAccessRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(acceso));
        when(temporaryQrAccessRepository.recordAccess(1L, fixedInstant)).thenReturn(1);
        when(patientRepository.findById(10L)).thenReturn(Optional.of(paciente));
        when(userRepository.findById(5L)).thenReturn(Optional.of(usuario));

        // Alergias
        Allergy alergia = new Allergy(100L, 10L, "Penicilina", "Shock anafilactico", "GRAVE", fixedInstant);
        when(allergyRepository.listByPatientId(10L)).thenReturn(List.of(alergia));

        // Recetas vigentes
        PrescriptionDetailResponse det1 = new PrescriptionDetailResponse(
                "med-1", "MED-001", "Amoxicilina", "Amoxicilina", "Capsulas", "500mg",
                "1 cada 8 horas", "Oral", 7, 21, "Tomar con alimentos"
        );
        PrescriptionResponse recetaVigente = new PrescriptionResponse(
                "rec-1", "at-1", "pac-uuid-10", "Maria Gomez", "prof-1", "Dr. Perez",
                "Medicina General", 30, fixedInstant.minus(5, ChronoUnit.DAYS), List.of(det1)
        );
        when(prescriptionRepository.listByPatientId(10L, 0, 50)).thenReturn(List.of(recetaVigente));

        // Atenciones recientes
        ClinicalAttentionResponse atencion = new ClinicalAttentionResponse(
                "at-1", "cita-1", "pac-uuid-10", "Maria Gomez", "prof-1", "Dr. Perez",
                "Medicina General", "CERRADA", fixedInstant.minus(5, ChronoUnit.DAYS), fixedInstant.minus(5, ChronoUnit.DAYS),
                "J00", "Rinofaringitis aguda", "Congestion y dolor de garganta", "Evolucion adecuada",
                "Reposo e hidratacion", null, List.of()
        );
        when(attentionRepository.listPatientHistory(10L, 0, 5)).thenReturn(List.of(atencion));

        HealthSummaryResponse response = emergencySummaryService.getSummaryByToken(
                tokenPlano,
                new ConsultSummaryRequest(null),
                "200.1.2.3"
        );

        assertThat(response).isNotNull();
        assertThat(response.patient().fullName()).isEqualTo("Maria Gomez");
        assertThat(response.patient().documentType()).isEqualTo("CC");
        assertThat(response.patient().documentNumber()).isEqualTo("1020304050");
        assertThat(response.patient().age()).isEqualTo(30);
        assertThat(response.patient().phone()).isEqualTo("3101234567");
        assertThat(response.patient().email()).isEqualTo("paciente@test.com");

        assertThat(response.allergies()).hasSize(1);
        assertThat(response.allergies().get(0).substance()).isEqualTo("Penicilina");
        assertThat(response.allergies().get(0).severity()).isEqualTo("GRAVE");

        assertThat(response.activeMedications()).hasSize(1);
        assertThat(response.activeMedications().get(0).name()).isEqualTo("Amoxicilina");

        assertThat(response.recentAttentions()).hasSize(1);
        assertThat(response.recentAttentions().get(0).diagnosisCode()).isEqualTo("J00");
        assertThat(response.recentAttentions().get(0).diagnosisDescription()).isEqualTo("Rinofaringitis aguda");

        verify(temporaryQrAccessRepository).recordAccess(1L, fixedInstant);

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).audit(auditCaptor.capture());
        AuditEvent audit = auditCaptor.getValue();
        assertThat(audit.action()).isEqualTo(AuditableAction.ACCESO_EMERGENCIA_QR);
        assertThat(audit.resourcePublicId()).isEqualTo("qr-pub-1");
        assertThat(audit.sourceIp()).isEqualTo("200.1.2.3");
    }

    @Test
    @DisplayName("consultarResumenPorToken: con PIN numérico válido permite la consulta")
    void getSummaryByToken_withPinValid() {
        TemporaryQrAccess accesoConPin = new TemporaryQrAccess(
                2L, "qr-pub-2", 10L, tokenHash, "argon2id$hashed",
                true, true, true, true,
                3, 0, false,
                fixedInstant.plus(15, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );

        when(temporaryQrAccessRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(accesoConPin));
        when(passwordEncoder.matches("4321", "argon2id$hashed")).thenReturn(true);
        when(temporaryQrAccessRepository.recordAccess(2L, fixedInstant)).thenReturn(1);
        when(patientRepository.findById(10L)).thenReturn(Optional.of(paciente));
        when(userRepository.findById(5L)).thenReturn(Optional.of(usuario));

        HealthSummaryResponse response = emergencySummaryService.getSummaryByToken(
                tokenPlano,
                new ConsultSummaryRequest("4321"),
                "127.0.0.1"
        );

        assertThat(response).isNotNull();
        verify(temporaryQrAccessRepository).recordAccess(2L, fixedInstant);
    }

    @Test
    @DisplayName("consultarResumenPorToken: PIN incorrecto o ausente lanza CredencialesInvalidasException")
    void getSummaryByToken_pinIncorrect_throwsInvalidCredentialsException() {
        TemporaryQrAccess accesoConPin = new TemporaryQrAccess(
                2L, "qr-pub-2", 10L, tokenHash, "argon2id$hashed",
                true, true, true, true,
                3, 0, false,
                fixedInstant.plus(15, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );

        when(temporaryQrAccessRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(accesoConPin));
        when(passwordEncoder.matches("0000", "argon2id$hashed")).thenReturn(false);

        assertThatThrownBy(() -> emergencySummaryService.getSummaryByToken(
                tokenPlano,
                new ConsultSummaryRequest("0000"),
                "127.0.0.1"
        )).isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("El PIN de seguridad proporcionado es incorrecto");

        verify(temporaryQrAccessRepository).recordFailedPin(2L);
        verify(temporaryQrAccessRepository, never()).recordAccess(anyLong(), any());
    }

    @Test
    @DisplayName("consultarResumenPorToken: flags de alcance respetan las exclusiones del paciente")
    void getSummaryByToken_flagsExclusions() {
        TemporaryQrAccess accesoRestringido = new TemporaryQrAccess(
                3L, "qr-pub-3", 10L, tokenHash, null,
                false, false, false, false,
                3, 0, false,
                fixedInstant.plus(15, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );

        when(temporaryQrAccessRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(accesoRestringido));
        when(temporaryQrAccessRepository.recordAccess(3L, fixedInstant)).thenReturn(1);
        when(patientRepository.findById(10L)).thenReturn(Optional.of(paciente));
        when(userRepository.findById(5L)).thenReturn(Optional.of(usuario));

        HealthSummaryResponse response = emergencySummaryService.getSummaryByToken(
                tokenPlano,
                new ConsultSummaryRequest(null),
                "127.0.0.1"
        );

        assertThat(response.allergies()).isEmpty();
        assertThat(response.activeMedications()).isEmpty();
        assertThat(response.recentAttentions()).isEmpty();
        assertThat(response.patient().phone()).isNull();
        assertThat(response.patient().email()).isNull();

        verify(allergyRepository, never()).listByPatientId(anyLong());
        verify(prescriptionRepository, never()).listByPatientId(anyLong(), any(Integer.class), any(Integer.class));
        verify(attentionRepository, never()).listPatientHistory(anyLong(), any(Integer.class), any(Integer.class));
    }

    @Test
    @DisplayName("consultarResumenPorToken: recetas vencidas no se incluyen en medicamentos activos")
    void getSummaryByToken_prescriptionsExpiredNotIncluded() {
        TemporaryQrAccess acceso = new TemporaryQrAccess(
                4L, "qr-pub-4", 10L, tokenHash, null,
                false, true, false, false,
                3, 0, false,
                fixedInstant.plus(15, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );

        when(temporaryQrAccessRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(acceso));
        when(temporaryQrAccessRepository.recordAccess(4L, fixedInstant)).thenReturn(1);
        when(patientRepository.findById(10L)).thenReturn(Optional.of(paciente));
        when(userRepository.findById(5L)).thenReturn(Optional.of(usuario));

        // Receta emitida hace 40 días con vigencia de 30 días -> vencida
        PrescriptionDetailResponse detVencido = new PrescriptionDetailResponse(
                "med-old", "MED-OLD", "Ibuprofeno", "Ibuprofeno", "Tabletas", "400mg",
                "1 cada 8h", "Oral", 3, 9, "Tomar con agua"
        );
        PrescriptionResponse recetaVencida = new PrescriptionResponse(
                "rec-old", "at-old", "pac-uuid-10", "Maria Gomez", "prof-1", "Dr. Perez",
                "Medicina General", 30, fixedInstant.minus(40, ChronoUnit.DAYS), List.of(detVencido)
        );
        when(prescriptionRepository.listByPatientId(10L, 0, 50)).thenReturn(List.of(recetaVencida));

        HealthSummaryResponse response = emergencySummaryService.getSummaryByToken(
                tokenPlano,
                new ConsultSummaryRequest(null),
                "127.0.0.1"
        );

        assertThat(response.activeMedications()).isEmpty();
    }

    @Test
    @DisplayName("consultarResumenPorToken: token expirado lanza DatosInvalidosException")
    void getSummaryByToken_expired_throwsInvalidData() {
        TemporaryQrAccess expirado = new TemporaryQrAccess(
                5L, "qr-pub-5", 10L, tokenHash, null,
                true, true, true, true,
                3, 0, false,
                fixedInstant.minus(1, ChronoUnit.SECONDS), fixedInstant.minus(16, ChronoUnit.MINUTES), fixedInstant
        );

        when(temporaryQrAccessRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(expirado));

        assertThatThrownBy(() -> emergencySummaryService.getSummaryByToken(
                tokenPlano,
                new ConsultSummaryRequest(null),
                "127.0.0.1"
        )).isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("El acceso QR ha expirado.");
    }

    @Test
    @DisplayName("consultarResumenPorToken: token revocado lanza DatosInvalidosException")
    void getSummaryByToken_revoked_throwsInvalidData() {
        TemporaryQrAccess revocado = new TemporaryQrAccess(
                6L, "qr-pub-6", 10L, tokenHash, null,
                true, true, true, true,
                3, 0, true,
                fixedInstant.plus(10, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );

        when(temporaryQrAccessRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(revocado));

        assertThatThrownBy(() -> emergencySummaryService.getSummaryByToken(
                tokenPlano,
                new ConsultSummaryRequest(null),
                "127.0.0.1"
        )).isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("El acceso QR ha sido revocado por el paciente.");
    }

    @Test
    @DisplayName("consultarResumenPorToken: token con lecturas agotadas lanza DatosInvalidosException")
    void getSummaryByToken_agotado_throwsInvalidData() {
        TemporaryQrAccess agotado = new TemporaryQrAccess(
                7L, "qr-pub-7", 10L, tokenHash, null,
                true, true, true, true,
                3, 3, false,
                fixedInstant.plus(10, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );

        when(temporaryQrAccessRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(agotado));

        assertThatThrownBy(() -> emergencySummaryService.getSummaryByToken(
                tokenPlano,
                new ConsultSummaryRequest(null),
                "127.0.0.1"
        )).isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("superado el limite maximo de lecturas permitidas");
    }

    @Test
    @DisplayName("consultarResumenPorToken: token inexistente lanza RecursoNoEncontradoException")
    void getSummaryByToken_notFound_throwsResourceNotFound() {
        when(temporaryQrAccessRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emergencySummaryService.getSummaryByToken(
                "token-fantasma",
                new ConsultSummaryRequest(null),
                "127.0.0.1"
        )).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Acceso de emergencia no valido o inexistente.");
    }

    @Test
    @DisplayName("consultarResumenPorToken: alergias activas retornan con su origen clínico y autorreportado (D3, T4)")
    void getSummaryByToken_allergiesWithOrigin_success() {
        TemporaryQrAccess acceso = new TemporaryQrAccess(
                1L, "qr-pub-1", 10L, tokenHash, null,
                true, false, false, false,
                3, 0, false,
                fixedInstant.plus(15, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );

        when(temporaryQrAccessRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(acceso));
        when(temporaryQrAccessRepository.recordAccess(1L, fixedInstant)).thenReturn(1);
        when(patientRepository.findById(10L)).thenReturn(Optional.of(paciente));
        when(userRepository.findById(5L)).thenReturn(Optional.of(usuario));

        Allergy a1 = new Allergy(101L, "ale-1", 10L, "Penicilina", "Shock", "GRAVE", "ACTIVA", "PROFESIONAL", 1L, null, null, null, null, fixedInstant, fixedInstant);
        Allergy a2 = new Allergy(102L, "ale-2", 10L, "Mariscos", "Urticaria", "MODERADA", "ACTIVA", "PACIENTE", 5L, null, null, null, null, fixedInstant, fixedInstant);
        when(allergyRepository.listByPatientId(10L)).thenReturn(List.of(a1, a2));

        HealthSummaryResponse res = emergencySummaryService.getSummaryByToken(
                tokenPlano,
                new ConsultSummaryRequest(null),
                "127.0.0.1"
        );

        assertThat(res.allergies()).hasSize(2);
        assertThat(res.allergies().get(0).substance()).isEqualTo("Penicilina");
        assertThat(res.allergies().get(0).origin()).isEqualTo("PROFESIONAL");
        assertThat(res.allergies().get(1).substance()).isEqualTo("Mariscos");
        assertThat(res.allergies().get(1).origin()).isEqualTo("PACIENTE");
    }
}
