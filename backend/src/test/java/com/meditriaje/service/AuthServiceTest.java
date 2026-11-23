package com.meditriaje.service;

import com.meditriaje.dto.AuthTokens;
import com.meditriaje.dto.ChangePasswordRequest;
import com.meditriaje.dto.LoginRequest;
import com.meditriaje.dto.RegisterPatientRequest;
import com.meditriaje.dto.RegisterPatientResponse;
import com.meditriaje.exception.InvalidCredentialsException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.InvalidTokenException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.RefreshToken;
import com.meditriaje.model.AuditResult;
import com.meditriaje.model.User;
import com.meditriaje.repository.ConsentRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.RefreshTokenRepository;
import com.meditriaje.repository.UserRepository;
import com.meditriaje.security.JwtService;
import com.meditriaje.security.TokenHashUtil;
import com.meditriaje.service.email.EmailSendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import com.meditriaje.dto.auth.MfaAuthenticateRequest;
import com.meditriaje.dto.auth.MfaSetupResponse;
import com.meditriaje.dto.auth.MfaVerifyRequest;
import com.meditriaje.dto.auth.MfaVerifyResponse;
import com.meditriaje.dto.auth.ResetPasswordRequest;
import com.meditriaje.dto.auth.ResetPasswordResponse;
import com.meditriaje.dto.auth.RequestPasswordResetRequest;
import com.meditriaje.dto.auth.RequestPasswordResetResponse;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.model.VerificationCode;
import com.meditriaje.model.Patient;
import com.meditriaje.repository.VerificationCodeRepository;
import com.meditriaje.repository.MfaBackupCodeRepository;
import com.meditriaje.repository.ProfessionalRepository;
import com.meditriaje.security.TotpService;
import com.meditriaje.service.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

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
    private JwtService jwtService;

    @Mock
    private TotpService totpService;

    @Mock
    private EmailService emailService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
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
                totpService,
                emailService,
                7
        );
    }

    private RegisterPatientRequest requestValido() {
        return new RegisterPatientRequest(
                "CC",
                "1020304050",
                "Carlos",
                "Perez",
                LocalDate.of(1995, 5, 20),
                "3001234567",
                "carlos.perez@example.com",
                "Segura12345*",
                "v1.0",
                true
        );
    }

    // -------------------------------------------------------------------------
    // REGISTRO
    // -------------------------------------------------------------------------

    @Test
    void registerPatient_success_createsUserPatientConsentAndAudits() {
        RegisterPatientRequest req = requestValido();

        when(userRepository.existsByEmail("carlos.perez@example.com")).thenReturn(false);
        when(patientRepository.existsByDocument("CC", "1020304050")).thenReturn(false);
        when(passwordEncoder.encode("Segura12345*")).thenReturn("$argon2id$encoded");
        when(userRepository.create(anyString(), eq("carlos.perez@example.com"), eq("$argon2id$encoded"))).thenReturn(100L);
        when(userRepository.findRoleIdByName("ROLE_PACIENTE")).thenReturn(Optional.of(1L));
        when(patientRepository.create(eq(100L), anyString(), eq("CC"), eq("1020304050"), eq("Carlos"), eq("Perez"), eq(LocalDate.of(1995, 5, 20)), eq("3001234567"))).thenReturn(200L);
        when(consentRepository.record(eq(100L), eq("v1.0"), eq(true), eq("192.168.1.5"))).thenReturn(300L);

        RegisterPatientResponse res = authService.registerPatient(req, "192.168.1.5");

        assertThat(res).isNotNull();
        assertThat(res.email()).isEqualTo("carlos.perez@example.com");
        assertThat(res.patientPublicId()).isNotBlank();
        assertThat(res.firstNames()).isEqualTo("Carlos");
        assertThat(res.lastNames()).isEqualTo("Perez");

        verify(userRepository).assignRole(100L, 1L);
        verify(auditService).recordEvent(
                eq(100L),
                eq(AuditableAction.REGISTRO_PACIENTE),
                eq("PACIENTE"),
                anyString(),
                eq(AuditResult.EXITO),
                eq("192.168.1.5")
        );
    }

    @Test
    void registerPatient_emailDuplicate_throwsExceptionAndAuditsFailure() {
        RegisterPatientRequest req = requestValido();
        when(userRepository.existsByEmail("carlos.perez@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerPatient(req, "192.168.1.5"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("correo electronico ya se encuentra registrado");

        verify(auditService).recordEvent(
                eq(AuditableAction.REGISTRO_PACIENTE),
                eq("USUARIO"),
                isNull(),
                eq(AuditResult.FALLO),
                eq("192.168.1.5")
        );
        verify(userRepository, never()).create(anyString(), anyString(), anyString());
    }

    @Test
    void registerPatient_linksExistingWalkInPatientWithoutUserAccount() {
        RegisterPatientRequest req = requestValido();
        Patient walkInExistente = new Patient(
                200L, null, "pac-walkin-pub", "CC", "1020304050",
                "Carlos", "Perez", LocalDate.of(1995, 5, 20), "3001234567",
                Instant.now(), null
        );
        when(userRepository.existsByEmail("carlos.perez@example.com")).thenReturn(false);
        when(patientRepository.existsByDocument("CC", "1020304050")).thenReturn(true);
        when(patientRepository.findByDocument("CC", "1020304050")).thenReturn(Optional.of(walkInExistente));
        when(passwordEncoder.encode(anyString())).thenReturn("argon2id-hash");
        when(userRepository.create(anyString(), eq("carlos.perez@example.com"), eq("argon2id-hash"))).thenReturn(100L);
        when(userRepository.findRoleIdByName("ROLE_PACIENTE")).thenReturn(Optional.of(1L));
        when(patientRepository.linkUserToPatient(200L, 100L)).thenReturn(1);
        when(consentRepository.record(eq(100L), eq("v1.0"), eq(true), eq("192.168.1.5"))).thenReturn(300L);

        RegisterPatientResponse res = authService.registerPatient(req, "192.168.1.5");

        assertThat(res.patientPublicId()).isEqualTo("pac-walkin-pub");
        verify(patientRepository).linkUserToPatient(200L, 100L);
        verify(patientRepository, never()).create(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void registerPatient_documentDuplicate_throwsExceptionAndAuditsFailure() {
        RegisterPatientRequest req = requestValido();
        when(userRepository.existsByEmail("carlos.perez@example.com")).thenReturn(false);
        when(patientRepository.existsByDocument("CC", "1020304050")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerPatient(req, "192.168.1.5"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("documento de identidad ya se encuentra registrado");

        verify(auditService).recordEvent(
                eq(AuditableAction.REGISTRO_PACIENTE),
                eq("PACIENTE"),
                isNull(),
                eq(AuditResult.FALLO),
                eq("192.168.1.5")
        );
        verify(userRepository, never()).create(anyString(), anyString(), anyString());
    }

    @Test
    void registerPatient_documentAlreadyLinkedToUser_throwsException() {
        RegisterPatientRequest req = requestValido();
        Patient existenteConUsuario = new Patient(
                200L, 99L, "pac-con-usuario", "CC", "1020304050",
                "Carlos", "Perez", LocalDate.of(1995, 5, 20), "3001234567",
                Instant.now(), null
        );
        when(userRepository.existsByEmail("carlos.perez@example.com")).thenReturn(false);
        when(patientRepository.existsByDocument("CC", "1020304050")).thenReturn(true);
        when(patientRepository.findByDocument("CC", "1020304050")).thenReturn(Optional.of(existenteConUsuario));

        assertThatThrownBy(() -> authService.registerPatient(req, "192.168.1.5"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("documento de identidad ya se encuentra registrado");
        verify(userRepository, never()).create(anyString(), anyString(), anyString());
    }

    @Test
    void registerPatient_withoutConsent_throwsExceptionAndAuditsFailure() {
        RegisterPatientRequest req = new RegisterPatientRequest(
                "CC", "1020304050", "Carlos", "Perez",
                LocalDate.of(1995, 5, 20), null, "carlos.perez@example.com",
                "Segura12345*", "v1.0", false
        );

        assertThatThrownBy(() -> authService.registerPatient(req, "192.168.1.5"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("consentimiento informado");

        verify(auditService).recordEvent(
                eq(AuditableAction.REGISTRO_PACIENTE),
                eq("USUARIO"),
                isNull(),
                eq(AuditResult.FALLO),
                eq("192.168.1.5")
        );
    }

    @Test
    void registerPatient_citizenshipIdWithUnderOfAge_throwsExceptionAndAuditsFailure() {
        // Paciente con 16 años intentando registrarse con Cédula de Ciudadanía
        RegisterPatientRequest req = new RegisterPatientRequest(
                "CC", "1020304050", "Carlos", "Perez",
                LocalDate.now().minusYears(16), "3001234567", "carlos.perez@example.com",
                "Segura12345*", "v1.0", true
        );

        assertThatThrownBy(() -> authService.registerPatient(req, "192.168.1.5"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Cedula de Ciudadania (CC) solo es valida para personas mayores de 18 anos");

        verify(auditService).recordEvent(
                eq(AuditableAction.REGISTRO_PACIENTE),
                eq("USUARIO"),
                isNull(),
                eq(AuditResult.FALLO),
                eq("192.168.1.5")
        );
        verify(userRepository, never()).create(anyString(), anyString(), anyString());
    }

    @Test
    void registerPatient_numberDocumentInvalid_throwsExceptionAndAuditsFailure() {
        // Cédula con letras
        RegisterPatientRequest req = new RegisterPatientRequest(
                "CC", "10203A4050", "Carlos", "Perez",
                LocalDate.of(1995, 5, 20), "3001234567", "carlos.perez@example.com",
                "Segura12345*", "v1.0", true
        );

        assertThatThrownBy(() -> authService.registerPatient(req, "192.168.1.5"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Cedula de Ciudadania (CC) debe contener entre 6 y 10 digitos");

        verify(auditService).recordEvent(
                eq(AuditableAction.REGISTRO_PACIENTE),
                eq("USUARIO"),
                isNull(),
                eq(AuditResult.FALLO),
                eq("192.168.1.5")
        );
    }

    @Test
    void registerPatient_mobileInvalid_throwsExceptionAndAuditsFailure() {
        // Teléfono que no es celular colombiano (fijo o formato inválido)
        RegisterPatientRequest req = new RegisterPatientRequest(
                "CC", "1020304050", "Carlos", "Perez",
                LocalDate.of(1995, 5, 20), "6011234567", "carlos.perez@example.com",
                "Segura12345*", "v1.0", true
        );

        assertThatThrownBy(() -> authService.registerPatient(req, "192.168.1.5"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("celular colombiano valido de 10 digitos (iniciando por 3)");

        verify(auditService).recordEvent(
                eq(AuditableAction.REGISTRO_PACIENTE),
                eq("USUARIO"),
                isNull(),
                eq(AuditResult.FALLO),
                eq("192.168.1.5")
        );
    }

    @Test
    void registerPatient_namesWithCharsEspeciales_throwsExceptionAndAuditsFailure() {
        RegisterPatientRequest req = new RegisterPatientRequest(
                "CC", "1020304050", "Carlos123", "Perez",
                LocalDate.of(1995, 5, 20), "3001234567", "carlos.perez@example.com",
                "Segura12345*", "v1.0", true
        );

        assertThatThrownBy(() -> authService.registerPatient(req, "192.168.1.5"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("solo puede contener letras, espacios, tildes y guiones");

        verify(auditService).recordEvent(
                eq(AuditableAction.REGISTRO_PACIENTE),
                eq("USUARIO"),
                isNull(),
                eq(AuditResult.FALLO),
                eq("192.168.1.5")
        );
    }

    // -------------------------------------------------------------------------
    // LOGIN
    // -------------------------------------------------------------------------

    @Test
    void login_exito_emiteTokensYAudita() {
        LoginRequest req = new LoginRequest("carlos.perez@example.com", "Segura12345*");
        User usuario = new User(
                10L,
                "user-pub-id-1",
                "carlos.perez@example.com",
                "$argon2id$hashed",
                "ACTIVO",
                0,
                null,
                Instant.now()
        );

        when(userRepository.findByEmail("carlos.perez@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Segura12345*", "$argon2id$hashed")).thenReturn(true);
        when(userRepository.getRoles(10L)).thenReturn(List.of("ROLE_PACIENTE"));
        when(jwtService.generateAccessToken(eq("user-pub-id-1"), eq("carlos.perez@example.com"), eq(List.of("ROLE_PACIENTE"))))
                .thenReturn("mock.access.jwt");

        AuthTokens resultado = authService.login(req, "192.168.1.10");

        assertThat(resultado).isNotNull();
        assertThat(resultado.accessToken()).isEqualTo("mock.access.jwt");
        assertThat(resultado.rawRefreshToken()).isNotBlank();
        assertThat(resultado.sessionResponse().email()).isEqualTo("carlos.perez@example.com");
        assertThat(resultado.sessionResponse().roles()).containsExactly("ROLE_PACIENTE");

        verify(refreshTokenRepository).create(eq(10L), anyString(), any(Instant.class));
        verify(auditService).recordEvent(
                eq(10L),
                eq(AuditableAction.LOGIN_EXITOSO),
                eq("USUARIO"),
                eq("user-pub-id-1"),
                eq(AuditResult.EXITO),
                eq("192.168.1.10")
        );
    }

    @Test
    void login_emailInexistente_throwsInvalidCredentialsAndAuditsFailure() {
        LoginRequest req = new LoginRequest("desconocido@example.com", "CualquierClave1*");
        when(userRepository.findByEmail("desconocido@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req, "192.168.1.10"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciales invalidas.");

        verify(auditService).recordEvent(
                eq(AuditableAction.LOGIN_FALLIDO),
                eq("USUARIO"),
                isNull(),
                eq(AuditResult.FALLO),
                eq("192.168.1.10")
        );
        verify(refreshTokenRepository, never()).create(anyLong(), anyString(), any());
    }

    @Test
    void login_passwordWrong_incrementsAttemptsAndAuditsFailure() {
        LoginRequest req = new LoginRequest("carlos.perez@example.com", "ClaveErronea1*");
        User usuario = new User(
                10L,
                "user-pub-id-1",
                "carlos.perez@example.com",
                "$argon2id$hashed",
                "ACTIVO",
                2, // 2 previos
                null,
                Instant.now()
        );

        when(userRepository.findByEmail("carlos.perez@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("ClaveErronea1*", "$argon2id$hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req, "192.168.1.10"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciales invalidas.");

        verify(userRepository).updateFailedAttempts(10L, 3, null, "ACTIVO");
        verify(auditService).recordEvent(
                eq(10L),
                eq(AuditableAction.LOGIN_FALLIDO),
                eq("USUARIO"),
                eq("user-pub-id-1"),
                eq(AuditResult.FALLO),
                eq("192.168.1.10")
        );
    }

    @Test
    void login_fifthFailureConsecutivo_locksCuentaTemporarily() {
        LoginRequest req = new LoginRequest("carlos.perez@example.com", "ClaveErronea1*");
        User usuario = new User(
                10L,
                "user-pub-id-1",
                "carlos.perez@example.com",
                "$argon2id$hashed",
                "ACTIVO",
                4, // 4 previos -> este será el 5to
                null,
                Instant.now()
        );

        when(userRepository.findByEmail("carlos.perez@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("ClaveErronea1*", "$argon2id$hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req, "192.168.1.10"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciales invalidas.");

        verify(userRepository).updateFailedAttempts(eq(10L), eq(5), any(Instant.class), eq("BLOQUEADO"));
        verify(auditService).recordEvent(
                eq(10L),
                eq(AuditableAction.LOGIN_FALLIDO),
                eq("USUARIO"),
                eq("user-pub-id-1"),
                eq(AuditResult.BLOQUEADO),
                eq("192.168.1.10")
        );
    }

    @Test
    void login_cuentaLocked_rejectsImmediatelyWithoutCheckingPassword() {
        LoginRequest req = new LoginRequest("carlos.perez@example.com", "Segura12345*");
        User usuario = new User(
                10L,
                "user-pub-id-1",
                "carlos.perez@example.com",
                "$argon2id$hashed",
                "BLOQUEADO",
                5,
                Instant.now().plus(10, ChronoUnit.MINUTES), // Le faltan 10 minutos de bloqueo
                Instant.now()
        );

        when(userRepository.findByEmail("carlos.perez@example.com")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.login(req, "192.168.1.10"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciales invalidas.");

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(auditService).recordEvent(
                eq(10L),
                eq(AuditableAction.LOGIN_FALLIDO),
                eq("USUARIO"),
                eq("user-pub-id-1"),
                eq(AuditResult.BLOQUEADO),
                eq("192.168.1.10")
        );
    }

    // -------------------------------------------------------------------------
    // REFRESH & ROTACIÓN
    // -------------------------------------------------------------------------

    @Test
    void refresh_success_rotaTokenAndRetornaNewSession() {
        String rawToken = "raw-refresh-token-xyz";
        String tokenHash = TokenHashUtil.hash(rawToken);

        RefreshToken refreshToken = new RefreshToken(
                55L,
                10L,
                tokenHash,
                Instant.now().plus(6, ChronoUnit.DAYS),
                false,
                Instant.now()
        );

        User usuario = new User(
                10L,
                "user-pub-id-1",
                "carlos.perez@example.com",
                "$argon2id$hashed",
                "ACTIVO",
                0,
                null,
                Instant.now()
        );

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(refreshToken));
        when(userRepository.findById(10L)).thenReturn(Optional.of(usuario));
        when(userRepository.getRoles(10L)).thenReturn(List.of("ROLE_PACIENTE"));
        when(jwtService.generateAccessToken(eq("user-pub-id-1"), eq("carlos.perez@example.com"), eq(List.of("ROLE_PACIENTE"))))
                .thenReturn("new.access.jwt");

        AuthTokens resultado = authService.refresh(rawToken, "192.168.1.10");

        assertThat(resultado).isNotNull();
        assertThat(resultado.accessToken()).isEqualTo("new.access.jwt");
        assertThat(resultado.rawRefreshToken()).isNotBlank();
        assertThat(resultado.rawRefreshToken()).isNotEqualTo(rawToken);

        // Se revoca el token anterior y se persiste el nuevo rotativo
        verify(refreshTokenRepository).revoke(55L);
        verify(refreshTokenRepository).create(eq(10L), anyString(), any(Instant.class));
    }

    @Test
    void refresh_tokenAlreadyRevoked_activeMitigationTheftAndRevocaTodosTheTokens() {
        String rawToken = "stolen-refresh-token";
        String tokenHash = TokenHashUtil.hash(rawToken);

        RefreshToken refreshToken = new RefreshToken(
                55L,
                10L,
                tokenHash,
                Instant.now().plus(6, ChronoUnit.DAYS),
                true, // Ya revocado
                Instant.now()
        );

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(refreshToken));

        assertThatThrownBy(() -> authService.refresh(rawToken, "192.168.1.10"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("deteccion de reuso");

        // Revocación masiva de todas las sesiones activas del usuario comprometido
        verify(refreshTokenRepository).revokeAllByUser(10L);
        verify(auditService).recordEvent(
                eq(10L),
                eq(AuditableAction.LOGIN_FALLIDO),
                eq("REFRESH_TOKEN"),
                eq("55"),
                eq(AuditResult.BLOQUEADO),
                eq("192.168.1.10")
        );
    }

    @Test
    void refresh_tokenExpired_revocaAndRechaza() {
        String rawToken = "expired-refresh-token";
        String tokenHash = TokenHashUtil.hash(rawToken);

        RefreshToken refreshToken = new RefreshToken(
                55L,
                10L,
                tokenHash,
                Instant.now().minus(1, ChronoUnit.DAYS), // Expirado ayer
                false,
                Instant.now()
        );

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(refreshToken));

        assertThatThrownBy(() -> authService.refresh(rawToken, "192.168.1.10"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expirado");

        verify(refreshTokenRepository).revoke(55L);
    }

    // -------------------------------------------------------------------------
    // LOGOUT
    // -------------------------------------------------------------------------

    @Test
    void logout_withTokenValid_revocaAndAudits() {
        String rawToken = "active-refresh-token";
        String tokenHash = TokenHashUtil.hash(rawToken);

        RefreshToken refreshToken = new RefreshToken(
                55L,
                10L,
                tokenHash,
                Instant.now().plus(5, ChronoUnit.DAYS),
                false,
                Instant.now()
        );
        User usuario = new User(
                10L,
                "user-pub-id-1",
                "carlos.perez@example.com",
                "$argon2id$hashed",
                "ACTIVO",
                0,
                null,
                Instant.now()
        );

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(refreshToken));
        when(userRepository.findById(10L)).thenReturn(Optional.of(usuario));

        authService.logout(rawToken, "192.168.1.10");

        verify(refreshTokenRepository).revoke(55L);
        verify(auditService).recordEvent(
                eq(10L),
                eq(AuditableAction.LOGOUT),
                eq("USUARIO"),
                eq("user-pub-id-1"),
                eq(AuditResult.EXITO),
                eq("192.168.1.10")
        );
    }

    // -------------------------------------------------------------------------
    // CAMBIO DE CONTRASEÑA
    // -------------------------------------------------------------------------

    @Test
    void changePassword_withDataValid_updatesPasswordAndAudits() {
        String userPubId = "user-pub-id-1";
        User usuario = new User(
                10L, userPubId, "carlos@hospital.com", "$argon2id$oldhash", "ACTIVO", 0, null, Instant.now(), true
        );
        ChangePasswordRequest req = new ChangePasswordRequest("PasswordActual123*", "PasswordNuevo123*");

        when(userRepository.findByPublicId(userPubId)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("PasswordActual123*", "$argon2id$oldhash")).thenReturn(true);
        when(passwordEncoder.matches("PasswordNuevo123*", "$argon2id$oldhash")).thenReturn(false);
        when(passwordEncoder.encode("PasswordNuevo123*")).thenReturn("$argon2id$newhash");

        authService.changePassword(userPubId, req, "192.168.1.10");

        verify(userRepository).updatePassword(10L, "$argon2id$newhash", false);
        verify(auditService).recordEvent(
                eq(10L),
                eq(AuditableAction.CAMBIO_PASSWORD),
                eq("USUARIO"),
                eq(userPubId),
                eq(AuditResult.EXITO),
                eq("192.168.1.10")
        );
    }

    @Test
    void changePassword_passwordCurrentIncorrect_throwsInvalidCredentialsAndAuditsFailure() {
        String userPubId = "user-pub-id-1";
        User usuario = new User(
                10L, userPubId, "carlos@hospital.com", "$argon2id$oldhash", "ACTIVO", 0, null, Instant.now(), true
        );
        ChangePasswordRequest req = new ChangePasswordRequest("PasswordErroneo123*", "PasswordNuevo123*");

        when(userRepository.findByPublicId(userPubId)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("PasswordErroneo123*", "$argon2id$oldhash")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(userPubId, req, "192.168.1.10"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Credenciales invalidas");

        verify(userRepository, never()).updatePassword(anyLong(), anyString(), anyBoolean());
        verify(auditService).recordEvent(
                eq(10L),
                eq(AuditableAction.CAMBIO_PASSWORD),
                eq("USUARIO"),
                eq(userPubId),
                eq(AuditResult.FALLO),
                eq("192.168.1.10")
        );
    }

    @Test
    void changePassword_passwordNewEqualToCurrent_throwsInvalidDataExceptionAndAuditsFailure() {
        String userPubId = "user-pub-id-1";
        User usuario = new User(
                10L, userPubId, "carlos@hospital.com", "$argon2id$oldhash", "ACTIVO", 0, null, Instant.now(), true
        );
        ChangePasswordRequest req = new ChangePasswordRequest("PasswordActual123*", "PasswordActual123*");

        when(userRepository.findByPublicId(userPubId)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("PasswordActual123*", "$argon2id$oldhash")).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(userPubId, req, "192.168.1.10"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("no puede ser igual a la anterior");

        verify(userRepository, never()).updatePassword(anyLong(), anyString(), anyBoolean());
        verify(auditService).recordEvent(
                eq(10L),
                eq(AuditableAction.CAMBIO_PASSWORD),
                eq("USUARIO"),
                eq(userPubId),
                eq(AuditResult.FALLO),
                eq("192.168.1.10")
        );
    }

    @Test
    void changePassword_userNotFound_throwsInvalidCredentialsException() {
        String userPubId = "user-no-existe";
        ChangePasswordRequest req = new ChangePasswordRequest("PasswordActual123*", "PasswordNuevo123*");

        when(userRepository.findByPublicId(userPubId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changePassword(userPubId, req, "192.168.1.10"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository, never()).updatePassword(anyLong(), anyString(), anyBoolean());
    }

    @Test
    void requestPasswordReset_userExists_generatesCodeSendsEmailAndAuditsExito() {
        String email = "carlos@hospital.com";
        User usuario = new User(
                10L, "usr-pub-1", email, "$argon2id$hash", "ACTIVO", 0, null, Instant.now(), false
        );
        Patient paciente = new Patient(
                5L, 10L, "pac-pub-1", "CC", "12345678", "Carlos", "Perez",
                LocalDate.of(1990, 1, 1), "3001234567", Instant.now(), null
        );

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(usuario));
        when(patientRepository.findByUserId(10L)).thenReturn(Optional.of(paciente));

        RequestPasswordResetRequest req = new RequestPasswordResetRequest(email);
        RequestPasswordResetResponse resp = authService.requestPasswordReset(req, "192.168.1.50");

        assertThat(resp.message()).isEqualTo(RequestPasswordResetResponse.DEFAULT_MESSAGE);

        verify(verificationCodeRepository).invalidatePreviousCodes(10L, VerificationCode.PASSWORD_RECOVERY_TYPE);
        verify(verificationCodeRepository).create(any(VerificationCode.class));
        verify(emailService).sendRecoveryCode(eq(email), eq("Carlos"), anyString(), eq(15));
        verify(auditService).recordEvent(
                eq(10L),
                eq(AuditableAction.SOLICITUD_RECUPERACION_PASSWORD),
                eq("USUARIO"),
                eq("usr-pub-1"),
                eq(AuditResult.EXITO),
                eq("192.168.1.50")
        );
    }

    @Test
    void requestPasswordReset_failureSendEmail_responde200GenericAndAuditsEmailFailed() {
        String email = "carlos@hospital.com";
        User usuario = new User(
                10L, "usr-pub-1", email, "$argon2id$hash", "ACTIVO", 0, null, Instant.now(), false
        );
        Patient paciente = new Patient(
                5L, 10L, "pac-pub-1", "CC", "12345678", "Carlos", "Perez",
                LocalDate.of(1990, 1, 1), "3001234567", Instant.now(), null
        );

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(usuario));
        when(patientRepository.findByUserId(10L)).thenReturn(Optional.of(paciente));
        when(emailService.sendRecoveryCode(eq(email), eq("Carlos"), anyString(), eq(15)))
                .thenReturn(EmailSendResult.failed("BREVO_401", "Clave de API inválida"));

        RequestPasswordResetRequest req = new RequestPasswordResetRequest(email);
        RequestPasswordResetResponse resp = authService.requestPasswordReset(req, "192.168.1.50");

        assertThat(resp.message()).isEqualTo(RequestPasswordResetResponse.DEFAULT_MESSAGE);

        verify(auditService).recordEvent(
                eq(10L),
                eq(AuditableAction.EMAIL_FALLIDO),
                eq("USUARIO"),
                eq("usr-pub-1"),
                eq(AuditResult.FALLO),
                eq("192.168.1.50")
        );

        verify(auditService).recordEvent(
                eq(10L),
                eq(AuditableAction.SOLICITUD_RECUPERACION_PASSWORD),
                eq("USUARIO"),
                eq("usr-pub-1"),
                eq(AuditResult.EXITO),
                eq("192.168.1.50")
        );
    }

    @Test
    void requestPasswordReset_userNotExists_returnsMessageGenericWithoutSendEmail() {
        String email = "fantasma@hospital.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        RequestPasswordResetRequest req = new RequestPasswordResetRequest(email);
        RequestPasswordResetResponse resp = authService.requestPasswordReset(req, "192.168.1.50");

        assertThat(resp.message()).isEqualTo(RequestPasswordResetResponse.DEFAULT_MESSAGE);

        verify(verificationCodeRepository, never()).create(any());
        verify(emailService, never()).sendRecoveryCode(anyString(), anyString(), anyString(), anyInt());
        verify(auditService).recordEvent(
                eq(AuditableAction.SOLICITUD_RECUPERACION_PASSWORD),
                eq("USUARIO"),
                isNull(),
                eq(AuditResult.EXITO),
                eq("192.168.1.50")
        );
    }

    @Test
    void resetPassword_success_updatesHashRevocaSesionesAndAuditsExito() {
        String email = "carlos@hospital.com";
        String rawCode = "654321";
        String codeHash = TokenHashUtil.hash(rawCode);
        User usuario = new User(
                10L, "usr-pub-1", email, "$argon2id$oldhash", "ACTIVO", 0, null, Instant.now(), false
        );
        VerificationCode codigo = new VerificationCode(
                1L, "cod-pub-1", 10L, VerificationCode.PASSWORD_RECOVERY_TYPE,
                codeHash, Instant.now().plus(10, ChronoUnit.MINUTES), 0, 3, false, Instant.now()
        );

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(usuario));
        when(verificationCodeRepository.findLatestPendingByUserAndType(10L, VerificationCode.PASSWORD_RECOVERY_TYPE))
                .thenReturn(Optional.of(codigo));
        when(passwordEncoder.matches("NuevoPasswordSeguro123*", "$argon2id$oldhash")).thenReturn(false);
        when(passwordEncoder.encode("NuevoPasswordSeguro123*")).thenReturn("$argon2id$newhash");

        ResetPasswordRequest req = new ResetPasswordRequest(email, rawCode, "NuevoPasswordSeguro123*");
        ResetPasswordResponse resp = authService.resetPassword(req, "192.168.1.50");

        assertThat(resp.message()).isEqualTo(ResetPasswordResponse.DEFAULT_MESSAGE);

        verify(userRepository).updatePassword(10L, "$argon2id$newhash", false);
        verify(userRepository).resetAttempts(10L);
        verify(verificationCodeRepository).markAsUsed(1L);
        verify(refreshTokenRepository).revokeAllByUser(10L);
        verify(auditService).recordEvent(
                eq(10L),
                eq(AuditableAction.RECUPERACION_PASSWORD_EXITO),
                eq("USUARIO"),
                eq("usr-pub-1"),
                eq(AuditResult.EXITO),
                eq("192.168.1.50")
        );
    }

    @Test
    void resetPassword_codeIncorrect_incrementsAttemptsAndAuditsFailure() {
        String email = "carlos@hospital.com";
        String rawCodeCorrecto = "654321";
        String rawCodeEnviado = "111111";
        String codeHash = TokenHashUtil.hash(rawCodeCorrecto);
        User usuario = new User(
                10L, "usr-pub-1", email, "$argon2id$oldhash", "ACTIVO", 0, null, Instant.now(), false
        );
        VerificationCode codigo = new VerificationCode(
                1L, "cod-pub-1", 10L, VerificationCode.PASSWORD_RECOVERY_TYPE,
                codeHash, Instant.now().plus(10, ChronoUnit.MINUTES), 0, 3, false, Instant.now()
        );

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(usuario));
        when(verificationCodeRepository.findLatestPendingByUserAndType(10L, VerificationCode.PASSWORD_RECOVERY_TYPE))
                .thenReturn(Optional.of(codigo));

        ResetPasswordRequest req = new ResetPasswordRequest(email, rawCodeEnviado, "NuevoPasswordSeguro123*");

        assertThatThrownBy(() -> authService.resetPassword(req, "192.168.1.50"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Codigo de verificacion incorrecto");

        verify(verificationCodeRepository).incrementAttempts(1L);
        verify(userRepository, never()).updatePassword(anyLong(), anyString(), anyBoolean());
        verify(auditService).recordEvent(
                eq(10L),
                eq(AuditableAction.RECUPERACION_PASSWORD_FALLO),
                eq("USUARIO"),
                eq("usr-pub-1"),
                eq(AuditResult.FALLO),
                eq("192.168.1.50")
        );
    }

    @Test
    void resetPassword_codeExpired_marksAsUsedAndAuditsFailure() {
        String email = "carlos@hospital.com";
        User usuario = new User(
                10L, "usr-pub-1", email, "$argon2id$oldhash", "ACTIVO", 0, null, Instant.now(), false
        );
        VerificationCode codigo = new VerificationCode(
                1L, "cod-pub-1", 10L, VerificationCode.PASSWORD_RECOVERY_TYPE,
                "somehash", Instant.now().minus(5, ChronoUnit.MINUTES), 0, 3, false, Instant.now().minus(20, ChronoUnit.MINUTES)
        );

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(usuario));
        when(verificationCodeRepository.findLatestPendingByUserAndType(10L, VerificationCode.PASSWORD_RECOVERY_TYPE))
                .thenReturn(Optional.of(codigo));

        ResetPasswordRequest req = new ResetPasswordRequest(email, "123456", "NuevoPasswordSeguro123*");

        assertThatThrownBy(() -> authService.resetPassword(req, "192.168.1.50"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("expirado");

        verify(verificationCodeRepository).markAsUsed(1L);
        verify(userRepository, never()).updatePassword(anyLong(), anyString(), anyBoolean());
    }

    @Test
    void resetPassword_codeExceededMaxAttempts_marksAsUsedAndAuditsBlocked() {
        String email = "carlos@hospital.com";
        User usuario = new User(
                10L, "usr-pub-1", email, "$argon2id$oldhash", "ACTIVO", 0, null, Instant.now(), false
        );
        VerificationCode codigo = new VerificationCode(
                1L, "cod-pub-1", 10L, VerificationCode.PASSWORD_RECOVERY_TYPE,
                "somehash", Instant.now().plus(10, ChronoUnit.MINUTES), 3, 3, false, Instant.now()
        );

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(usuario));
        when(verificationCodeRepository.findLatestPendingByUserAndType(10L, VerificationCode.PASSWORD_RECOVERY_TYPE))
                .thenReturn(Optional.of(codigo));

        ResetPasswordRequest req = new ResetPasswordRequest(email, "123456", "NuevoPasswordSeguro123*");

        assertThatThrownBy(() -> authService.resetPassword(req, "192.168.1.50"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("superado el numero maximo de intentos");

        verify(verificationCodeRepository).markAsUsed(1L);
    }

    @Test
    void resetPassword_passwordNewEqualToCurrent_throwsInvalidDataException() {
        String email = "carlos@hospital.com";
        String rawCode = "654321";
        String codeHash = TokenHashUtil.hash(rawCode);
        User usuario = new User(
                10L, "usr-pub-1", email, "$argon2id$oldhash", "ACTIVO", 0, null, Instant.now(), false
        );
        VerificationCode codigo = new VerificationCode(
                1L, "cod-pub-1", 10L, VerificationCode.PASSWORD_RECOVERY_TYPE,
                codeHash, Instant.now().plus(10, ChronoUnit.MINUTES), 0, 3, false, Instant.now()
        );

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(usuario));
        when(verificationCodeRepository.findLatestPendingByUserAndType(10L, VerificationCode.PASSWORD_RECOVERY_TYPE))
                .thenReturn(Optional.of(codigo));
        when(passwordEncoder.matches("MismaPassword123*", "$argon2id$oldhash")).thenReturn(true);

        ResetPasswordRequest req = new ResetPasswordRequest(email, rawCode, "MismaPassword123*");

        assertThatThrownBy(() -> authService.resetPassword(req, "192.168.1.50"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("no puede ser igual a la anterior");

        verify(userRepository, never()).updatePassword(anyLong(), anyString(), anyBoolean());
    }

    // -------------------------------------------------------------------------
    // AUTENTICACIÓN MULTIFACTOR (MFA TOTP) (ADR-014, F2.1.4)
    // -------------------------------------------------------------------------

    @Test
    void login_withMfaEnabled_returnsChallengeWithoutTokensFinal() {
        LoginRequest req = new LoginRequest("dr.garcia@hospital.com", "PasswordSeguro123*");
        User usuario = new User(
                20L,
                "usr-medico-1",
                "dr.garcia@hospital.com",
                "$argon2id$hashed",
                "ACTIVO",
                0,
                null,
                Instant.now(),
                false,
                true, // mfaHabilitado
                "JBSWY3DPEHPK3PXP",
                Instant.now()
        );

        when(userRepository.findByEmail("dr.garcia@hospital.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("PasswordSeguro123*", "$argon2id$hashed")).thenReturn(true);
        when(userRepository.getRoles(20L)).thenReturn(List.of("ROLE_PROFESIONAL"));
        when(jwtService.generateMfaChallengeToken("usr-medico-1", "dr.garcia@hospital.com", List.of("ROLE_PROFESIONAL")))
                .thenReturn("mock.challenge.jwt");

        AuthTokens resultado = authService.login(req, "192.168.1.100");

        assertThat(resultado).isNotNull();
        assertThat(resultado.accessToken()).isNull();
        assertThat(resultado.rawRefreshToken()).isNull();
        assertThat(resultado.sessionResponse().mfaRequired()).isTrue();
        assertThat(resultado.sessionResponse().mfaChallengeToken()).isEqualTo("mock.challenge.jwt");

        verify(refreshTokenRepository, never()).create(anyLong(), anyString(), any(Instant.class));
    }

    @Test
    void setupMfa_exito_guardaSecretYRetornaQrUri() {
        User usuario = new User(
                25L, "usr-medico-2", "medico@hospital.com", "$hash", "ACTIVO", 0, null, Instant.now(), false
        );

        when(userRepository.findByPublicId("usr-medico-2")).thenReturn(Optional.of(usuario));
        when(totpService.generateNewSecret()).thenReturn("JBSWY3DPEHPK3PXP");
        when(totpService.generateOtpAuthUri("MediTriaje", "medico@hospital.com", "JBSWY3DPEHPK3PXP"))
                .thenReturn("otpauth://totp/MediTriaje:medico@hospital.com?secret=JBSWY3DPEHPK3PXP&issuer=MediTriaje");

        MfaSetupResponse response = authService.setupMfa("usr-medico-2", "192.168.1.100");

        assertThat(response).isNotNull();
        assertThat(response.secret()).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(response.qrUri()).startsWith("otpauth://totp/");

        verify(userRepository).saveMfaSecret(25L, "JBSWY3DPEHPK3PXP");
        verify(auditService).recordEvent(
                eq(25L),
                eq(AuditableAction.MFA_SETUP),
                eq("USUARIO"),
                eq("usr-medico-2"),
                eq(AuditResult.EXITO),
                eq("192.168.1.100")
        );
    }

    @Test
    void setupMfa_userInactive_throwsUnauthorizedAccess() {
        User usuario = new User(
                25L, "usr-inactivo", "inactivo@hospital.com", "$hash", "INACTIVO", 0, null, Instant.now(), false
        );

        when(userRepository.findByPublicId("usr-inactivo")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.setupMfa("usr-inactivo", "192.168.1.100"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("no se encuentra activa");
    }

    @Test
    void verifyMfa_codeValid_activeMfaAndGeneratesCodesBackup() {
        User usuario = new User(
                25L, "usr-medico-2", "medico@hospital.com", "$hash", "ACTIVO", 0, null, Instant.now(),
                false, false, "JBSWY3DPEHPK3PXP", null
        );

        when(userRepository.findByPublicId("usr-medico-2")).thenReturn(Optional.of(usuario));
        when(totpService.validateCode(eq("JBSWY3DPEHPK3PXP"), eq("123456"), any(Instant.class))).thenReturn(true);

        MfaVerifyRequest request = new MfaVerifyRequest("123456");
        MfaVerifyResponse response = authService.verifyMfa("usr-medico-2", request, "192.168.1.100");

        assertThat(response).isNotNull();
        assertThat(response.mfaEnabled()).isTrue();
        assertThat(response.backupCodes()).hasSize(8);

        verify(userRepository).enableMfa(25L, "JBSWY3DPEHPK3PXP");
        verify(mfaBackupCodeRepository).deleteByUser(25L);
        verify(mfaBackupCodeRepository).saveBatch(eq(25L), any());
        verify(auditService).recordEvent(
                eq(25L),
                eq(AuditableAction.MFA_VERIFY),
                eq("USUARIO"),
                eq("usr-medico-2"),
                eq(AuditResult.EXITO),
                eq("192.168.1.100")
        );
    }

    @Test
    void verifyMfa_codeInvalid_throwsInvalidCredentialsException() {
        User usuario = new User(
                25L, "usr-medico-2", "medico@hospital.com", "$hash", "ACTIVO", 0, null, Instant.now(),
                false, false, "JBSWY3DPEHPK3PXP", null
        );

        when(userRepository.findByPublicId("usr-medico-2")).thenReturn(Optional.of(usuario));
        when(totpService.validateCode(eq("JBSWY3DPEHPK3PXP"), eq("000000"), any(Instant.class))).thenReturn(false);

        MfaVerifyRequest request = new MfaVerifyRequest("000000");

        assertThatThrownBy(() -> authService.verifyMfa("usr-medico-2", request, "192.168.1.100"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("incorrecto o expirado");

        verify(userRepository, never()).enableMfa(anyLong(), anyString());
        verify(auditService).recordEvent(
                eq(25L),
                eq(AuditableAction.MFA_VERIFY),
                eq("USUARIO"),
                eq("usr-medico-2"),
                eq(AuditResult.FALLO),
                eq("192.168.1.100")
        );
    }

    @Test
    void authenticateMfa_challengeInvalid_throwsInvalidTokenException() {
        MfaAuthenticateRequest request = new MfaAuthenticateRequest("token-falso", "123456");
        when(jwtService.isValidMfaChallenge("token-falso")).thenReturn(false);

        assertThatThrownBy(() -> authService.authenticateMfa(request, "192.168.1.100"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("desafio de autenticacion MFA ha expirado o es invalido");
    }

    @Test
    void authenticateMfa_withTotpValid_issuesTokensAndAudits() {
        MfaAuthenticateRequest request = new MfaAuthenticateRequest("token-valido", "123456");
        User usuario = new User(
                30L, "usr-pub-30", "admin@hospital.com", "$hash", "ACTIVO", 0, null, Instant.now(),
                false, true, "JBSWY3DPEHPK3PXP", Instant.now()
        );

        when(jwtService.isValidMfaChallenge("token-valido")).thenReturn(true);
        when(jwtService.extractPublicId("token-valido")).thenReturn("usr-pub-30");
        when(userRepository.findByPublicId("usr-pub-30")).thenReturn(Optional.of(usuario));
        when(totpService.validateCode(eq("JBSWY3DPEHPK3PXP"), eq("123456"), any(Instant.class))).thenReturn(true);
        when(userRepository.getRoles(30L)).thenReturn(List.of("ROLE_ADMINISTRADOR"));
        when(jwtService.generateAccessToken(eq("usr-pub-30"), eq("admin@hospital.com"), eq(List.of("ROLE_ADMINISTRADOR"))))
                .thenReturn("mock.access.jwt");

        AuthTokens tokens = authService.authenticateMfa(request, "192.168.1.100");

        assertThat(tokens).isNotNull();
        assertThat(tokens.accessToken()).isEqualTo("mock.access.jwt");
        assertThat(tokens.rawRefreshToken()).isNotBlank();
        assertThat(tokens.sessionResponse().email()).isEqualTo("admin@hospital.com");

        verify(refreshTokenRepository).create(eq(30L), anyString(), any(Instant.class));
        verify(auditService).recordEvent(
                eq(30L),
                eq(AuditableAction.MFA_LOGIN_EXITOSO),
                eq("USUARIO"),
                eq("usr-pub-30"),
                eq(AuditResult.EXITO),
                eq("192.168.1.100")
        );
        verify(auditService).recordEvent(
                eq(30L),
                eq(AuditableAction.LOGIN_EXITOSO),
                eq("USUARIO"),
                eq("usr-pub-30"),
                eq(AuditResult.EXITO),
                eq("192.168.1.100")
        );
    }

    @Test
    void authenticateMfa_withCodeBackupValid_consumeCodeAndIssuesTokens() {
        MfaAuthenticateRequest request = new MfaAuthenticateRequest("token-valido", "1234-5678");
        User usuario = new User(
                30L, "usr-pub-30", "admin@hospital.com", "$hash", "ACTIVO", 0, null, Instant.now(),
                false, true, "JBSWY3DPEHPK3PXP", Instant.now()
        );

        when(jwtService.isValidMfaChallenge("token-valido")).thenReturn(true);
        when(jwtService.extractPublicId("token-valido")).thenReturn("usr-pub-30");
        when(userRepository.findByPublicId("usr-pub-30")).thenReturn(Optional.of(usuario));
        when(mfaBackupCodeRepository.consumeCode(eq(30L), anyString())).thenReturn(true);
        when(userRepository.getRoles(30L)).thenReturn(List.of("ROLE_ADMINISTRADOR"));
        when(jwtService.generateAccessToken(eq("usr-pub-30"), eq("admin@hospital.com"), eq(List.of("ROLE_ADMINISTRADOR"))))
                .thenReturn("mock.access.jwt");

        AuthTokens tokens = authService.authenticateMfa(request, "192.168.1.100");

        assertThat(tokens).isNotNull();
        assertThat(tokens.accessToken()).isEqualTo("mock.access.jwt");
        verify(mfaBackupCodeRepository).consumeCode(eq(30L), anyString());
    }

    @Test
    void authenticateMfa_withCodeInvalid_throwsInvalidCredentialsException() {
        MfaAuthenticateRequest request = new MfaAuthenticateRequest("token-valido", "999999");
        User usuario = new User(
                30L, "usr-pub-30", "admin@hospital.com", "$hash", "ACTIVO", 0, null, Instant.now(),
                false, true, "JBSWY3DPEHPK3PXP", Instant.now()
        );

        when(jwtService.isValidMfaChallenge("token-valido")).thenReturn(true);
        when(jwtService.extractPublicId("token-valido")).thenReturn("usr-pub-30");
        when(userRepository.findByPublicId("usr-pub-30")).thenReturn(Optional.of(usuario));
        when(totpService.validateCode(eq("JBSWY3DPEHPK3PXP"), eq("999999"), any(Instant.class))).thenReturn(false);
        when(mfaBackupCodeRepository.consumeCode(eq(30L), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.authenticateMfa(request, "192.168.1.100"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("invalido");

        verify(auditService).recordEvent(
                eq(30L),
                eq(AuditableAction.MFA_LOGIN_FALLIDO),
                eq("USUARIO"),
                eq("usr-pub-30"),
                eq(AuditResult.FALLO),
                eq("192.168.1.100")
        );
    }
}
