package com.meditriaje.demo;

import com.meditriaje.dto.RegisterPatientRequest;
import com.meditriaje.dto.admin.CreateSpecialtyRequest;
import com.meditriaje.dto.admin.CreateProfessionalRequest;
import com.meditriaje.dto.admin.GenerateSlotsRequest;
import com.meditriaje.dto.admin.GenerateSlotsResponse;
import com.meditriaje.dto.admin.SlotResponse;
import com.meditriaje.dto.allergy.RegisterAllergyRequest;
import com.meditriaje.dto.appointment.CancelAppointmentRequest;
import com.meditriaje.dto.appointment.AppointmentResponse;
import com.meditriaje.dto.appointment.BookAppointmentRequest;
import com.meditriaje.dto.clinical.ClinicalAttentionResponse;
import com.meditriaje.dto.clinical.CloseAttentionRequest;
import com.meditriaje.dto.clinical.CreateAmendmentRequest;
import com.meditriaje.dto.clinical.StartAttentionRequest;
import com.meditriaje.dto.clinical.VitalSignsDto;
import com.meditriaje.dto.followup.CreateFollowUpRequest;
import com.meditriaje.dto.pharmacy.DeliveryDetailRequest;
import com.meditriaje.dto.pharmacy.RegisterDispensationRequest;
import com.meditriaje.dto.prescription.CreatePrescriptionDetailRequest;
import com.meditriaje.dto.prescription.CreatePrescriptionRequest;
import com.meditriaje.dto.prescription.MedicationResponse;
import com.meditriaje.dto.prescription.PrescriptionResponse;
import com.meditriaje.dto.triage.CreateTriageRequest;
import com.meditriaje.dto.triage.SymptomItemRequest;
import com.meditriaje.dto.triage.TriageResponse;
import com.meditriaje.model.Specialty;
import com.meditriaje.model.Patient;
import com.meditriaje.model.Professional;
import com.meditriaje.model.User;
import com.meditriaje.repository.AppointmentRepository;
import com.meditriaje.repository.DemoSeedRepository;
import com.meditriaje.repository.AvailabilitySlotRepository;
import com.meditriaje.repository.SpecialtyRepository;
import com.meditriaje.repository.MedicationRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.ProfessionalRepository;
import com.meditriaje.repository.UserRepository;
import com.meditriaje.service.AdminCatalogService;
import com.meditriaje.service.AdminProfessionalService;
import com.meditriaje.service.AllergyService;
import com.meditriaje.service.AppointmentService;
import com.meditriaje.service.AuthService;
import com.meditriaje.service.ClinicalAttentionService;
import com.meditriaje.service.DispensationService;
import com.meditriaje.service.FollowUpService;
import com.meditriaje.service.PrescriptionService;
import com.meditriaje.service.SlotGeneratorService;
import com.meditriaje.service.TriageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Seeder de datos de demostración para MediTriaje 2.0 (Plan Post-Auditoría §T8.2).
 * Desactivado por defecto. Se activa exclusivamente con {@code DEMO_SEED=true}.
 *
 * <p>Reglas de seguridad y diseño:
 * <ul>
 *   <li>Es idempotente: comprueba si los datos ya fueron generados antes de sembrar.</li>
 *   <li>Usa exclusivamente el dominio reservado {@code @demo.meditriaje.test}.</li>
 *   <li><b>Se niega tajantemente a ejecutarse</b> si la base de datos contiene usuarios de otros dominios.</li>
 *   <li>Contraseña obligatoria desde la variable {@code DEMO_PASSWORD} (mínimo 12 caracteres).</li>
 *   <li>Crea los datos mediante los <b>servicios de la aplicación</b> para respetar triggers, auditoría e inmutabilidad.</li>
 * </ul>
 */
@Component
@Order(100)
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String DEMO_DOMAIN = "@demo.meditriaje.test";
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private static final String IP_LOCAL = "127.0.0.1";

    private final Environment environment;
    private final DemoSeedRepository demoSeedRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final ProfessionalRepository professionalRepository;
    private final SpecialtyRepository specialtyRepository;
    private final AvailabilitySlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final MedicationRepository medicationRepository;
    private final AdminCatalogService adminCatalogService;
    private final AdminProfessionalService adminProfessionalService;
    private final AuthService authService;
    private final SlotGeneratorService slotGeneratorService;
    private final TriageService triageService;
    private final AppointmentService appointmentService;
    private final ClinicalAttentionService clinicalAttentionService;
    private final PrescriptionService prescriptionService;
    private final AllergyService allergyService;
    private final FollowUpService followUpService;
    private final DispensationService dispensationService;

    public DemoDataSeeder(
            Environment environment,
            DemoSeedRepository demoSeedRepository,
            PasswordEncoder passwordEncoder,
            UserRepository userRepository,
            PatientRepository patientRepository,
            ProfessionalRepository professionalRepository,
            SpecialtyRepository specialtyRepository,
            AvailabilitySlotRepository slotRepository,
            AppointmentRepository appointmentRepository,
            MedicationRepository medicationRepository,
            AdminCatalogService adminCatalogService,
            AdminProfessionalService adminProfessionalService,
            AuthService authService,
            SlotGeneratorService slotGeneratorService,
            TriageService triageService,
            AppointmentService appointmentService,
            ClinicalAttentionService clinicalAttentionService,
            PrescriptionService prescriptionService,
            AllergyService allergyService,
            FollowUpService followUpService,
            DispensationService dispensationService
    ) {
        this.environment = Objects.requireNonNull(environment, "environment no puede ser nulo");
        this.demoSeedRepository = Objects.requireNonNull(demoSeedRepository, "demoSeedRepository no puede ser nulo");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder no puede ser nulo");
        this.userRepository = Objects.requireNonNull(userRepository, "usuarioRepository no puede ser nulo");
        this.patientRepository = Objects.requireNonNull(patientRepository, "pacienteRepository no puede ser nulo");
        this.professionalRepository = Objects.requireNonNull(professionalRepository, "profesionalRepository no puede ser nulo");
        this.specialtyRepository = Objects.requireNonNull(specialtyRepository, "especialidadRepository no puede ser nulo");
        this.slotRepository = Objects.requireNonNull(slotRepository, "slotRepository no puede ser nulo");
        this.appointmentRepository = Objects.requireNonNull(appointmentRepository, "citaRepository no puede ser nulo");
        this.medicationRepository = Objects.requireNonNull(medicationRepository, "medicamentoRepository no puede ser nulo");
        this.adminCatalogService = Objects.requireNonNull(adminCatalogService, "adminCatalogService no puede ser nulo");
        this.adminProfessionalService = Objects.requireNonNull(adminProfessionalService, "adminProfessionalService no puede ser nulo");
        this.authService = Objects.requireNonNull(authService, "authService no puede ser nulo");
        this.slotGeneratorService = Objects.requireNonNull(slotGeneratorService, "slotGeneratorService no puede ser nulo");
        this.triageService = Objects.requireNonNull(triageService, "triajeService no puede ser nulo");
        this.appointmentService = Objects.requireNonNull(appointmentService, "appointmentService no puede ser nulo");
        this.clinicalAttentionService = Objects.requireNonNull(clinicalAttentionService, "clinicalAttentionService no puede ser nulo");
        this.prescriptionService = Objects.requireNonNull(prescriptionService, "prescriptionService no puede ser nulo");
        this.allergyService = Objects.requireNonNull(allergyService, "allergyService no puede ser nulo");
        this.followUpService = Objects.requireNonNull(followUpService, "followUpService no puede ser nulo");
        this.dispensationService = Objects.requireNonNull(dispensationService, "dispensationService no puede ser nulo");
    }

    @Override
    public void run(String... args) {
        String seedEnabled = environment.getProperty("DEMO_SEED");
        if (seedEnabled == null || seedEnabled.isBlank()) {
            seedEnabled = environment.getProperty("meditriaje.demo.seed", "false");
        }
        seedEnabled = seedEnabled != null ? seedEnabled.trim() : "false";

        if (!"true".equalsIgnoreCase(seedEnabled)) {
            log.debug("DemoDataSeeder: Inactivo (DEMO_SEED != true).");
            return;
        }

        log.info("DemoDataSeeder: Iniciando verificación de seguridad y pre-condiciones de siembra...");

        // 1. Validar contraseña demo requerida (mínimo 12 caracteres, nunca por defecto)
        String demoPassword = environment.getProperty("DEMO_PASSWORD");
        if (demoPassword == null || demoPassword.isBlank()) {
            demoPassword = environment.getProperty("meditriaje.demo.password", "");
        }
        demoPassword = demoPassword != null ? demoPassword.trim() : "";

        if (demoPassword.length() < 12) {
            String errorMsg = "DemoDataSeeder: Operación abortada. La variable DEMO_PASSWORD debe estar definida con al menos 12 caracteres.";
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        // 2. Advertencia sobre DEMO_RESET e inmutabilidad clínica
        String demoReset = environment.getProperty("DEMO_RESET");
        if (demoReset == null || demoReset.isBlank()) {
            demoReset = environment.getProperty("meditriaje.demo.reset", "false");
        }
        demoReset = demoReset != null ? demoReset.trim() : "false";

        if ("true".equalsIgnoreCase(demoReset)) {
            log.warn("DemoDataSeeder: DEMO_RESET=true detectado. Por diseño de inmutabilidad clínica (ADR-008, ADR-011, ADR-016), " +
                    "las tablas ATENCION, RECETA, ALERGIA y AUDITORIA cuentan con triggers Oracle que impiden DELETE físico, y el usuario " +
                    "MEDITRIAJE_APP carece de privilegios DELETE en ellas. Para un reinicio total de esquema demo, aplique 'flyway clean' o " +
                    "re-ejecute el aprovisionamiento de esquema en Oracle ATP con MEDITRIAJE_OWNER.");
        }

        // 3. Blindaje de aislamiento: NEGATIVA a ejecutarse si hay usuarios de otros dominios
        int usuariosNoDemo = demoSeedRepository.countUsersOutsideDomain(DEMO_DOMAIN);

        if (usuariosNoDemo > 0) {
            String errorAislamiento = "DemoDataSeeder: ABORTADO POR SEGURIDAD. La base de datos contiene " + usuariosNoDemo +
                    " usuario(s) que no pertenecen al dominio reservado '" + DEMO_DOMAIN + "'. " +
                    "Para prevenir contaminación accidental de datos reales o entornos no demo, la siembra ha sido rechazada.";
            log.error(errorAislamiento);
            throw new IllegalStateException(errorAislamiento);
        }

        // 4. Idempotencia: si ya existen usuarios demo sembrados, omitir
        int usuariosDemo = demoSeedRepository.countUsersInDomain(DEMO_DOMAIN);

        if (usuariosDemo >= 17) {
            log.info("DemoDataSeeder: Datos de demostración ya sembrados ({} usuarios '{}' detectados). Omitiendo siembra.",
                    usuariosDemo, DEMO_DOMAIN);
            return;
        }

        log.info("DemoDataSeeder: Entorno validado. Procediendo a sembrar datos de demostración mediante servicios de dominio...");

        try {
            runSeed(demoPassword);
            log.info("DemoDataSeeder: ¡Siembra de datos de demostración completada con éxito!");
        } catch (Exception e) {
            log.error("DemoDataSeeder: Error durante la siembra de datos demo: {}", e.getMessage(), e);
            throw new IllegalStateException("Error en siembra de demostración: " + e.getMessage(), e);
        }
    }

    private void runSeed(String demoPassword) {
        String hashDemo = passwordEncoder.encode(demoPassword);

        // A. Crear Administrador Demo
        String adminPublicId = UUID.randomUUID().toString();
        String adminEmail = "admin" + DEMO_DOMAIN;
        Long adminId;
        if (!userRepository.existsByEmail(adminEmail)) {
            adminId = userRepository.create(adminPublicId, adminEmail, hashDemo, false);
            Long rolAdmin = userRepository.findRoleIdByName("ROLE_ADMINISTRADOR")
                    .orElseThrow(() -> new IllegalStateException("Rol ROLE_ADMINISTRADOR no encontrado en BD."));
            userRepository.assignRole(adminId, rolAdmin);
            log.info("Demo: Administrador creado: {}", adminEmail);
        } else {
            User u = userRepository.findByEmail(adminEmail).orElseThrow();
            adminId = u.id();
            adminPublicId = u.publicId();
        }

        // B. Crear Farmacéutico Demo
        String farmEmail = "farmacia" + DEMO_DOMAIN;
        String farmPublicId = UUID.randomUUID().toString();
        if (!userRepository.existsByEmail(farmEmail)) {
            Long farmId = userRepository.create(farmPublicId, farmEmail, hashDemo, false);
            Long rolFarm = userRepository.findRoleIdByName("ROLE_FARMACEUTICO")
                    .orElseThrow(() -> new IllegalStateException("Rol ROLE_FARMACEUTICO no encontrado en BD."));
            userRepository.assignRole(farmId, rolFarm);
            log.info("Demo: Farmacéutico creado: {}", farmEmail);
        } else {
            farmPublicId = userRepository.findByEmail(farmEmail).orElseThrow().publicId();
        }

        // C. Hospital único: la sede la crea la migración V024; no se crean sedes ni instituciones.

        // D. Especialidades (4)
        String espMedGeneral = getOrCreateSpecialty("Medicina General", 20, adminPublicId);
        String espMedInterna = getOrCreateSpecialty("Medicina Interna", 30, adminPublicId);
        String espPediatria = getOrCreateSpecialty("Pediatría", 20, adminPublicId);
        String espCardiologia = getOrCreateSpecialty("Cardiología", 30, adminPublicId);

        // E. 5 Profesionales Asistenciales y 1 Personal de Enfermería
        List<DemoProfessionalInfo> profesionales = new ArrayList<>();
        profesionales.add(registerDemoProfessional("dra.gomez" + DEMO_DOMAIN, "1018273645", "RM-102938", "María Paula", "Gómez Vargas", espMedGeneral, adminPublicId, hashDemo));
        profesionales.add(registerDemoProfessional("dr.rodriguez" + DEMO_DOMAIN, "1029384756", "RM-203948", "Carlos Eduardo", "Rodríguez Peña", espMedGeneral, adminPublicId, hashDemo));
        profesionales.add(registerDemoProfessional("dra.restrepo" + DEMO_DOMAIN, "1038475629", "RM-304958", "Ana Lucía", "Restrepo Mejia", espMedInterna, adminPublicId, hashDemo));
        profesionales.add(registerDemoProfessional("dr.silva" + DEMO_DOMAIN, "1049586738", "RM-405968", "Felipe Andrés", "Silva Castro", espPediatria, adminPublicId, hashDemo));
        profesionales.add(registerDemoProfessional("dr.martinez" + DEMO_DOMAIN, "1058674930", "RM-506978", "Jorge Hernán", "Martínez Osorio", espCardiologia, adminPublicId, hashDemo));

        // E2. Personal de Enfermería y Triaje Presencial (Fase U, ADR-022, ADR-026)
        registerDemoProfessional("enfermera" + DEMO_DOMAIN, "1098765432", "ENF-102938", "Beatriz Elena", "Valencia Ramos", espMedGeneral, adminPublicId, hashDemo, "ROLE_ENFERMERIA");

        // F. Generar agendas de los próximos 14 días para los profesionales
        LocalDate fechaInicioSlots = LocalDate.now(BOGOTA_ZONE).plusDays(1);
        LocalDate fechaFinSlots = fechaInicioSlots.plusDays(13);
        List<SlotResponse> slotsGenerados = new ArrayList<>();

        for (int i = 0; i < profesionales.size(); i++) {
            DemoProfessionalInfo prof = profesionales.get(i);
            try {
                GenerateSlotsResponse resSlots = slotGeneratorService.generateSlots(
                        new GenerateSlotsRequest(
                                prof.professionalPublicId(),
                                fechaInicioSlots,
                                fechaFinSlots,
                                LocalTime.of(8, 0),
                                LocalTime.of(12, 0),
                                prof.slotDurationMinutes(),
                                "PRESENCIAL",
                                List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
                        ),
                        adminPublicId, IP_LOCAL
                );
                slotsGenerados.addAll(resSlots.slots());
                log.info("Demo: Generados {} slots para {}", resSlots.generatedSlots(), prof.email());
            } catch (Exception e) {
                log.warn("Demo: No se pudieron generar slots para {}: {}", prof.email(), e.getMessage());
            }
        }

        // G. 10 Pacientes con Historia Clínica
        List<DemoPatientInfo> pacientes = new ArrayList<>();
        pacientes.add(registerDemoPatient("laura.morales" + DEMO_DOMAIN, "1020304050", "Laura Sofía", "Morales Ruiz", LocalDate.of(1995, 5, 12), "3101234567", demoPassword));
        pacientes.add(registerDemoPatient("santiago.moreno" + DEMO_DOMAIN, "1030405060", "Santiago", "Moreno Vargas", LocalDate.of(1990, 8, 20), "3112345678", demoPassword));
        pacientes.add(registerDemoPatient("valentina.castro" + DEMO_DOMAIN, "1040506070", "Valentina", "Castro Ríos", LocalDate.of(1988, 2, 14), "3123456789", demoPassword));
        pacientes.add(registerDemoPatient("mateo.herrera" + DEMO_DOMAIN, "1050607080", "Mateo", "Herrera Gómez", LocalDate.of(2000, 11, 3), "3134567890", demoPassword));
        pacientes.add(registerDemoPatient("camila.pena" + DEMO_DOMAIN, "1060708090", "Camila Andrea", "Peña Salazar", LocalDate.of(1993, 7, 25), "3145678901", demoPassword));
        pacientes.add(registerDemoPatient("daniel.torres" + DEMO_DOMAIN, "1070809010", "Daniel Esteban", "Torres Nieto", LocalDate.of(1985, 9, 18), "3156789012", demoPassword));
        pacientes.add(registerDemoPatient("mariana.duque" + DEMO_DOMAIN, "1080901020", "Mariana", "Duque Zuluaga", LocalDate.of(1997, 4, 30), "3167890123", demoPassword));
        pacientes.add(registerDemoPatient("andres.quintana" + DEMO_DOMAIN, "1090102030", "Andrés Felipe", "Quintana Marín", LocalDate.of(1992, 12, 8), "3178901234", demoPassword));
        pacientes.add(registerDemoPatient("isabel.salgado" + DEMO_DOMAIN, "1011223344", "Isabel Cristina", "Salgado Cano", LocalDate.of(1999, 1, 19), "3189012345", demoPassword));
        pacientes.add(registerDemoPatient("nicolas.osorio" + DEMO_DOMAIN, "1022334455", "Nicolás", "Osorio Pardo", LocalDate.of(1994, 6, 22), "3190123456", demoPassword));

        // H. Flujo Clínico 1: Triaje leve -> Cita -> Atención -> Diagnóstico -> Receta -> Alergia -> Seguimiento -> Dispensación
        if (!slotsGenerados.isEmpty()) {
            try {
                DemoPatientInfo p1 = pacientes.get(0); // Laura
                DemoProfessionalInfo med1 = profesionales.get(0); // Dra. Gómez

                // Triaje leve Nivel III
                TriageResponse triaje1 = triageService.evaluateAndSaveTriage(
                        new CreateTriageRequest(
                                List.of(new SymptomItemRequest("SIN-001", new BigDecimal("24.0"), 4)),
                                "Dolor de cabeza tipo opresivo de intensidad moderada sin signos de alarma."
                        ),
                        p1.userPublicId(), IP_LOCAL
                );

                // Reservar cita
                SlotResponse slot1 = slotsGenerados.stream()
                        .filter(s -> s.professionalPublicId().equals(med1.professionalPublicId()))
                        .findFirst().orElse(slotsGenerados.get(0));

                AppointmentResponse cita1 = appointmentService.bookAppointment(
                        new BookAppointmentRequest(slot1.publicId(), triaje1.publicId()),
                        p1.userPublicId(), IP_LOCAL
                );

                // Iniciar y cerrar atención médica
                ClinicalAttentionResponse atencion1 = clinicalAttentionService.startAttention(
                        new StartAttentionRequest(cita1.publicId()),
                        med1.userPublicId(), IP_LOCAL
                );

                VitalSignsDto signos1 = new VitalSignsDto(
                        120, 80, 72, 16,
                        new BigDecimal("36.5"), 98,
                        new BigDecimal("62.5"), new BigDecimal("165.0")
                );

                clinicalAttentionService.closeAttention(
                        atencion1.publicId(),
                        new CloseAttentionRequest(
                                "R51",
                                "Cefalea tensional de 24 horas de evolución.",
                                "Paciente alerta, orientada, sin signos de focalización neurológica.",
                                "Manejo ambulatorio con analgésicos, reposo e hidratación adecuada.",
                                signos1
                        ),
                        med1.userPublicId(), IP_LOCAL
                );

                // Registrar alergia médica profesional
                allergyService.registerAllergyByProfessional(
                        p1.patientPublicId(),
                        new RegisterAllergyRequest("Penicilina", "Anafilaxia previa en la infancia", "GRAVE", atencion1.publicId()),
                        med1.userPublicId(), IP_LOCAL
                );

                // Emitir receta médica con medicamentos del catálogo
                String medAcetaminofen = getMedicationPublicId("MED-ACE-500");
                String medIbuprofeno = getMedicationPublicId("MED-IBU-400");

                PrescriptionResponse receta1 = prescriptionService.issuePrescription(
                        new CreatePrescriptionRequest(
                                atencion1.publicId(),
                                30,
                                List.of(
                                        new CreatePrescriptionDetailRequest(medAcetaminofen, "500 mg", "Cada 8 horas", 3, 10, "Tomar después de las comidas"),
                                        new CreatePrescriptionDetailRequest(medIbuprofeno, "400 mg", "Cada 12 horas en caso de dolor severo", 2, 6, "Uso condicionado a dolor")
                                )
                        ),
                        med1.userPublicId(), IP_LOCAL
                );

                // Prescribir seguimiento post-atención
                followUpService.prescribeFollowUp(
                        atencion1.publicId(),
                        new CreateFollowUpRequest("CONTROL_MEDICO", "Control ambulatorio para reevaluación de cefalea", LocalDate.now(BOGOTA_ZONE).plusDays(7)),
                        med1.userPublicId(), IP_LOCAL
                );

                // Dispensación farmacéutica de la receta
                dispensationService.registerDispensation(
                        new RegisterDispensationRequest(
                                receta1.publicId(),
                                "Dispensación completa de analgésico primario.",
                                List.of(new DeliveryDetailRequest(medAcetaminofen, 10, "LOTE-2026-A1", LocalDate.now(BOGOTA_ZONE).plusYears(2)))
                        ),
                        farmPublicId,
                        List.of(new SimpleGrantedAuthority("ROLE_FARMACEUTICO")),
                        IP_LOCAL
                );

                log.info("Demo: Flujo clínico integral 1 completado para paciente {}", p1.email());
            } catch (Exception e) {
                log.warn("Demo: Advertencia en flujo clínico 1: {}", e.getMessage());
            }
        }

        // I. Flujo 2: Triaje de Emergencia Vital (Nivel I)
        try {
            DemoPatientInfo p2 = pacientes.get(1); // Santiago
            triageService.evaluateAndSaveTriage(
                    new CreateTriageRequest(
                            List.of(new SymptomItemRequest("SIN-007", new BigDecimal("1.0"), 9)),
                            "Dolor opresivo retroesternal con irradiación a miembro superior izquierdo y diaforesis."
                    ),
                    p2.userPublicId(), IP_LOCAL
            );
            log.info("Demo: Triaje de emergencia vital (Nivel I) registrado para paciente {}", p2.email());
        } catch (Exception e) {
            log.warn("Demo: Advertencia en triaje de emergencia: {}", e.getMessage());
        }

        // J. Flujo 3: Atención con Enmienda Médica Inmutable
        if (slotsGenerados.size() > 1) {
            try {
                DemoPatientInfo p3 = pacientes.get(2); // Valentina
                DemoProfessionalInfo med2 = profesionales.get(1); // Dr. Rodríguez

                SlotResponse slot2 = slotsGenerados.stream()
                        .filter(s -> s.professionalPublicId().equals(med2.professionalPublicId()) && "LIBRE".equals(s.status()))
                        .findFirst().orElse(slotsGenerados.get(1));

                AppointmentResponse cita2 = appointmentService.bookAppointment(
                        new BookAppointmentRequest(slot2.publicId(), null),
                        p3.userPublicId(), IP_LOCAL
                );

                ClinicalAttentionResponse atencion2 = clinicalAttentionService.startAttention(
                        new StartAttentionRequest(cita2.publicId()),
                        med2.userPublicId(), IP_LOCAL
                );

                VitalSignsDto signos2 = new VitalSignsDto(
                        145, 95, 80, 18,
                        new BigDecimal("36.7"), 97,
                        new BigDecimal("75.0"), new BigDecimal("160.0")
                );

                clinicalAttentionService.closeAttention(
                        atencion2.publicId(),
                        new CloseAttentionRequest(
                                "I10",
                                "Control de hipertensión arterial.",
                                "Cifras tensionales elevadas en dos tomas.",
                                "Ajuste farmacológico y control de factores de riesgo.",
                                signos2
                        ),
                        med2.userPublicId(), IP_LOCAL
                );

                // Enmienda médica obligatoria (ADR-008)
                clinicalAttentionService.createAmendment(
                        atencion2.publicId(),
                        new CreateAmendmentRequest(
                                "Aclaración sobre toma tensional",
                                "Se constató que la segunda toma fue realizada con 15 minutos de reposo previo."
                        ),
                        med2.userPublicId(), IP_LOCAL
                );

                // Receta para hipertensión
                String medLosartan = getMedicationPublicId("MED-LOS-050");
                prescriptionService.issuePrescription(
                        new CreatePrescriptionRequest(
                                atencion2.publicId(),
                                30,
                                List.of(new CreatePrescriptionDetailRequest(medLosartan, "50 mg", "Una tableta diaria en ayunas", 30, 30, "No suspender bruscamente"))
                        ),
                        med2.userPublicId(), IP_LOCAL
                );

                log.info("Demo: Atención con enmienda y receta completada para paciente {}", p3.email());
            } catch (Exception e) {
                log.warn("Demo: Advertencia en flujo con enmienda: {}", e.getMessage());
            }
        }

        // K. Flujo 4: Alergia autorreportada por paciente
        try {
            DemoPatientInfo p4 = pacientes.get(3); // Mateo
            allergyService.registerMyAllergy(
                    new RegisterAllergyRequest("Polen y gramíneas", "Rinitis alérgica estacional y prurito nasal", "LEVE", null),
                    p4.userPublicId(), IP_LOCAL
            );
            log.info("Demo: Alergia autorreportada registrada para paciente {}", p4.email());
        } catch (Exception e) {
            log.warn("Demo: Advertencia en alergia autorreportada: {}", e.getMessage());
        }

        // L. Flujo 5: Alergia inactivada
        try {
            DemoPatientInfo p5 = pacientes.get(4); // Camila
            DemoProfessionalInfo med1 = profesionales.get(0); // Dra. Gómez

            var alResp = allergyService.registerAllergyByProfessional(
                    p5.patientPublicId(),
                    new RegisterAllergyRequest("Amoxicilina", "Exantema macular leve en infancia", "LEVE", null),
                    med1.userPublicId(), IP_LOCAL
            );

            allergyService.inactivateAllergyByProfessional(
                    alResp.publicId(),
                    new com.meditriaje.dto.allergy.InactivateAllergyRequest("Prueba de provocación oral negativa en unidad de alergología hospitalaria"),
                    med1.userPublicId(), IP_LOCAL
            );
            log.info("Demo: Alergia inactivada registrada para paciente {}", p5.email());
        } catch (Exception e) {
            log.warn("Demo: Advertencia en alergia inactivada: {}", e.getMessage());
        }

        // M. Flujo 6: Cita Cancelada por Paciente
        if (slotsGenerados.size() > 2) {
            try {
                DemoPatientInfo p6 = pacientes.get(5); // Daniel
                SlotResponse slot6 = slotsGenerados.stream()
                        .filter(s -> "LIBRE".equals(s.status()))
                        .findFirst().orElse(null);

                if (slot6 != null) {
                    AppointmentResponse cita6 = appointmentService.bookAppointment(
                            new BookAppointmentRequest(slot6.publicId(), null),
                            p6.userPublicId(), IP_LOCAL
                    );

                    appointmentService.cancelAppointment(
                            cita6.publicId(),
                            new CancelAppointmentRequest("Compromiso laboral imprevisto fuera de la ciudad"),
                            p6.userPublicId(),
                            List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")),
                            IP_LOCAL
                    );
                    log.info("Demo: Cita cancelada con éxito para paciente {}", p6.email());
                }
            } catch (Exception e) {
                log.warn("Demo: Advertencia en cita cancelada: {}", e.getMessage());
            }
        }

        // N. Flujo 7: Cita con Inasistencia (No-Show)
        if (slotsGenerados.size() > 3) {
            try {
                DemoPatientInfo p7 = pacientes.get(6); // Mariana
                DemoProfessionalInfo med2 = profesionales.get(1); // Dr. Rodríguez

                SlotResponse slot7 = slotsGenerados.stream()
                        .filter(s -> "LIBRE".equals(s.status()))
                        .findFirst().orElse(null);

                if (slot7 != null) {
                    AppointmentResponse cita7 = appointmentService.bookAppointment(
                            new BookAppointmentRequest(slot7.publicId(), null),
                            p7.userPublicId(), IP_LOCAL
                    );

                    // Para cumplir la regla temporal (now >= inicio de la cita), ajustar transitoriamente el horario del slot
                    demoSeedRepository.moveSlotToPastForDemo(slot7.publicId());

                    appointmentService.markNoShow(
                            cita7.publicId(),
                            med2.userPublicId(),
                            List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL")),
                            IP_LOCAL
                    );
                    log.info("Demo: Cita marcada como NO_ASISTIO con éxito para paciente {}", p7.email());
                }
            } catch (Exception e) {
                log.warn("Demo: Advertencia en cita no-show: {}", e.getMessage());
            }
        }

        // O. Citas Programadas Activas para los pacientes restantes (8, 9, 10)
        for (int pIdx = 7; pIdx < pacientes.size(); pIdx++) {
            DemoPatientInfo p = pacientes.get(pIdx);
            SlotResponse sLibre = slotsGenerados.stream()
                    .filter(s -> "LIBRE".equals(s.status()))
                    .findFirst().orElse(null);

            if (sLibre != null) {
                try {
                    appointmentService.bookAppointment(
                            new BookAppointmentRequest(sLibre.publicId(), null),
                            p.userPublicId(), IP_LOCAL
                    );
                    log.info("Demo: Cita PROGRAMADA agendada para paciente {}", p.email());
                } catch (Exception e) {
                    log.warn("Demo: Advertencia al agendar cita para {}: {}", p.email(), e.getMessage());
                }
            }
        }
    }

    private String getOrCreateSpecialty(String nombre, int duracionMin, String adminPublicId) {
        return specialtyRepository.list(0, 10, "ACTIVO").stream()
                .filter(e -> e.name().equalsIgnoreCase(nombre))
                .findFirst()
                .map(Specialty::publicId)
                .orElseGet(() -> adminCatalogService.createSpecialty(
                        new CreateSpecialtyRequest(nombre, duracionMin),
                        adminPublicId, IP_LOCAL
                ).publicId());
    }

    private DemoProfessionalInfo registerDemoProfessional(
            String email, String numDoc, String regMed, String nombres, String apellidos,
            String especialidadPublicId, String adminPublicId, String hashDemo
    ) {
        return registerDemoProfessional(email, numDoc, regMed, nombres, apellidos, especialidadPublicId, adminPublicId, hashDemo, "ROLE_PROFESIONAL");
    }

    private DemoProfessionalInfo registerDemoProfessional(
            String email, String numDoc, String regMed, String nombres, String apellidos,
            String especialidadPublicId, String adminPublicId, String hashDemo, String rol
    ) {
        if (!userRepository.existsByEmail(email)) {
            var resp = adminProfessionalService.registerProfessional(
                    new CreateProfessionalRequest("CC", numDoc, regMed, nombres, apellidos, email, "3109876543", especialidadPublicId, rol),
                    adminPublicId, IP_LOCAL
            );

            // Fijar contraseña oficial de demo para que pueda loguearse directamente
            User u = userRepository.findByEmail(email).orElseThrow();
            userRepository.updatePassword(u.id(), hashDemo, false);

            int duracion = specialtyRepository.findByPublicId(especialidadPublicId)
                    .map(Specialty::slotDurationMinutes).orElse(20);

            log.info("Demo: Personal asistencial creado con rol {}: {} ({})", rol, email, nombres + " " + apellidos);
            return new DemoProfessionalInfo(resp.publicId(), u.publicId(), u.id(), email, duracion);
        } else {
            User u = userRepository.findByEmail(email).orElseThrow();
            Professional p = professionalRepository.findByUserId(u.id()).orElseThrow();
            int duracion = specialtyRepository.findById(p.specialtyId())
                    .map(Specialty::slotDurationMinutes).orElse(20);
            return new DemoProfessionalInfo(p.publicId(), u.publicId(), u.id(), email, duracion);
        }
    }

    private DemoPatientInfo registerDemoPatient(
            String email, String numDoc, String nombres, String apellidos,
            LocalDate fechaNac, String telefono, String password
    ) {
        if (!userRepository.existsByEmail(email)) {
            var reg = authService.registerPatient(
                    new RegisterPatientRequest("CC", numDoc, nombres, apellidos, fechaNac, telefono, email, password, "v1.0", true),
                    IP_LOCAL
            );
            User u = userRepository.findByEmail(email).orElseThrow();
            Patient p = patientRepository.findByUserId(u.id()).orElseThrow();
            log.info("Demo: Paciente registrado: {} ({})", email, nombres + " " + apellidos);
            return new DemoPatientInfo(p.publicId(), u.publicId(), email);
        } else {
            User u = userRepository.findByEmail(email).orElseThrow();
            Patient p = patientRepository.findByUserId(u.id()).orElseThrow();
            return new DemoPatientInfo(p.publicId(), u.publicId(), email);
        }
    }

    private String getMedicationPublicId(String codigo) {
        return medicationRepository.listActive(codigo, 0, 1).stream()
                .filter(m -> m.code().equalsIgnoreCase(codigo))
                .findFirst()
                .map(MedicationResponse::publicId)
                .orElse("00000000-0000-7000-a000-000000000001");
    }

    private record DemoProfessionalInfo(
            String professionalPublicId,
            String userPublicId,
            Long userId,
            String email,
            int slotDurationMinutes
    ) {}

    private record DemoPatientInfo(
            String patientPublicId,
            String userPublicId,
            String email
    ) {}
}
