package com.meditriaje.service;

import com.meditriaje.dto.clinical.ClinicalAttentionResponse;
import com.meditriaje.dto.emergency.ConsultSummaryRequest;
import com.meditriaje.dto.emergency.summary.EmergencyAllergyDto;
import com.meditriaje.dto.emergency.summary.AttentionSummaryDto;
import com.meditriaje.dto.emergency.summary.ActiveMedicationDto;
import com.meditriaje.dto.emergency.summary.EmergencyPatientDto;
import com.meditriaje.dto.emergency.summary.HealthSummaryResponse;
import com.meditriaje.dto.prescription.PrescriptionResponse;
import com.meditriaje.exception.InvalidCredentialsException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.TemporaryQrAccess;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.Allergy;
import com.meditriaje.model.QrAccessStatus;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.Patient;
import com.meditriaje.model.AuditResult;
import com.meditriaje.model.User;
import com.meditriaje.repository.TemporaryQrAccessRepository;
import com.meditriaje.repository.AllergyRepository;
import com.meditriaje.repository.ClinicalAttentionRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.PrescriptionRepository;
import com.meditriaje.repository.UserRepository;
import com.meditriaje.security.TokenHashUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Servicio de agregación y consulta controlada del resumen clínico de salud para emergencias (ADR-010, §5.17, §5.18, SEC-002).
 */
@Service
public class EmergencySummaryService {

    private static final Logger log = LoggerFactory.getLogger(EmergencySummaryService.class);

    public static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    public static final String LEGAL_WARNING = "El presente resumen clinico es un documento temporal de orientacion medica generado por el paciente bajo acceso controlado. No sustituye la historia clinica integral.";

    private final TemporaryQrAccessRepository temporaryQrAccessRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final AllergyRepository allergyRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final ClinicalAttentionRepository attentionRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Autowired
    public EmergencySummaryService(
            TemporaryQrAccessRepository temporaryQrAccessRepository,
            PatientRepository patientRepository,
            UserRepository userRepository,
            AllergyRepository allergyRepository,
            PrescriptionRepository prescriptionRepository,
            ClinicalAttentionRepository attentionRepository,
            AuditService auditService,
            PasswordEncoder passwordEncoder
    ) {
        this(
                temporaryQrAccessRepository,
                patientRepository,
                userRepository,
                allergyRepository,
                prescriptionRepository,
                attentionRepository,
                auditService,
                passwordEncoder,
                Clock.systemUTC()
        );
    }

    public EmergencySummaryService(
            TemporaryQrAccessRepository temporaryQrAccessRepository,
            PatientRepository patientRepository,
            UserRepository userRepository,
            AllergyRepository allergyRepository,
            PrescriptionRepository prescriptionRepository,
            ClinicalAttentionRepository attentionRepository,
            AuditService auditService,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.temporaryQrAccessRepository = Objects.requireNonNull(temporaryQrAccessRepository, "accesoTemporalQrRepository no puede ser nulo");
        this.patientRepository = Objects.requireNonNull(patientRepository, "pacienteRepository no puede ser nulo");
        this.userRepository = Objects.requireNonNull(userRepository, "usuarioRepository no puede ser nulo");
        this.allergyRepository = Objects.requireNonNull(allergyRepository, "alergiaRepository no puede ser nulo");
        this.prescriptionRepository = Objects.requireNonNull(prescriptionRepository, "recetaRepository no puede ser nulo");
        this.attentionRepository = Objects.requireNonNull(attentionRepository, "atencionRepository no puede ser nulo");
        this.auditService = Objects.requireNonNull(auditService, "auditoriaService no puede ser nulo");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    /**
     * Consulta pública protegida del resumen clínico de salud mediante token criptográfico y PIN opcional.
     * Valida vigencia, consume un acceso atómicamente y audita el evento sin exponer datos clínicos en bitácora.
     */
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public HealthSummaryResponse getSummaryByToken(String token, ConsultSummaryRequest request, String ipOrigen) {
        if (token == null || token.isBlank()) {
            throw new ResourceNotFoundException("Acceso de emergencia no valido o inexistente.");
        }

        String tokenHash = TokenHashUtil.hash(token.trim());
        TemporaryQrAccess acceso = temporaryQrAccessRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Acceso de emergencia no valido o inexistente."));

        Instant now = Instant.now(clock);
        QrAccessStatus estado = acceso.resolveStatus(now);

        if (estado == QrAccessStatus.REVOCADO) {
            throw new InvalidDataException("El acceso QR ha sido revocado por el paciente.");
        }
        if (estado == QrAccessStatus.EXPIRADO) {
            throw new InvalidDataException("El acceso QR ha expirado.");
        }
        if (estado == QrAccessStatus.AGOTADO) {
            throw new InvalidDataException("El acceso QR ha superado el limite maximo de lecturas permitidas.");
        }

        // Validación de PIN de seguridad si el token lo requiere (SEC-002)
        if (acceso.requiresPin()) {
            String pinProporcionado = (request != null && request.pin() != null) ? request.pin().trim() : "";
            // Comparación de PIN en tiempo constante con Argon2id
            String pinAValidar = pinProporcionado.isBlank() ? "----" : pinProporcionado;
            boolean coincide = false;
            try {
                coincide = passwordEncoder.matches(pinAValidar, acceso.pinHash());
            } catch (Exception ignored) {
                coincide = false;
            }
            boolean pinValido = coincide && !pinProporcionado.isBlank();

            if (!pinValido) {
                // Registrar intento fallido: incrementa fallos, cuenta para MAX_ACCESOS y revoca tras 3 fallos
                temporaryQrAccessRepository.recordFailedPin(acceso.id());
                int nuevosFallos = acceso.failedPinAttempts() + 1;
                boolean bloqueado = (nuevosFallos >= 3);

                try {
                    Patient pac = patientRepository.findById(acceso.patientId()).orElse(null);
                    Long usuarioId = (pac != null) ? pac.userId() : null;
                    if (usuarioId != null) {
                        auditService.audit(new AuditEvent(
                                usuarioId,
                                AuditableAction.ACCESO_EMERGENCIA_QR,
                                "RESUMEN_SALUD",
                                acceso.publicId(),
                                bloqueado ? AuditResult.BLOQUEADO : AuditResult.FALLO,
                                ipOrigen
                        ));
                    }
                } catch (Exception ex) {
                    log.warn("Fallo no bloqueante al auditar intento fallido de PIN QR: {}", ex.getMessage());
                }

                throw new InvalidCredentialsException(
                        bloqueado
                                ? "El acceso QR ha sido revocado por superar el limite de intentos fallidos de PIN."
                                : "El PIN de seguridad proporcionado es incorrecto o el acceso ha sido revocado."
                );
            }
        }

        // Registro atómico de acceso (decrementa cupo disponible en BD)
        int actualizados = temporaryQrAccessRepository.recordAccess(acceso.id(), now);
        if (actualizados == 0) {
            throw new InvalidDataException("El acceso QR ya no se encuentra disponible.");
        }

        Patient paciente = patientRepository.findById(acceso.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado."));

        User usuario = userRepository.findById(paciente.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        // Auditoría obligatoria inmutable (ADR-010, ADR-011)
        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.ACCESO_EMERGENCIA_QR,
                "RESUMEN_SALUD",
                acceso.publicId(),
                AuditResult.EXITO,
                ipOrigen
        ));

        // 1. Datos básicos del paciente
        LocalDate fechaHoy = LocalDate.ofInstant(now, BOGOTA_ZONE);
        int edad = paciente.birthDate() != null
                ? Period.between(paciente.birthDate(), fechaHoy).getYears()
                : 0;

        String telefono = acceso.includeContact() ? paciente.phone() : null;
        String email = acceso.includeContact() ? usuario.email() : null;

        EmergencyPatientDto pacienteDto = new EmergencyPatientDto(
                paciente.firstNames() + " " + paciente.lastNames(),
                paciente.documentType(),
                paciente.documentNumber(),
                paciente.birthDate(),
                edad,
                telefono,
                email
        );

        // 2. Alergias registradas (si el alcance lo autoriza)
        List<EmergencyAllergyDto> alergiasList = new ArrayList<>();
        if (acceso.includeAllergies()) {
            List<Allergy> alergias = allergyRepository.listByPatientId(paciente.id());
            for (Allergy a : alergias) {
                alergiasList.add(new EmergencyAllergyDto(a.substance(), a.reaction(), a.severity(), a.origin()));
            }
        }

        // 3. Medicamentos activos de recetas vigentes (si el alcance lo autoriza)
        List<ActiveMedicationDto> medicamentosList = new ArrayList<>();
        if (acceso.includeMedications()) {
            List<PrescriptionResponse> recetas = prescriptionRepository.listByPatientId(paciente.id(), 0, 50);
            for (PrescriptionResponse r : recetas) {
                if (r.createdAt() != null && r.createdAt().plus(r.validityDays(), ChronoUnit.DAYS).isAfter(now)) {
                    if (r.details() != null) {
                        for (var d : r.details()) {
                            medicamentosList.add(new ActiveMedicationDto(
                                    d.brandName(),
                                    d.activeIngredient(),
                                    d.presentation(),
                                    d.concentration(),
                                    d.dose(),
                                    d.frequency(),
                                    d.durationDays(),
                                    d.quantity(),
                                    d.instructions(),
                                    r.createdAt(),
                                    r.validityDays()
                            ));
                        }
                    }
                }
            }
        }

        // 4. Antecedentes / Atenciones clínicas recientes (si el alcance lo autoriza)
        List<AttentionSummaryDto> atencionesList = new ArrayList<>();
        if (acceso.includeAttentions()) {
            List<ClinicalAttentionResponse> atenciones = attentionRepository.listPatientHistory(paciente.id(), 0, 5);
            for (ClinicalAttentionResponse at : atenciones) {
                Instant fechaAtencion = at.closedAt() != null ? at.closedAt() : at.createdAt();
                atencionesList.add(new AttentionSummaryDto(
                        fechaAtencion,
                        at.specialtyName(),
                        at.diagnosisCode(),
                        at.diagnosisDescription(),
                        at.consultationReason(),
                        at.instructions()
                ));
            }
        }

        return new HealthSummaryResponse(
                pacienteDto,
                alergiasList,
                medicamentosList,
                atencionesList,
                now,
                LEGAL_WARNING
        );
    }
}
