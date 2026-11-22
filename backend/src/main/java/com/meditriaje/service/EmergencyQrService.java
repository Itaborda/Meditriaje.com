package com.meditriaje.service;

import com.meditriaje.dto.emergency.QrAccessResponse;
import com.meditriaje.dto.emergency.GenerateQrRequest;
import com.meditriaje.dto.emergency.GenerateQrResponse;
import com.meditriaje.dto.emergency.VerifyQrResponse;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.TemporaryQrAccess;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.QrAccessStatus;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.Patient;
import com.meditriaje.model.AuditResult;
import com.meditriaje.model.User;
import com.meditriaje.repository.TemporaryQrAccessRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.UserRepository;
import com.meditriaje.security.TokenHashUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio para la generación, gestión y verificación de accesos temporales QR al resumen de salud (ADR-010, §5.17, §5.18).
 */
@Service
public class EmergencyQrService {

    public static final int VALIDITY_MINUTES = 15;
    public static final int MAX_ALLOWED_ACCESSES = 3;

    private final TemporaryQrAccessRepository temporaryQrAccessRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public EmergencyQrService(
            TemporaryQrAccessRepository temporaryQrAccessRepository,
            PatientRepository patientRepository,
            UserRepository userRepository,
            AuditService auditService,
            PasswordEncoder passwordEncoder
    ) {
        this(
                temporaryQrAccessRepository,
                patientRepository,
                userRepository,
                auditService,
                passwordEncoder,
                Clock.systemUTC()
        );
    }

    public EmergencyQrService(
            TemporaryQrAccessRepository temporaryQrAccessRepository,
            PatientRepository patientRepository,
            UserRepository userRepository,
            AuditService auditService,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.temporaryQrAccessRepository = Objects.requireNonNull(temporaryQrAccessRepository, "accesoTemporalQrRepository no puede ser nulo");
        this.patientRepository = Objects.requireNonNull(patientRepository, "pacienteRepository no puede ser nulo");
        this.userRepository = Objects.requireNonNull(userRepository, "usuarioRepository no puede ser nulo");
        this.auditService = Objects.requireNonNull(auditService, "auditoriaService no puede ser nulo");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    /**
     * Genera un nuevo token temporal criptográfico de 256 bits y registra el acceso QR para el paciente autenticado.
     */
    @Transactional
    public GenerateQrResponse generateQrAccess(GenerateQrRequest request, String usuarioAutenticadoPublicId, String ipOrigen) {
        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        Patient paciente = patientRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Solo pacientes registrados pueden generar accesos QR de emergencia."));

        // Generar token criptográfico opaco de 256 bits (32 bytes)
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        String tokenHash = TokenHashUtil.hash(token);

        String pinHash = null;
        if (request != null && request.pin() != null && !request.pin().isBlank()) {
            pinHash = passwordEncoder.encode(request.pin().trim());
        }

        Instant now = Instant.now(clock);
        Instant expiraAt = now.plus(VALIDITY_MINUTES, ChronoUnit.MINUTES);
        String publicId = UUID.randomUUID().toString();

        boolean incluirAlergias = request != null ? request.getIncludeAllergies() : true;
        boolean incluirMedicamentos = request != null ? request.getIncludeMedications() : true;
        boolean incluirAtenciones = request != null ? request.getIncludeAttentions() : true;
        boolean incluirContacto = request != null ? request.getIncludeContact() : true;

        TemporaryQrAccess nuevo = new TemporaryQrAccess(
                null,
                publicId,
                paciente.id(),
                tokenHash,
                pinHash,
                incluirAlergias,
                incluirMedicamentos,
                incluirAtenciones,
                incluirContacto,
                MAX_ALLOWED_ACCESSES,
                0,
                false,
                expiraAt,
                now,
                now
        );

        temporaryQrAccessRepository.create(nuevo);

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.GENERACION_QR_EMERGENCIA,
                "ACCESO_TEMPORAL_QR",
                publicId,
                AuditResult.EXITO,
                ipOrigen
        ));

        String qrUrl = "#/emergency-summary/" + token;

        return new GenerateQrResponse(
                publicId,
                token,
                qrUrl,
                expiraAt,
                MAX_ALLOWED_ACCESSES,
                pinHash != null,
                incluirAlergias,
                incluirMedicamentos,
                incluirAtenciones,
                incluirContacto,
                now
        );
    }

    /**
     * Lista los accesos QR generados por el paciente autenticado con su estado temporal resuelto.
     */
    @Transactional(readOnly = true)
    public List<QrAccessResponse> listMyQrAccesses(String usuarioAutenticadoPublicId) {
        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        Patient paciente = patientRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Solo pacientes registrados pueden consultar sus accesos QR."));

        List<TemporaryQrAccess> accesos = temporaryQrAccessRepository.listByPatientId(paciente.id());
        Instant now = Instant.now(clock);

        return accesos.stream()
                .map(a -> new QrAccessResponse(
                        a.publicId(),
                        a.resolveStatus(now).name(),
                        a.maxAccesses(),
                        a.accessCount(),
                        a.revoked(),
                        a.requiresPin(),
                        a.includeAllergies(),
                        a.includeMedications(),
                        a.includeAttentions(),
                        a.includeContact(),
                        a.expiresAt(),
                        a.createdAt()
                ))
                .toList();
    }

    /**
     * Revoca inmediatamente un acceso temporal QR a solicitud de su titular.
     */
    @Transactional
    public void revokeQrAccess(String publicId, String usuarioAutenticadoPublicId, String ipOrigen) {
        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        Patient paciente = patientRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Solo pacientes registrados pueden revocar accesos QR."));

        TemporaryQrAccess acceso = temporaryQrAccessRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Acceso temporal QR no encontrado."));

        if (!acceso.patientId().equals(paciente.id())) {
            throw new UnauthorizedAccessException("No tiene autorizacion para revocar un acceso QR ajeno.");
        }

        temporaryQrAccessRepository.revoke(publicId, paciente.id());

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.REVOCACION_QR_EMERGENCIA,
                "ACCESO_TEMPORAL_QR",
                publicId,
                AuditResult.EXITO,
                ipOrigen
        ));
    }

    /**
     * Verifica la validez pública de un token QR escaneado (indica vigencia y si requiere PIN).
     */
    @Transactional(readOnly = true)
    public VerifyQrResponse verifyPublicToken(String token) {
        if (token == null || token.isBlank()) {
            return new VerifyQrResponse(false, false, QrAccessStatus.EXPIRADO.name(), null);
        }

        String tokenHash = TokenHashUtil.hash(token.trim());
        Optional<TemporaryQrAccess> accesoOpt = temporaryQrAccessRepository.findByTokenHash(tokenHash);

        if (accesoOpt.isEmpty()) {
            return new VerifyQrResponse(false, false, QrAccessStatus.EXPIRADO.name(), null);
        }

        TemporaryQrAccess acceso = accesoOpt.get();
        Instant now = Instant.now(clock);
        QrAccessStatus estado = acceso.resolveStatus(now);
        boolean valido = (estado == QrAccessStatus.ACTIVO);

        return new VerifyQrResponse(
                valido,
                acceso.requiresPin(),
                estado.name(),
                acceso.expiresAt()
        );
    }
}
