package com.meditriaje.service;

import com.meditriaje.dto.admin.UpdateProfessionalRequest;
import com.meditriaje.dto.admin.CreateProfessionalRequest;
import com.meditriaje.dto.admin.CreateProfessionalResponse;
import com.meditriaje.dto.admin.ProfessionalResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.Specialty;
import com.meditriaje.model.Professional;
import com.meditriaje.model.AuditResult;
import com.meditriaje.model.User;
import com.meditriaje.repository.SpecialtyRepository;
import com.meditriaje.repository.ProfessionalRepository;
import com.meditriaje.repository.UserRepository;
import com.meditriaje.service.email.EmailService;
import com.meditriaje.util.ColombianStandardValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio de administración de profesionales asistenciales (HU-10, ADR-002, ADR-003, ADR-011, Ley 1164/2007).
 * Gestiona el alta con validaciones de talento humano en salud de Colombia (ReTHUS), contraseña temporal
 * de un solo uso, actualización de perfil asistencial, activación/desactivación y auditoría inmutable.
 */
@Service
public class AdminProfessionalService {

    private static final Logger log = LoggerFactory.getLogger(AdminProfessionalService.class);
    private static final String STATUS_ACTIVE = "ACTIVO";
    private static final String STATUS_INACTIVE = "INACTIVO";
    private static final String RESOURCE_PROFESSIONAL = "PROFESIONAL";

    private final ProfessionalRepository professionalRepository;
    private final UserRepository userRepository;
    private final SpecialtyRepository specialtyRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Autowired
    public AdminProfessionalService(
            ProfessionalRepository professionalRepository,
            UserRepository userRepository,
            SpecialtyRepository specialtyRepository,
            AuditService auditService,
            PasswordEncoder passwordEncoder,
            @Autowired(required = false) EmailService emailService
    ) {
        this.professionalRepository = Objects.requireNonNull(professionalRepository, "ProfesionalRepository no puede ser nulo");
        this.userRepository = Objects.requireNonNull(userRepository, "UsuarioRepository no puede ser nulo");
        this.specialtyRepository = Objects.requireNonNull(specialtyRepository, "EspecialidadRepository no puede ser nulo");
        this.auditService = Objects.requireNonNull(auditService, "AuditoriaService no puede ser nulo");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "PasswordEncoder no puede ser nulo");
        this.emailService = emailService;
    }

    public AdminProfessionalService(
            ProfessionalRepository professionalRepository,
            UserRepository userRepository,
            SpecialtyRepository specialtyRepository,
            AuditService auditService,
            PasswordEncoder passwordEncoder
    ) {
        this(professionalRepository, userRepository, specialtyRepository, auditService, passwordEncoder, null);
    }

    /**
     * Da de alta a un profesional asistencial validando exhaustivamente sus datos conforme a la ley colombiana
     * (Ley 1164 de 2007, Decreto 780 de 2016, ReTHUS), creando su cuenta de usuario con contraseña temporal segura,
     * asignando el rol asistencial y registrando el evento en la bitácora inmutable.
     */
    @Transactional
    public CreateProfessionalResponse registerProfessional(CreateProfessionalRequest request, String adminPublicId, String ipOrigen) {
        Objects.requireNonNull(request, "La solicitud de creacion no puede ser nula");

        // 0. Validar tipo y número de documento conforme a la ley colombiana (Ley 1164/2007)
        String tipoDoc = (request.documentType() != null && !request.documentType().isBlank())
                ? request.documentType().trim().toUpperCase(Locale.ROOT)
                : "CC";
        if (!"CC".equals(tipoDoc) && !"CE".equals(tipoDoc)) {
            throw new InvalidDataException("Tipo de documento no valido para profesionales en Colombia. Permitidos: CC, CE.");
        }

        String numDoc = (request.documentNumber() != null) ? request.documentNumber().trim() : "";
        if (numDoc.isBlank()) {
            throw new InvalidDataException("El numero de documento es obligatorio.");
        }
        ColombianStandardValidator.validateDocument(tipoDoc, numDoc);

        if (professionalRepository.existsByDocument(tipoDoc, numDoc)) {
            throw new InvalidDataException("Ya existe un profesional registrado con el documento ingresado.");
        }

        // 1. Validar nombres y apellidos según norma colombiana
        ColombianStandardValidator.validateNamesOrSurnames("nombres", request.firstNames());
        ColombianStandardValidator.validateNamesOrSurnames("apellidos", request.lastNames());

        // 2. Validar teléfono celular Colombia si fue provisto
        String telefonoNormalizado = null;
        if (request.phone() != null && !request.phone().isBlank()) {
            ColombianStandardValidator.validateColombianPhone(request.phone());
            telefonoNormalizado = request.phone().trim().replaceAll("\\s+", "");
        }

        // 3. Validar especialidad existente y activa
        Specialty esp = specialtyRepository.findByPublicId(request.specialtyPublicId().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Especialidad no encontrada: " + request.specialtyPublicId()));

        if (!STATUS_ACTIVE.equalsIgnoreCase(esp.status())) {
            throw new InvalidDataException("La especialidad seleccionada no se encuentra activa.");
        }

        // 4. Validar correo electronico unico
        String emailNormalizado = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(emailNormalizado)) {
            throw new InvalidDataException("El correo electronico ya se encuentra registrado.");
        }

        // 5. Validar registro medico / ReTHUS unico y formato
        String registroMedicoNormalizado = request.medicalLicense().trim();
        if (registroMedicoNormalizado.length() < 4) {
            throw new InvalidDataException("El registro medico / ReTHUS debe tener al menos 4 caracteres.");
        }
        if (professionalRepository.existsByMedicalLicense(registroMedicoNormalizado)) {
            throw new InvalidDataException("El registro medico ya se encuentra registrado.");
        }

        // 6. Generar contraseña temporal segura (mínimo 12 caracteres: mayúsculas, minúsculas, dígitos, símbolos)
        String passwordTemporal = generateTemporaryPassword();
        String passwordHash = passwordEncoder.encode(passwordTemporal);

        // 7. Crear USUARIO con debeCambiarPassword = true y estado ACTIVO
        String usuarioPublicId = UUID.randomUUID().toString();
        Long usuarioId = userRepository.create(usuarioPublicId, emailNormalizado, passwordHash, true);

        // 8. Asignar rol (ROLE_PROFESIONAL o ROLE_ENFERMERIA)
        String rolSolicitado = (request.role() != null && !request.role().isBlank()) ? request.role().trim() : "ROLE_PROFESIONAL";
        String rolNombre = ("ROLE_ENFERMERIA".equalsIgnoreCase(rolSolicitado) || "ENFERMERIA".equalsIgnoreCase(rolSolicitado))
                ? "ROLE_ENFERMERIA"
                : "ROLE_PROFESIONAL";
        Long rolId = userRepository.findRoleIdByName(rolNombre)
                .orElseThrow(() -> new IllegalStateException("El rol " + rolNombre + " no existe en el sistema."));
        userRepository.assignRole(usuarioId, rolId);

        // 9. Crear PROFESIONAL vinculado a usuarioId y especialidadId
        String profesionalPublicId = UUID.randomUUID().toString();
        Professional profesional = new Professional(
                usuarioId,
                profesionalPublicId,
                esp.id(),
                tipoDoc,
                numDoc,
                registroMedicoNormalizado,
                request.firstNames().trim(),
                request.lastNames().trim(),
                telefonoNormalizado
        );
        professionalRepository.create(profesional);

        // 10. Auditar evento administrativo
        Long adminId = getAdminUserId(adminPublicId);
        auditService.recordEvent(
                adminId,
                AuditableAction.CAMBIO_ADMINISTRATIVO,
                RESOURCE_PROFESSIONAL,
                profesionalPublicId,
                AuditResult.EXITO,
                ipOrigen
        );

        // 11. Despachar credenciales de acceso inicial por correo electrónico (Brevo SMTP)
        if (emailService != null) {
            try {
                String nombreCompleto = request.firstNames().trim() + " " + request.lastNames().trim();
                String rolDescripcion = "Profesional Asistencial (" + esp.name() + ")";
                emailService.sendInitialCredentials(emailNormalizado, nombreCompleto, rolDescripcion, passwordTemporal);
            } catch (Exception e) {
                log.warn("No fue posible despachar correo con credenciales iniciales a [{}]: {}", emailNormalizado, e.getMessage());
            }
        }

        return new CreateProfessionalResponse(
                profesionalPublicId,
                usuarioPublicId,
                tipoDoc,
                numDoc,
                registroMedicoNormalizado,
                request.firstNames().trim(),
                request.lastNames().trim(),
                emailNormalizado,
                telefonoNormalizado,
                esp.publicId(),
                esp.name(),
                passwordTemporal,
                true,
                Instant.now()
        );
    }

    /**
     * Actualiza los datos asistenciales del profesional (nombres, apellidos, especialidad, teléfono).
     */
    @Transactional
    public ProfessionalResponse updateProfessional(
            String publicId,
            UpdateProfessionalRequest request,
            String adminPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "La solicitud no puede ser nula");

        ColombianStandardValidator.validateNamesOrSurnames("nombres", request.firstNames());
        ColombianStandardValidator.validateNamesOrSurnames("apellidos", request.lastNames());

        Professional actual = professionalRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Profesional no encontrado: " + publicId));

        Specialty esp = specialtyRepository.findByPublicId(request.specialtyPublicId().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Especialidad no encontrada: " + request.specialtyPublicId()));

        if (!STATUS_ACTIVE.equalsIgnoreCase(esp.status())) {
            throw new InvalidDataException("La especialidad seleccionada no se encuentra activa.");
        }

        String telefonoActualizado = actual.phone();
        if (request.phone() != null) {
            if (!request.phone().isBlank()) {
                ColombianStandardValidator.validateColombianPhone(request.phone());
                telefonoActualizado = request.phone().trim().replaceAll("\\s+", "");
            } else {
                telefonoActualizado = null;
            }
        }

        Professional actualizado = new Professional(
                actual.id(),
                actual.userId(),
                actual.publicId(),
                esp.id(),
                actual.documentType(),
                actual.documentNumber(),
                actual.medicalLicense(),
                request.firstNames().trim(),
                request.lastNames().trim(),
                telefonoActualizado,
                actual.createdAt(),
                Instant.now()
        );
        professionalRepository.update(actualizado);

        Long adminId = getAdminUserId(adminPublicId);
        auditService.recordEvent(
                adminId,
                AuditableAction.CAMBIO_ADMINISTRATIVO,
                RESOURCE_PROFESSIONAL,
                publicId,
                AuditResult.EXITO,
                ipOrigen
        );

        User usuario = userRepository.findById(actual.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        return new ProfessionalResponse(
                actual.publicId(),
                usuario.publicId(),
                actual.documentType(),
                actual.documentNumber(),
                actual.medicalLicense(),
                request.firstNames().trim(),
                request.lastNames().trim(),
                usuario.email(),
                telefonoActualizado,
                esp.publicId(),
                esp.name(),
                usuario.status(),
                usuario.mustChangePassword(),
                actual.createdAt()
        );
    }

    /**
     * Cambia el estado de la cuenta del profesional (ACTIVO / INACTIVO) a nivel de USUARIO.
     */
    @Transactional
    public ProfessionalResponse changeStatus(String publicId, String nuevoEstado, String adminPublicId, String ipOrigen) {
        String estadoNormalizado = validateAndNormalizeStatus(nuevoEstado);

        Professional profesional = professionalRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Profesional no encontrado: " + publicId));

        userRepository.updateStatus(profesional.userId(), estadoNormalizado);

        Long adminId = getAdminUserId(adminPublicId);
        auditService.recordEvent(
                adminId,
                AuditableAction.CAMBIO_ADMINISTRATIVO,
                RESOURCE_PROFESSIONAL,
                publicId,
                AuditResult.EXITO,
                ipOrigen
        );

        User usuario = userRepository.findById(profesional.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Specialty esp = specialtyRepository.findById(profesional.specialtyId())
                .orElseThrow(() -> new ResourceNotFoundException("Especialidad no encontrada"));

        return new ProfessionalResponse(
                profesional.publicId(),
                usuario.publicId(),
                profesional.documentType(),
                profesional.documentNumber(),
                profesional.medicalLicense(),
                profesional.firstNames(),
                profesional.lastNames(),
                usuario.email(),
                profesional.phone(),
                esp.publicId(),
                esp.name(),
                estadoNormalizado,
                usuario.mustChangePassword(),
                profesional.createdAt()
        );
    }

    /**
     * Obtiene los datos detallados de un profesional asistencial.
     */
    @Transactional(readOnly = true)
    public ProfessionalResponse getByPublicId(String publicId) {
        Professional profesional = professionalRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Profesional no encontrado: " + publicId));

        User usuario = userRepository.findById(profesional.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Specialty esp = specialtyRepository.findById(profesional.specialtyId())
                .orElseThrow(() -> new ResourceNotFoundException("Especialidad no encontrada"));

        return new ProfessionalResponse(
                profesional.publicId(),
                usuario.publicId(),
                profesional.documentType(),
                profesional.documentNumber(),
                profesional.medicalLicense(),
                profesional.firstNames(),
                profesional.lastNames(),
                usuario.email(),
                profesional.phone(),
                esp.publicId(),
                esp.name(),
                usuario.status(),
                usuario.mustChangePassword(),
                profesional.createdAt()
        );
    }

    /**
     * Lista profesionales asistenciales con paginación y filtros opcionales por especialidad y estado.
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<ProfessionalResponse> list(int page, int size, String especialidadPublicId, String estado) {
        String estadoFiltro = null;
        if (estado != null && !estado.isBlank()) {
            estadoFiltro = validateAndNormalizeStatus(estado);
        }

        String especialidadFiltro = (especialidadPublicId != null && !especialidadPublicId.isBlank())
                ? especialidadPublicId.trim()
                : null;

        List<ProfessionalResponse> items = professionalRepository.list(page, size, especialidadFiltro, estadoFiltro);
        int total = professionalRepository.count(especialidadFiltro, estadoFiltro);

        return PaginatedResponse.of(items, page, size, (long) total);
    }

    // =========================================================================
    // UTILIDADES PRIVADAS
    // =========================================================================

    private Long getAdminUserId(String adminPublicId) {
        return userRepository.findByPublicId(adminPublicId)
                .map(User::id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario administrador"));
    }

    private String validateAndNormalizeStatus(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new InvalidDataException("El estado no puede ser vacio.");
        }
        String normalizado = estado.trim().toUpperCase(Locale.ROOT);
        if (!STATUS_ACTIVE.equals(normalizado) && !STATUS_INACTIVE.equals(normalizado)) {
            throw new InvalidDataException("Estado invalido. Los valores permitidos son: ACTIVO, INACTIVO.");
        }
        return normalizado;
    }

    /**
     * Genera una contraseña aleatoria de 14 caracteres garantizando al menos:
     * 2 mayúsculas, 2 minúsculas, 2 dígitos y 2 símbolos especiales.
     */
    private String generateTemporaryPassword() {
        final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        final String LOWER = "abcdefghijkmnopqrstuvwxyz";
        final String DIGITS = "23456789";
        final String SYMBOLS = "!@#$%&*+-_=";
        final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;

        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(14);
        sb.append(UPPER.charAt(random.nextInt(UPPER.length())));
        sb.append(UPPER.charAt(random.nextInt(UPPER.length())));
        sb.append(LOWER.charAt(random.nextInt(LOWER.length())));
        sb.append(LOWER.charAt(random.nextInt(LOWER.length())));
        sb.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        sb.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        sb.append(SYMBOLS.charAt(random.nextInt(SYMBOLS.length())));
        sb.append(SYMBOLS.charAt(random.nextInt(SYMBOLS.length())));

        for (int i = 0; i < 6; i++) {
            sb.append(ALL.charAt(random.nextInt(ALL.length())));
        }

        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }
        return new String(chars);
    }
}
