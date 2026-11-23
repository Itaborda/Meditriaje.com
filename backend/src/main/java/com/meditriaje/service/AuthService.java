package com.meditriaje.service;

import com.meditriaje.dto.AuthSessionResponse;
import com.meditriaje.dto.AuthTokens;
import com.meditriaje.dto.ChangePasswordRequest;
import com.meditriaje.dto.LoginRequest;
import com.meditriaje.dto.RegisterPatientRequest;
import com.meditriaje.dto.RegisterPatientResponse;
import com.meditriaje.dto.auth.MfaAuthenticateRequest;
import com.meditriaje.dto.auth.MfaSetupResponse;
import com.meditriaje.dto.auth.MfaVerifyRequest;
import com.meditriaje.dto.auth.MfaVerifyResponse;
import com.meditriaje.dto.auth.ResetPasswordRequest;
import com.meditriaje.dto.auth.ResetPasswordResponse;
import com.meditriaje.dto.auth.RequestPasswordResetRequest;
import com.meditriaje.dto.auth.RequestPasswordResetResponse;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.InvalidCredentialsException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.InvalidTokenException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.VerificationCode;
import com.meditriaje.model.Patient;
import com.meditriaje.model.Professional;
import com.meditriaje.model.RefreshToken;
import com.meditriaje.model.AuditResult;
import com.meditriaje.model.User;
import com.meditriaje.repository.VerificationCodeRepository;
import com.meditriaje.repository.ConsentRepository;
import com.meditriaje.repository.MfaBackupCodeRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.ProfessionalRepository;
import com.meditriaje.repository.RefreshTokenRepository;
import com.meditriaje.repository.UserRepository;
import com.meditriaje.security.JwtService;
import com.meditriaje.security.TokenHashUtil;
import com.meditriaje.security.TotpService;
import com.meditriaje.service.email.EmailService;
import com.meditriaje.service.email.EmailUtil;
import com.meditriaje.service.email.EmailSendResult;
import com.meditriaje.util.ColombianStandardValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de autenticación, registro de usuarios y ciclo de vida de sesiones (HU-01, ADR-002, ADR-013, ADR-014).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final ProfessionalRepository professionalRepository;
    private final ConsentRepository consentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final MfaBackupCodeRepository mfaBackupCodeRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TotpService totpService;
    private final EmailService emailService;
    private final long refreshExpirationDays;

    @Autowired
    public AuthService(
            UserRepository userRepository,
            PatientRepository patientRepository,
            ProfessionalRepository professionalRepository,
            ConsentRepository consentRepository,
            RefreshTokenRepository refreshTokenRepository,
            VerificationCodeRepository verificationCodeRepository,
            MfaBackupCodeRepository mfaBackupCodeRepository,
            AuditService auditService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TotpService totpService,
            EmailService emailService,
            @Value("${security.jwt.refresh-expiration-days:7}") long refreshExpirationDays
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "UsuarioRepository no puede ser nulo");
        this.patientRepository = Objects.requireNonNull(patientRepository, "PacienteRepository no puede ser nulo");
        this.professionalRepository = professionalRepository;
        this.consentRepository = Objects.requireNonNull(consentRepository, "ConsentimientoRepository no puede ser nulo");
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository, "RefreshTokenRepository no puede ser nulo");
        this.verificationCodeRepository = verificationCodeRepository;
        this.mfaBackupCodeRepository = mfaBackupCodeRepository;
        this.auditService = Objects.requireNonNull(auditService, "AuditoriaService no puede ser nulo");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "PasswordEncoder no puede ser nulo");
        this.jwtService = Objects.requireNonNull(jwtService, "JwtService no puede ser nulo");
        this.totpService = totpService;
        this.emailService = emailService;
        this.refreshExpirationDays = refreshExpirationDays;
    }

    public AuthService(
            UserRepository userRepository,
            PatientRepository patientRepository,
            ProfessionalRepository professionalRepository,
            ConsentRepository consentRepository,
            RefreshTokenRepository refreshTokenRepository,
            VerificationCodeRepository verificationCodeRepository,
            AuditService auditService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailService emailService,
            long refreshExpirationDays
    ) {
        this(
                userRepository,
                patientRepository,
                professionalRepository,
                consentRepository,
                refreshTokenRepository,
                verificationCodeRepository,
                null,
                auditService,
                passwordEncoder,
                jwtService,
                null,
                emailService,
                refreshExpirationDays
        );
    }

    public AuthService(
            UserRepository userRepository,
            PatientRepository patientRepository,
            ConsentRepository consentRepository,
            RefreshTokenRepository refreshTokenRepository,
            AuditService auditService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            long refreshExpirationDays
    ) {
        this(
                userRepository,
                patientRepository,
                null,
                consentRepository,
                refreshTokenRepository,
                null,
                null,
                auditService,
                passwordEncoder,
                jwtService,
                null,
                null,
                refreshExpirationDays
        );
    }

    /**
     * Registra un nuevo paciente en una única transacción atómica:
     * crea USUARIO, asigna ROLE_PACIENTE, crea PACIENTE y registra CONSENTIMIENTO.
     */
    @Transactional
    public RegisterPatientResponse registerPatient(RegisterPatientRequest request, String ipOrigen) {
        Objects.requireNonNull(request, "La solicitud de registro no puede ser nula");

        // 1. Validar consentimiento
        if (!Boolean.TRUE.equals(request.acceptsConsent())) {
            auditService.recordEvent(
                    AuditableAction.REGISTRO_PACIENTE,
                    "USUARIO",
                    null,
                    AuditResult.FALLO,
                    ipOrigen
            );
            throw new InvalidDataException("Debe aceptar el consentimiento informado para registrarse.");
        }

        // 2. Validar reglas de identificación y datos según norma colombiana
        try {
            ColombianStandardValidator.validateDocument(request.documentType(), request.documentNumber());
            ColombianStandardValidator.validateDocumentAgeCoherence(request.documentType(), request.birthDate(), null);
            ColombianStandardValidator.validateNamesOrSurnames("nombres", request.firstNames());
            ColombianStandardValidator.validateNamesOrSurnames("apellidos", request.lastNames());
            ColombianStandardValidator.validateColombianPhone(request.phone());
        } catch (InvalidDataException e) {
            auditService.recordEvent(
                    AuditableAction.REGISTRO_PACIENTE,
                    "USUARIO",
                    null,
                    AuditResult.FALLO,
                    ipOrigen
            );
            throw e;
        }

        // 3. Normalizar correo y verificar duplicados
        String emailNormalizado = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(emailNormalizado)) {
            auditService.recordEvent(
                    AuditableAction.REGISTRO_PACIENTE,
                    "USUARIO",
                    null,
                    AuditResult.FALLO,
                    ipOrigen
            );
            throw new InvalidDataException("El correo electronico ya se encuentra registrado.");
        }

        // 3. Verificar documento duplicado o vincular cuenta a paciente presencial previo sin usuario web
        String docNumber = request.documentNumber().trim();
        Optional<Patient> walkInPatient = Optional.empty();
        if (patientRepository.existsByDocument(request.documentType(), docNumber)) {
            Optional<Patient> existing = patientRepository.findByDocument(request.documentType(), docNumber);
            if (existing.isEmpty() || existing.get().userId() != null) {
                auditService.recordEvent(
                        AuditableAction.REGISTRO_PACIENTE,
                        "PACIENTE",
                        null,
                        AuditResult.FALLO,
                        ipOrigen
                );
                throw new InvalidDataException("El documento de identidad ya se encuentra registrado.");
            }
            walkInPatient = existing;
        }

        // 4. Hashear contraseña con Argon2id
        String passwordHash = passwordEncoder.encode(request.password());

        // 5. Crear USUARIO
        String usuarioPublicId = UUID.randomUUID().toString();
        Long usuarioId = userRepository.create(usuarioPublicId, emailNormalizado, passwordHash);

        // 6. Asignar rol ROLE_PACIENTE
        Long rolId = userRepository.findRoleIdByName("ROLE_PACIENTE")
                .orElseThrow(() -> new IllegalStateException("El rol ROLE_PACIENTE no existe en el sistema."));
        userRepository.assignRole(usuarioId, rolId);

        // 7. Crear o vincular PACIENTE
        String pacientePublicId;
        if (walkInPatient.isPresent()) {
            Patient existing = walkInPatient.get();
            patientRepository.linkUserToPatient(existing.id(), usuarioId);
            pacientePublicId = existing.publicId();
        } else {
            pacientePublicId = UUID.randomUUID().toString();
            patientRepository.create(
                    usuarioId,
                    pacientePublicId,
                    request.documentType(),
                    docNumber,
                    request.firstNames().trim(),
                    request.lastNames().trim(),
                    request.birthDate(),
                    request.phone() != null ? request.phone().trim() : null
            );
        }

        // 8. Registrar CONSENTIMIENTO
        consentRepository.record(
                usuarioId,
                request.consentTextVersion().trim(),
                true,
                ipOrigen
        );

        // 9. Registrar evento exitoso en auditoría
        auditService.recordEvent(
                usuarioId,
                AuditableAction.REGISTRO_PACIENTE,
                "PACIENTE",
                pacientePublicId,
                AuditResult.EXITO,
                ipOrigen
        );

        return new RegisterPatientResponse(
                pacientePublicId,
                emailNormalizado,
                request.firstNames().trim(),
                request.lastNames().trim(),
                "Registro completado exitosamente."
        );
    }

    /**
     * Inicia sesión validando credenciales y bloqueo temporal (5 fallos -> 15 min).
     * Devuelve tokens y sesión si es exitoso (ADR-002, HU-01).
     */
    @Transactional
    public AuthTokens login(LoginRequest request, String ipOrigen) {
        Objects.requireNonNull(request, "La solicitud de login no puede ser nula");

        String emailNormalizado = request.email().trim().toLowerCase(Locale.ROOT);
        Optional<User> usuarioOpt = userRepository.findByEmail(emailNormalizado);

        if (usuarioOpt.isEmpty()) {
            auditService.recordEvent(
                    AuditableAction.LOGIN_FALLIDO,
                    "USUARIO",
                    null,
                    AuditResult.FALLO,
                    ipOrigen
            );
            throw new InvalidCredentialsException();
        }

        User usuario = usuarioOpt.get();

        // Validar bloqueo temporal de cuenta
        if (usuario.isLocked()) {
            auditService.recordEvent(
                    usuario.id(),
                    AuditableAction.LOGIN_FALLIDO,
                    "USUARIO",
                    usuario.publicId(),
                    AuditResult.BLOQUEADO,
                    ipOrigen
            );
            throw new InvalidCredentialsException();
        }

        // Si expiró el bloqueo temporal previo, restablecer intentos
        if (usuario.lockedUntil() != null && Instant.now().isAfter(usuario.lockedUntil())) {
            userRepository.resetAttempts(usuario.id());
            usuario = new User(
                    usuario.id(),
                    usuario.publicId(),
                    usuario.email(),
                    usuario.passwordHash(),
                    "ACTIVO",
                    0,
                    null,
                    usuario.createdAt()
            );
        }

        // Validar contraseña
        if (!passwordEncoder.matches(request.password(), usuario.passwordHash())) {
            int nuevosIntentos = usuario.failedAttempts() + 1;
            if (nuevosIntentos >= 5) {
                Instant bloqueadoHasta = Instant.now().plus(15, ChronoUnit.MINUTES);
                userRepository.updateFailedAttempts(usuario.id(), nuevosIntentos, bloqueadoHasta, "BLOQUEADO");
                auditService.recordEvent(
                        usuario.id(),
                        AuditableAction.LOGIN_FALLIDO,
                        "USUARIO",
                        usuario.publicId(),
                        AuditResult.BLOQUEADO,
                        ipOrigen
                );
            } else {
                userRepository.updateFailedAttempts(usuario.id(), nuevosIntentos, null, "ACTIVO");
                auditService.recordEvent(
                        usuario.id(),
                        AuditableAction.LOGIN_FALLIDO,
                        "USUARIO",
                        usuario.publicId(),
                        AuditResult.FALLO,
                        ipOrigen
                );
            }
            throw new InvalidCredentialsException();
        }

        // Contraseña correcta: restablecer intentos si hubo fallos previos
        if (usuario.failedAttempts() > 0 || usuario.lockedUntil() != null) {
            userRepository.resetAttempts(usuario.id());
        }

        List<String> roles = userRepository.getRoles(usuario.id());

        // Si el usuario tiene MFA habilitado, requerir segundo factor (ADR-014, F2.1.4)
        if (usuario.mfaEnabled()) {
            String challengeToken = jwtService.generateMfaChallengeToken(usuario.publicId(), usuario.email(), roles);
            AuthSessionResponse challengeResponse = AuthSessionResponse.mfaRequired(
                    usuario.publicId(),
                    usuario.email(),
                    roles,
                    challengeToken
            );
            return new AuthTokens(null, null, challengeResponse);
        }

        String accessToken = jwtService.generateAccessToken(usuario.publicId(), usuario.email(), roles);

        // Refresh token rotativo opaco con alta entropía
        String rawRefreshToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        String tokenHash = TokenHashUtil.hash(rawRefreshToken);
        Instant expiracionRefresh = Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS);
        refreshTokenRepository.create(usuario.id(), tokenHash, expiracionRefresh);

        auditService.recordEvent(
                usuario.id(),
                AuditableAction.LOGIN_EXITOSO,
                "USUARIO",
                usuario.publicId(),
                AuditResult.EXITO,
                ipOrigen
        );

        AuthSessionResponse sessionResponse = new AuthSessionResponse(
                usuario.publicId(),
                usuario.email(),
                roles,
                "Inicio de sesion exitoso.",
                usuario.mustChangePassword()
        );

        return new AuthTokens(accessToken, rawRefreshToken, sessionResponse);
    }

    /**
     * Rota el refresh token y emite un nuevo access JWT (ADR-002).
     * Si el token presentado ya fue revocado, activa la revocación masiva del usuario (mitigación de robo).
     */
    @Transactional
    public AuthTokens refresh(String rawRefreshToken, String ipOrigen) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidTokenException("Token de sesion no proporcionado.");
        }

        String tokenHash = TokenHashUtil.hash(rawRefreshToken);
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);

        if (tokenOpt.isEmpty()) {
            throw new InvalidTokenException("Token de sesion invalido.");
        }

        RefreshToken refreshToken = tokenOpt.get();

        // Detección de reuso de token revocado (mitigación de secuestro de sesión)
        if (refreshToken.revoked()) {
            refreshTokenRepository.revokeAllByUser(refreshToken.userId());
            auditService.recordEvent(
                    refreshToken.userId(),
                    AuditableAction.LOGIN_FALLIDO,
                    "REFRESH_TOKEN",
                    String.valueOf(refreshToken.id()),
                    AuditResult.BLOQUEADO,
                    ipOrigen
            );
            throw new InvalidTokenException("Sesion revocada por seguridad debido a deteccion de reuso.");
        }

        // Token expirado
        if (refreshToken.isExpired()) {
            refreshTokenRepository.revoke(refreshToken.id());
            throw new InvalidTokenException("El token de sesion ha expirado.");
        }

        User usuario = userRepository.findById(refreshToken.userId())
                .orElseThrow(() -> new InvalidTokenException("Usuario no encontrado para la sesion."));

        if (usuario.isLocked() || !"ACTIVO".equalsIgnoreCase(usuario.status())) {
            refreshTokenRepository.revoke(refreshToken.id());
            throw new UnauthorizedAccessException();
        }

        // Rotación: revocar el token usado
        refreshTokenRepository.revoke(refreshToken.id());

        // Emitir nuevo refresh token
        String nuevoRawRefreshToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        String nuevoTokenHash = TokenHashUtil.hash(nuevoRawRefreshToken);
        Instant nuevaExpiracion = Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS);
        refreshTokenRepository.create(usuario.id(), nuevoTokenHash, nuevaExpiracion);

        List<String> roles = userRepository.getRoles(usuario.id());
        String nuevoAccessToken = jwtService.generateAccessToken(usuario.publicId(), usuario.email(), roles);

        AuthSessionResponse sessionResponse = new AuthSessionResponse(
                usuario.publicId(),
                usuario.email(),
                roles,
                "Sesion actualizada exitosamente.",
                usuario.mustChangePassword()
        );

        return new AuthTokens(nuevoAccessToken, nuevoRawRefreshToken, sessionResponse);
    }

    /**
     * Invalida el refresh token y registra el evento de logout (ADR-002, HU-01).
     */
    @Transactional
    public void logout(String rawRefreshToken, String ipOrigen) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = TokenHashUtil.hash(rawRefreshToken);
            Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
            if (tokenOpt.isPresent()) {
                RefreshToken rt = tokenOpt.get();
                refreshTokenRepository.revoke(rt.id());
                User usuario = userRepository.findById(rt.userId()).orElse(null);
                auditService.recordEvent(
                        rt.userId(),
                        AuditableAction.LOGOUT,
                        "USUARIO",
                        usuario != null ? usuario.publicId() : null,
                        AuditResult.EXITO,
                        ipOrigen
                );
                return;
            }
        }

        // Logout sin token o token inexistente
        auditService.recordEvent(
                AuditableAction.LOGOUT,
                "USUARIO",
                null,
                AuditResult.EXITO,
                ipOrigen
        );
    }

    /**
     * Cambia la contraseña del usuario autenticado (ADR-002, M3.3).
     * Valida la contraseña actual, que la nueva no sea idéntica y actualiza restableciendo debeCambiarPassword = false.
     */
    @Transactional
    public void changePassword(String usuarioPublicId, ChangePasswordRequest request, String ipOrigen) {
        Objects.requireNonNull(request, "La solicitud de cambio de contrasena no puede ser nula");

        User usuario = userRepository.findByPublicId(usuarioPublicId)
                .orElseThrow(() -> new InvalidCredentialsException("Credenciales invalidas."));

        if (!passwordEncoder.matches(request.currentPassword(), usuario.passwordHash())) {
            auditService.recordEvent(
                    usuario.id(),
                    AuditableAction.CAMBIO_PASSWORD,
                    "USUARIO",
                    usuario.publicId(),
                    AuditResult.FALLO,
                    ipOrigen
            );
            throw new InvalidCredentialsException("Credenciales invalidas.");
        }

        if (passwordEncoder.matches(request.newPassword(), usuario.passwordHash())) {
            auditService.recordEvent(
                    usuario.id(),
                    AuditableAction.CAMBIO_PASSWORD,
                    "USUARIO",
                    usuario.publicId(),
                    AuditResult.FALLO,
                    ipOrigen
            );
            throw new InvalidDataException("La nueva contrasena no puede ser igual a la anterior.");
        }

        String nuevoHash = passwordEncoder.encode(request.newPassword());
        userRepository.updatePassword(usuario.id(), nuevoHash, false);

        auditService.recordEvent(
                usuario.id(),
                AuditableAction.CAMBIO_PASSWORD,
                "USUARIO",
                usuario.publicId(),
                AuditResult.EXITO,
                ipOrigen
        );
    }

    /**
     * Procesa la solicitud de recuperación de contraseña enviando un código OTP de 6 dígitos por correo (ADR-014, F2.1.3).
     * Siempre retorna una respuesta idéntica informativa para mitigar ataques de enumeración de usuarios.
     */
    @Transactional
    public RequestPasswordResetResponse requestPasswordReset(RequestPasswordResetRequest request, String ipOrigen) {
        Objects.requireNonNull(request, "La solicitud de recuperacion no puede ser nula");
        String emailNormalizado = request.email().trim().toLowerCase(Locale.ROOT);

        Optional<User> usuarioOpt = userRepository.findByEmail(emailNormalizado);
        if (usuarioOpt.isEmpty()) {
            auditService.recordEvent(
                    AuditableAction.SOLICITUD_RECUPERACION_PASSWORD,
                    "USUARIO",
                    null,
                    AuditResult.EXITO,
                    ipOrigen
            );
            return RequestPasswordResetResponse.defaultResponse();
        }

        User usuario = usuarioOpt.get();

        // Si la cuenta está expresamente inactiva, no se emite código
        if (!"ACTIVO".equalsIgnoreCase(usuario.status()) && !"BLOQUEADO".equalsIgnoreCase(usuario.status())) {
            return RequestPasswordResetResponse.defaultResponse();
        }

        if (verificationCodeRepository != null) {
            // Invalidar códigos de recuperación pendientes anteriores
            verificationCodeRepository.invalidatePreviousCodes(usuario.id(), VerificationCode.PASSWORD_RECOVERY_TYPE);

            // Generar código numérico de 6 dígitos con SecureRandom (100000 - 999999)
            SecureRandom random = new SecureRandom();
            int codigoNum = 100000 + random.nextInt(900000);
            String codigo = String.valueOf(codigoNum);

            // Hash criptográfico SHA-256
            String codigoHash = TokenHashUtil.hash(codigo);
            Instant expiracion = Instant.now().plus(15, ChronoUnit.MINUTES);

            VerificationCode nuevoCodigo = new VerificationCode(
                    null,
                    UUID.randomUUID().toString(),
                    usuario.id(),
                    VerificationCode.PASSWORD_RECOVERY_TYPE,
                    codigoHash,
                    expiracion,
                    0,
                    3,
                    false,
                    null
            );
            verificationCodeRepository.create(nuevoCodigo);

            String destinatarioNombre = resolveRecipientName(usuario);

            if (emailService != null) {
                try {
                    EmailSendResult resultado = emailService.sendRecoveryCode(usuario.email(), destinatarioNombre, codigo, 15);
                    if (resultado != null && !resultado.success()) {
                        log.warn("Fallo no bloqueante al enviar código de recuperación a [{}]: {}",
                                EmailUtil.maskEmail(usuario.email()), resultado.message());
                        auditService.recordEvent(
                                usuario.id(),
                                AuditableAction.EMAIL_FALLIDO,
                                "USUARIO",
                                usuario.publicId(),
                                AuditResult.FALLO,
                                ipOrigen
                        );
                    }
                } catch (Exception ex) {
                    log.warn("Excepción no bloqueante al enviar código de recuperación a [{}]: {}",
                            EmailUtil.maskEmail(usuario.email()), ex.getMessage());
                    auditService.recordEvent(
                            usuario.id(),
                            AuditableAction.EMAIL_FALLIDO,
                            "USUARIO",
                            usuario.publicId(),
                            AuditResult.FALLO,
                            ipOrigen
                    );
                }
            }
        }

        auditService.recordEvent(
                usuario.id(),
                AuditableAction.SOLICITUD_RECUPERACION_PASSWORD,
                "USUARIO",
                usuario.publicId(),
                AuditResult.EXITO,
                ipOrigen
        );

        return RequestPasswordResetResponse.defaultResponse();
    }

    /**
     * Restablece la contraseña del usuario validando el código OTP de 6 dígitos (ADR-014, F2.1.3).
     * Tras restablecer con éxito, revoca todas las sesiones previas (refresh tokens) del usuario.
     */
    @Transactional
    public ResetPasswordResponse resetPassword(ResetPasswordRequest request, String ipOrigen) {
        Objects.requireNonNull(request, "La solicitud de restablecimiento no puede ser nula");
        String emailNormalizado = request.email().trim().toLowerCase(Locale.ROOT);

        User usuario = userRepository.findByEmail(emailNormalizado)
                .orElseThrow(() -> new InvalidCredentialsException("Codigo de verificacion invalido o expirado."));

        if (!"ACTIVO".equalsIgnoreCase(usuario.status()) && !"BLOQUEADO".equalsIgnoreCase(usuario.status())) {
            throw new UnauthorizedAccessException("La cuenta de usuario se encuentra inactiva.");
        }

        if (verificationCodeRepository == null) {
            throw new IllegalStateException("CodigoVerificacionRepository no esta disponible.");
        }

        Optional<VerificationCode> codigoOpt = verificationCodeRepository
                .findLatestPendingByUserAndType(usuario.id(), VerificationCode.PASSWORD_RECOVERY_TYPE);

        if (codigoOpt.isEmpty()) {
            auditService.recordEvent(
                    usuario.id(),
                    AuditableAction.RECUPERACION_PASSWORD_FALLO,
                    "USUARIO",
                    usuario.publicId(),
                    AuditResult.FALLO,
                    ipOrigen
            );
            throw new InvalidCredentialsException("Codigo de verificacion invalido o expirado.");
        }

        VerificationCode codigoVerif = codigoOpt.get();
        Instant ahora = Instant.now();

        if (codigoVerif.isExpired(ahora)) {
            verificationCodeRepository.markAsUsed(codigoVerif.id());
            auditService.recordEvent(
                    usuario.id(),
                    AuditableAction.RECUPERACION_PASSWORD_FALLO,
                    "USUARIO",
                    usuario.publicId(),
                    AuditResult.FALLO,
                    ipOrigen
            );
            throw new InvalidCredentialsException("Codigo de verificacion expirado.");
        }

        if (codigoVerif.reachedMaxAttempts()) {
            verificationCodeRepository.markAsUsed(codigoVerif.id());
            auditService.recordEvent(
                    usuario.id(),
                    AuditableAction.RECUPERACION_PASSWORD_FALLO,
                    "USUARIO",
                    usuario.publicId(),
                    AuditResult.BLOQUEADO,
                    ipOrigen
            );
            throw new InvalidCredentialsException("El codigo ha superado el numero maximo de intentos permitidos.");
        }

        String inputHash = TokenHashUtil.hash(request.code());
        boolean coincide = MessageDigest.isEqual(
                inputHash.getBytes(StandardCharsets.UTF_8),
                codigoVerif.codeHash().getBytes(StandardCharsets.UTF_8)
        );

        if (!coincide) {
            verificationCodeRepository.incrementAttempts(codigoVerif.id());
            int nuevosIntentos = codigoVerif.failedAttempts() + 1;
            if (nuevosIntentos >= codigoVerif.maxAttempts()) {
                verificationCodeRepository.markAsUsed(codigoVerif.id());
            }

            auditService.recordEvent(
                    usuario.id(),
                    AuditableAction.RECUPERACION_PASSWORD_FALLO,
                    "USUARIO",
                    usuario.publicId(),
                    AuditResult.FALLO,
                    ipOrigen
            );
            throw new InvalidCredentialsException("Codigo de verificacion incorrecto.");
        }

        if (passwordEncoder.matches(request.newPassword(), usuario.passwordHash())) {
            auditService.recordEvent(
                    usuario.id(),
                    AuditableAction.RECUPERACION_PASSWORD_FALLO,
                    "USUARIO",
                    usuario.publicId(),
                    AuditResult.FALLO,
                    ipOrigen
            );
            throw new InvalidDataException("La nueva contrasena no puede ser igual a la anterior.");
        }

        String nuevoHash = passwordEncoder.encode(request.newPassword());
        userRepository.updatePassword(usuario.id(), nuevoHash, false);
        userRepository.resetAttempts(usuario.id());

        verificationCodeRepository.markAsUsed(codigoVerif.id());
        refreshTokenRepository.revokeAllByUser(usuario.id());

        auditService.recordEvent(
                usuario.id(),
                AuditableAction.RECUPERACION_PASSWORD_EXITO,
                "USUARIO",
                usuario.publicId(),
                AuditResult.EXITO,
                ipOrigen
        );

        return ResetPasswordResponse.defaultResponse();
    }

    private String resolveRecipientName(User usuario) {
        if (patientRepository != null) {
            var pacienteOpt = patientRepository.findByUserId(usuario.id());
            if (pacienteOpt.isPresent()) {
                return pacienteOpt.get().firstNames();
            }
        }
        if (professionalRepository != null) {
            var profesionalOpt = professionalRepository.findByUserId(usuario.id());
            if (profesionalOpt.isPresent()) {
                return profesionalOpt.get().firstNames();
            }
        }
        return usuario.email();
    }

    /**
     * Inicia el enrolamiento MFA generando un nuevo secreto Base32 y la URI otpauth (ADR-014, F2.1.4).
     */
    @Transactional
    public MfaSetupResponse setupMfa(String usuarioPublicId, String ipOrigen) {
        User usuario = userRepository.findByPublicId(usuarioPublicId)
                .orElseThrow(() -> new InvalidCredentialsException("Usuario no encontrado."));

        if (!"ACTIVO".equalsIgnoreCase(usuario.status())) {
            throw new UnauthorizedAccessException("La cuenta de usuario no se encuentra activa.");
        }

        if (totpService == null) {
            throw new IllegalStateException("TotpService no se encuentra configurado.");
        }

        String secret = totpService.generateNewSecret();
        userRepository.saveMfaSecret(usuario.id(), secret);

        String qrUri = totpService.generateOtpAuthUri("MediTriaje", usuario.email(), secret);

        auditService.recordEvent(
                usuario.id(),
                AuditableAction.MFA_SETUP,
                "USUARIO",
                usuario.publicId(),
                AuditResult.EXITO,
                ipOrigen
        );

        return new MfaSetupResponse(secret, qrUri, "MediTriaje", usuario.email());
    }

    /**
     * Valida el primer código TOTP para activar definitivamente MFA y genera 8 códigos de respaldo (ADR-014, F2.1.4).
     */
    @Transactional
    public MfaVerifyResponse verifyMfa(String usuarioPublicId, MfaVerifyRequest request, String ipOrigen) {
        Objects.requireNonNull(request, "La solicitud de verificacion MFA no puede ser nula");
        User usuario = userRepository.findByPublicId(usuarioPublicId)
                .orElseThrow(() -> new InvalidCredentialsException("Usuario no encontrado."));

        if (!"ACTIVO".equalsIgnoreCase(usuario.status())) {
            throw new UnauthorizedAccessException("La cuenta de usuario no se encuentra activa.");
        }

        if (usuario.mfaSecret() == null || usuario.mfaSecret().isBlank()) {
            throw new InvalidDataException("No hay una configuracion de MFA en proceso para este usuario.");
        }

        if (totpService == null) {
            throw new IllegalStateException("TotpService no se encuentra configurado.");
        }

        boolean valido = totpService.validateCode(usuario.mfaSecret(), request.code(), Instant.now());
        if (!valido) {
            auditService.recordEvent(
                    usuario.id(),
                    AuditableAction.MFA_VERIFY,
                    "USUARIO",
                    usuario.publicId(),
                    AuditResult.FALLO,
                    ipOrigen
            );
            throw new InvalidCredentialsException("Codigo de verificacion TOTP incorrecto o expirado.");
        }

        // Activar MFA en USUARIO
        userRepository.enableMfa(usuario.id(), usuario.mfaSecret());

        // Generar 8 códigos de respaldo uniuso (formato: 4 dígitos - 4 dígitos)
        SecureRandom random = new SecureRandom();
        List<String> codigosPlano = new ArrayList<>(8);
        List<String> codigosHash = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) {
            int p1 = 1000 + random.nextInt(9000);
            int p2 = 1000 + random.nextInt(9000);
            String rawCode = p1 + "-" + p2;
            codigosPlano.add(rawCode);
            codigosHash.add(TokenHashUtil.hash(rawCode));
        }

        if (mfaBackupCodeRepository != null) {
            mfaBackupCodeRepository.deleteByUser(usuario.id());
            mfaBackupCodeRepository.saveBatch(usuario.id(), codigosHash);
        }

        auditService.recordEvent(
                usuario.id(),
                AuditableAction.MFA_VERIFY,
                "USUARIO",
                usuario.publicId(),
                AuditResult.EXITO,
                ipOrigen
        );

        return new MfaVerifyResponse(
                true,
                codigosPlano,
                "Autenticacion de dos factores habilitada exitosamente. Guarda tus codigos de respaldo en un lugar seguro."
        );
    }

    /**
     * Valida el segundo factor de autenticación (TOTP o código de respaldo) y emite los tokens definitivos (ADR-014, F2.1.4).
     */
    @Transactional
    public AuthTokens authenticateMfa(MfaAuthenticateRequest request, String ipOrigen) {
        Objects.requireNonNull(request, "La solicitud de autenticacion MFA no puede ser nula");

        if (!jwtService.isValidMfaChallenge(request.challengeToken())) {
            throw new InvalidTokenException("El desafio de autenticacion MFA ha expirado o es invalido.");
        }

        String publicId = jwtService.extractPublicId(request.challengeToken());
        User usuario = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new InvalidTokenException("Usuario no encontrado para el desafio MFA."));

        if (!"ACTIVO".equalsIgnoreCase(usuario.status()) || usuario.isLocked()) {
            throw new UnauthorizedAccessException("La cuenta de usuario no se encuentra activa o esta bloqueada.");
        }

        if (!usuario.mfaEnabled() || usuario.mfaSecret() == null) {
            throw new InvalidDataException("El usuario no tiene la autenticacion multifactor habilitada.");
        }

        String codigo = request.code().trim();
        boolean autenticado = false;

        // 1. Intentar validar como código numérico TOTP de 6 dígitos
        if (codigo.matches("^[0-9]{6}$") && totpService != null) {
            autenticado = totpService.validateCode(usuario.mfaSecret(), codigo, Instant.now());
        }

        // 2. Si no es TOTP válido, intentar validar como código de respaldo uniuso
        if (!autenticado && mfaBackupCodeRepository != null) {
            String codeHash = TokenHashUtil.hash(codigo);
            autenticado = mfaBackupCodeRepository.consumeCode(usuario.id(), codeHash);
        }

        if (!autenticado) {
            auditService.recordEvent(
                    usuario.id(),
                    AuditableAction.MFA_LOGIN_FALLIDO,
                    "USUARIO",
                    usuario.publicId(),
                    AuditResult.FALLO,
                    ipOrigen
            );
            throw new InvalidCredentialsException("Codigo de verificacion TOTP o codigo de respaldo invalido.");
        }

        // Éxito: emitir tokens de sesión definitivos
        List<String> roles = userRepository.getRoles(usuario.id());
        String rawRefreshToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        String tokenHash = TokenHashUtil.hash(rawRefreshToken);
        Instant expiracionRefresh = Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS);
        refreshTokenRepository.create(usuario.id(), tokenHash, expiracionRefresh);

        String accessToken = jwtService.generateAccessToken(usuario.publicId(), usuario.email(), roles);

        AuthSessionResponse sessionResponse = new AuthSessionResponse(
                usuario.publicId(),
                usuario.email(),
                roles,
                "Inicio de sesion completado exitosamente con MFA.",
                usuario.mustChangePassword()
        );

        auditService.recordEvent(
                usuario.id(),
                AuditableAction.MFA_LOGIN_EXITOSO,
                "USUARIO",
                usuario.publicId(),
                AuditResult.EXITO,
                ipOrigen
        );
        auditService.recordEvent(
                usuario.id(),
                AuditableAction.LOGIN_EXITOSO,
                "USUARIO",
                usuario.publicId(),
                AuditResult.EXITO,
                ipOrigen
        );

        return new AuthTokens(accessToken, rawRefreshToken, sessionResponse);
    }
}
