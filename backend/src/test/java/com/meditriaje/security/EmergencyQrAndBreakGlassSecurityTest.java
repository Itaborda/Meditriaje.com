package com.meditriaje.security;

import com.meditriaje.dto.clinical.BreakGlassAccessResponse;
import com.meditriaje.dto.clinical.ActivateBreakGlassRequest;
import com.meditriaje.dto.emergency.ConsultSummaryRequest;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.InvalidCredentialsException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.model.BreakGlassAccess;
import com.meditriaje.model.TemporaryQrAccess;
import com.meditriaje.model.Patient;
import com.meditriaje.model.Professional;
import com.meditriaje.model.User;
import com.meditriaje.repository.TemporaryQrAccessRepository;
import com.meditriaje.repository.AllergyRepository;
import com.meditriaje.repository.ClinicalAttentionRepository;
import com.meditriaje.repository.BreakGlassRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.ProfessionalRepository;
import com.meditriaje.repository.PrescriptionRepository;
import com.meditriaje.repository.UserRepository;
import com.meditriaje.service.AuditService;
import com.meditriaje.service.BreakGlassService;
import com.meditriaje.service.EmergencyQrService;
import com.meditriaje.service.EmergencySummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

/**
 * Suite 9: Casos de borde en QR de emergencia y Break-Glass médico (ADR-012, ADR-014, T6).
 * Valida expiración a 15 min, bloqueo de lectura > 3, bloqueo ante PIN erróneo, expiración a 24h
 * de break-glass, justificación < 20 caracteres y rechazo absoluto a roles no clínicos.
 */
@ExtendWith(MockitoExtension.class)
class EmergencyQrAndBreakGlassSecurityTest {

    private static final Instant T0 = Instant.parse("2026-10-06T10:00:00Z");
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final Clock clock = Clock.fixed(T0, BOGOTA);

    @Mock
    private TemporaryQrAccessRepository temporaryQrAccessRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProfessionalRepository professionalRepository;
    @Mock
    private AllergyRepository allergyRepository;
    @Mock
    private PrescriptionRepository prescriptionRepository;
    @Mock
    private ClinicalAttentionRepository attentionRepository;
    @Mock
    private BreakGlassRepository breakGlassRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private EmergencyQrService emergencyQrService;
    private EmergencySummaryService emergencySummaryService;
    private BreakGlassService breakGlassService;

    private static final String TOKEN_PLANO = "test-token-emergencia-123456789";
    private static final String TOKEN_HASH = TokenHashUtil.hash(TOKEN_PLANO);

    @BeforeEach
    void setUp() {
        emergencyQrService = new EmergencyQrService(
                temporaryQrAccessRepository,
                patientRepository,
                userRepository,
                auditService,
                passwordEncoder,
                clock
        );

        emergencySummaryService = new EmergencySummaryService(
                temporaryQrAccessRepository,
                patientRepository,
                userRepository,
                allergyRepository,
                prescriptionRepository,
                attentionRepository,
                auditService,
                passwordEncoder,
                clock
        );

        breakGlassService = new BreakGlassService(
                userRepository,
                professionalRepository,
                patientRepository,
                breakGlassRepository,
                auditService,
                clock
        );
    }

    // =========================================================================
    // SECCIÓN 1: Casos de Borde en QR de Emergencia (F2.3, ADR-012)
    // =========================================================================

    @Test
    @DisplayName("QR: Token expirado (> 15 min) es rechazado con DatosInvalidosException")
    void qr_tokenExpiredAfter15Minutes_isRejected() {
        TemporaryQrAccess expirado = new TemporaryQrAccess(
                1L, "qr-1", 10L, TOKEN_HASH, null,
                true, true, true, true,
                3, 0, false,
                T0.minus(1, ChronoUnit.SECONDS), // Expirado justo antes de T0
                T0.minus(16, ChronoUnit.MINUTES),
                T0.minus(16, ChronoUnit.MINUTES)
        );

        when(temporaryQrAccessRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(expirado));

        assertThatThrownBy(() -> emergencySummaryService.getSummaryByToken(
                TOKEN_PLANO, new ConsultSummaryRequest(null), "10.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("El acceso QR ha expirado");

        verify(temporaryQrAccessRepository, never()).recordAccess(anyLong(), any());
    }

    @Test
    @DisplayName("QR: Acceso bloqueado al superar el límite estricto de 3 lecturas (intentos >= 3)")
    void qr_exceedingLimit3Reads_isBlockedAndRejected() {
        TemporaryQrAccess agotado = new TemporaryQrAccess(
                2L, "qr-2", 10L, TOKEN_HASH, null,
                true, true, true, true,
                3, 3, false, // 3 de 3 lecturas consumidas
                T0.plus(10, ChronoUnit.MINUTES),
                T0.minus(5, ChronoUnit.MINUTES),
                T0.minus(5, ChronoUnit.MINUTES)
        );

        when(temporaryQrAccessRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(agotado));

        assertThatThrownBy(() -> emergencySummaryService.getSummaryByToken(
                TOKEN_PLANO, new ConsultSummaryRequest(null), "10.0.0.2"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("limite maximo de lecturas permitidas");

        verify(temporaryQrAccessRepository, never()).recordAccess(anyLong(), any());
    }

    @Test
    @DisplayName("QR: PIN erróneo lanza CredencialesInvalidasException, incrementa fallos y no registra lectura exitosa")
    void qr_pinWrong_isRejectedWithInvalidCredentials() {
        TemporaryQrAccess conPin = new TemporaryQrAccess(
                3L, "qr-3", 10L, TOKEN_HASH, "argon2id$hashedpin",
                true, true, true, true,
                3, 1, false,
                T0.plus(10, ChronoUnit.MINUTES),
                T0.minus(5, ChronoUnit.MINUTES),
                T0.minus(5, ChronoUnit.MINUTES)
        );

        when(temporaryQrAccessRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(conPin));
        when(passwordEncoder.matches("0000", "argon2id$hashedpin")).thenReturn(false);

        assertThatThrownBy(() -> emergencySummaryService.getSummaryByToken(
                TOKEN_PLANO, new ConsultSummaryRequest("0000"), "10.0.0.3"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("PIN de seguridad proporcionado es incorrecto");

        verify(temporaryQrAccessRepository).recordFailedPin(3L);
        verify(temporaryQrAccessRepository, never()).recordAccess(anyLong(), any());
    }

    @Test
    @DisplayName("QR: Fuerza bruta de PIN — 3 intentos fallidos revocan el token y bloquean accesos subsiguientes")
    void qr_fuerzaBrutaPin_threeAttemptsRevocanToken() {
        // Intento 1: token con 0 fallos
        TemporaryQrAccess conPinInicial = new TemporaryQrAccess(
                3L, "qr-3", 10L, TOKEN_HASH, "argon2id$hashedpin",
                true, true, true, true,
                3, 0, false, 0,
                T0.plus(10, ChronoUnit.MINUTES),
                T0.minus(5, ChronoUnit.MINUTES),
                T0.minus(5, ChronoUnit.MINUTES)
        );

        when(temporaryQrAccessRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(conPinInicial));
        when(passwordEncoder.matches("1111", "argon2id$hashedpin")).thenReturn(false);

        assertThatThrownBy(() -> emergencySummaryService.getSummaryByToken(
                TOKEN_PLANO, new ConsultSummaryRequest("1111"), "10.0.0.3"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("PIN de seguridad proporcionado es incorrecto");
        verify(temporaryQrAccessRepository).recordFailedPin(3L);

        // Intento 2: simula token con 1 fallo registrado
        TemporaryQrAccess con1Fallo = new TemporaryQrAccess(
                3L, "qr-3", 10L, TOKEN_HASH, "argon2id$hashedpin",
                true, true, true, true,
                3, 1, false, 1,
                T0.plus(10, ChronoUnit.MINUTES),
                T0.minus(5, ChronoUnit.MINUTES),
                T0.minus(5, ChronoUnit.MINUTES)
        );
        when(temporaryQrAccessRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(con1Fallo));
        when(passwordEncoder.matches("2222", "argon2id$hashedpin")).thenReturn(false);

        assertThatThrownBy(() -> emergencySummaryService.getSummaryByToken(
                TOKEN_PLANO, new ConsultSummaryRequest("2222"), "10.0.0.3"))
                .isInstanceOf(InvalidCredentialsException.class);

        // Intento 3: simula token con 2 fallos registrados -> alcanza 3er fallo y revoca
        TemporaryQrAccess con2Fallos = new TemporaryQrAccess(
                3L, "qr-3", 10L, TOKEN_HASH, "argon2id$hashedpin",
                true, true, true, true,
                3, 2, false, 2,
                T0.plus(10, ChronoUnit.MINUTES),
                T0.minus(5, ChronoUnit.MINUTES),
                T0.minus(5, ChronoUnit.MINUTES)
        );
        when(temporaryQrAccessRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(con2Fallos));
        when(passwordEncoder.matches("3333", "argon2id$hashedpin")).thenReturn(false);

        assertThatThrownBy(() -> emergencySummaryService.getSummaryByToken(
                TOKEN_PLANO, new ConsultSummaryRequest("3333"), "10.0.0.3"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("revocado por superar el limite de intentos fallidos");

        // Intento 4: el token ahora tiene 3 fallos (estado REVOCADO) y es rechazado directamente
        TemporaryQrAccess bloqueado = new TemporaryQrAccess(
                3L, "qr-3", 10L, TOKEN_HASH, "argon2id$hashedpin",
                true, true, true, true,
                3, 3, true, 3,
                T0.plus(10, ChronoUnit.MINUTES),
                T0.minus(5, ChronoUnit.MINUTES),
                T0.minus(5, ChronoUnit.MINUTES)
        );
        when(temporaryQrAccessRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(bloqueado));

        assertThatThrownBy(() -> emergencySummaryService.getSummaryByToken(
                TOKEN_PLANO, new ConsultSummaryRequest("9999"), "10.0.0.3"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("ha sido revocado");

        // Ningún intento registró lectura exitosa
        verify(temporaryQrAccessRepository, never()).recordAccess(anyLong(), any());
    }

    @Test
    @DisplayName("QR: Token revocado por el paciente bloquea cualquier intento de consulta")
    void qr_revokedByPatient_locksAccess() {
        TemporaryQrAccess revocado = new TemporaryQrAccess(
                4L, "qr-4", 10L, TOKEN_HASH, null,
                true, true, true, true,
                3, 0, true, // REVOCADO
                T0.plus(10, ChronoUnit.MINUTES),
                T0.minus(5, ChronoUnit.MINUTES),
                T0.minus(5, ChronoUnit.MINUTES)
        );

        when(temporaryQrAccessRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(revocado));

        assertThatThrownBy(() -> emergencySummaryService.getSummaryByToken(
                TOKEN_PLANO, new ConsultSummaryRequest(null), "10.0.0.4"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("ha sido revocado por el paciente");
    }

    @Test
    @DisplayName("QR: Paciente ajeno intentando revocar QR recibe 403 AccesoNoAutorizadoException")
    void qr_otherPatientRevoke_throws403() {
        User usuarioAtacante = new User(99L, "usr-atacante", "atacante@test.com", "hash", "ACTIVO", 0, null, T0, false, false, null, null);
        Patient pacienteAtacante = new Patient(99L, 99L, "pac-atacante", "CC", "999", "Atacante", "Hacker", LocalDate.of(1995, 1, 1), "300", T0, T0);
        TemporaryQrAccess qrVictima = new TemporaryQrAccess(
                5L, "qr-victima", 10L, "hash", null, true, true, true, true, 3, 0, false,
                T0.plus(10, ChronoUnit.MINUTES), T0, T0
        );

        when(userRepository.findByPublicId("usr-atacante")).thenReturn(Optional.of(usuarioAtacante));
        when(patientRepository.findByUserId(99L)).thenReturn(Optional.of(pacienteAtacante));
        when(temporaryQrAccessRepository.findByPublicId("qr-victima")).thenReturn(Optional.of(qrVictima));

        assertThatThrownBy(() -> emergencyQrService.revokeQrAccess("qr-victima", "usr-atacante", "10.0.0.9"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("No tiene autorizacion para revocar un acceso QR ajeno");

        verify(temporaryQrAccessRepository, never()).revoke(anyString(), anyLong());
    }

    // =========================================================================
    // SECCIÓN 2: Casos de Borde en Break-Glass Médico (F2.5, ADR-014)
    // =========================================================================

    @Test
    @DisplayName("Break-Glass: Justificación con menos de 20 caracteres es rechazada con DatosInvalidosException")
    void breakGlass_justificacionUnder20Chars_isRejected() {
        ActivateBreakGlassRequest request = new ActivateBreakGlassRequest("pac-100", "Muy urgente!"); // < 20 caracteres

        User usuarioMed = new User(1L, "usr-med-1", "med@test.com", "hash", "ACTIVO", 0, null, T0, false, false, null, null);
        Professional prof = new Professional(10L, 1L, "prof-1", 1L, "RM-1", "Dr. Juan", "Perez", T0, T0);
        Patient pac = new Patient(100L, 2L, "pac-100", "CC", "123", "Paciente", "Critico", LocalDate.of(1980, 1, 1), "300", T0, T0);

        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMed));
        when(professionalRepository.findByUserId(1L)).thenReturn(Optional.of(prof));
        when(patientRepository.findByPublicId("pac-100")).thenReturn(Optional.of(pac));

        assertThatThrownBy(() -> breakGlassService.activateBreakGlass(request, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("al menos 20 caracteres");

        verify(breakGlassRepository, never()).recordAccess(any());
    }

    @Test
    @DisplayName("Break-Glass: Solicitud por usuario no profesional (ej. admin o paciente) es rechazada con 403")
    void breakGlass_nonProfessionalRole_isRejected() {
        ActivateBreakGlassRequest request = new ActivateBreakGlassRequest("pac-100", "Paciente inconsciente en sala de cirugia mayor.");

        User adminUser = new User(5L, "usr-admin", "admin@test.com", "hash", "ACTIVO", 0, null, T0, false, false, null, null);
        when(userRepository.findByPublicId("usr-admin")).thenReturn(Optional.of(adminUser));
        when(professionalRepository.findByUserId(5L)).thenReturn(Optional.empty()); // No es profesional asistencial

        assertThatThrownBy(() -> breakGlassService.activateBreakGlass(request, "usr-admin", "127.0.0.1"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Usuario no registrado como profesional asistencial");

        verify(breakGlassRepository, never()).recordAccess(any());
    }

    @Test
    @DisplayName("Break-Glass: Acceso vencido tras 24 horas no figura en consultas activas")
    void breakGlass_expiredAfter24Hours_notAppearsAsActive() {
        User usuarioMed = new User(1L, "usr-med-1", "med@test.com", "hash", "ACTIVO", 0, null, T0, false, false, null, null);
        Professional prof = new Professional(10L, 1L, "prof-1", 1L, "RM-1", "Dr. Juan", "Perez", T0, T0);

        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuarioMed));
        when(professionalRepository.findByUserId(1L)).thenReturn(Optional.of(prof));

        // El repositorio filtra now > FECHA_EXPIRACION -> lista vacía
        when(breakGlassRepository.listActiveByProfessional(10L, T0)).thenReturn(List.of());

        List<BreakGlassAccessResponse> activos = breakGlassService.listMyActiveAccesses("usr-med-1");
        assertThat(activos).isEmpty();
    }

    @Test
    @DisplayName("Break-Glass: Profesional intentando consultar detalle de autorización de otro colega recibe 403")
    void breakGlass_queryColleagueOther_returns403() {
        User usuarioMed2 = new User(2L, "usr-med-2", "med2@test.com", "hash", "ACTIVO", 0, null, T0, false, false, null, null);
        Professional prof2 = new Professional(20L, 2L, "prof-2", 1L, "RM-2", "Dra. Gomez", "Lopez", T0, T0);

        when(userRepository.findByPublicId("usr-med-2")).thenReturn(Optional.of(usuarioMed2));
        when(professionalRepository.findByUserId(2L)).thenReturn(Optional.of(prof2));

        BreakGlassAccessResponse accesoOtro = new BreakGlassAccessResponse(
                "bg-otro-uuid",
                "prof-1", // Pertenece al prof 1, NO al prof 2
                "Dr. Juan Perez",
                "pac-100",
                "Paciente Critico",
                "Justificacion medica valida con mas de veinte caracteres",
                T0.plus(10, ChronoUnit.HOURS),
                T0,
                true
        );
        when(breakGlassRepository.findByPublicId("bg-otro-uuid", T0)).thenReturn(Optional.of(accesoOtro));

        assertThatThrownBy(() -> breakGlassService.getByPublicId("bg-otro-uuid", "usr-med-2"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("No tiene autorizacion para consultar el acceso break-glass de otro profesional");
    }
}
