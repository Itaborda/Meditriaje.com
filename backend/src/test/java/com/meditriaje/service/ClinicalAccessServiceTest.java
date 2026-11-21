package com.meditriaje.service;

import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.Patient;
import com.meditriaje.model.Professional;
import com.meditriaje.model.User;
import com.meditriaje.repository.ClinicalAttentionRepository;
import com.meditriaje.repository.BreakGlassRepository;
import com.meditriaje.repository.AppointmentRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.ProfessionalRepository;
import com.meditriaje.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para {@link AccesoClinicoService}.
 * Verifica de forma determinista y exhaustiva las reglas de autorización asistencial (ADR-007):
 * - Paciente dueño: SÍ.
 * - Otro paciente: NO (403).
 * - Administrador: NUNCA (403).
 * - Profesional con relación asistencial (cita activa futura o atención previa propia en <= 12 meses): SÍ.
 * - Profesional sin relación asistencial: NO (403).
 */
@ExtendWith(MockitoExtension.class)
class ClinicalAccessServiceTest {

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private static final Instant AHORA = Instant.parse("2026-10-03T18:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private ProfessionalRepository professionalRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private ClinicalAttentionRepository attentionRepository;

    @Mock
    private BreakGlassRepository breakGlassRepository;

    private Clock clock;
    private ClinicalAccessService clinicalAccessService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(AHORA, BOGOTA_ZONE);
        clinicalAccessService = new ClinicalAccessService(
                userRepository,
                patientRepository,
                professionalRepository,
                appointmentRepository,
                attentionRepository,
                breakGlassRepository,
                clock,
                12
        );
    }

    private User crearUsuario(Long id, String publicId, String email) {
        return new User(id, publicId, email, "hash", "ACTIVO", 0, null, AHORA, false);
    }

    private Patient crearPaciente(Long id, Long usuarioId, String publicId) {
        return new Patient(id, usuarioId, publicId, "CC", "12345678", "Juan", "Perez",
                LocalDate.of(1990, 1, 1), "3001234567", AHORA, AHORA);
    }

    @Test
    @DisplayName("Paciente dueño: puede acceder a su propio historial clínico")
    void ownerPatient_canAccederToItsOwnHistory() {
        String usuarioPublicId = "u-paciente-1";
        String pacientePublicId = "pac-001";
        User usuario = crearUsuario(10L, usuarioPublicId, "paciente@test.com");
        Patient paciente = crearPaciente(100L, 10L, pacientePublicId);

        when(userRepository.findByPublicId(usuarioPublicId)).thenReturn(Optional.of(usuario));
        when(patientRepository.findByUserId(10L)).thenReturn(Optional.of(paciente));

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PACIENTE"));

        assertThatCode(() -> clinicalAccessService.validateClinicalHistoryAccess(usuarioPublicId, pacientePublicId, authorities))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Otro paciente: no puede acceder al historial de otro paciente (403 Forbidden)")
    void otherPatient_notCanAccederToHistoryClinicalOther() {
        String usuarioPublicId = "u-paciente-1";
        String pacientePropioPublicId = "pac-001";
        String pacienteAjenoPublicId = "pac-999";

        User usuario = crearUsuario(10L, usuarioPublicId, "paciente1@test.com");
        Patient paciente = crearPaciente(100L, 10L, pacientePropioPublicId);

        when(userRepository.findByPublicId(usuarioPublicId)).thenReturn(Optional.of(usuario));
        when(patientRepository.findByUserId(10L)).thenReturn(Optional.of(paciente));

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PACIENTE"));

        assertThatThrownBy(() -> clinicalAccessService.validateClinicalHistoryAccess(usuarioPublicId, pacienteAjenoPublicId, authorities))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("No tiene autorizacion para acceder a la informacion clinica de otro paciente");
    }

    @Test
    @DisplayName("Administrador: NUNCA puede acceder a historial clínico (ADR-007, 403 Forbidden)")
    void administrator_nuncaCanAccederToHistoryClinical() {
        String adminPublicId = "u-admin-1";
        String pacientePublicId = "pac-001";
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));

        assertThatThrownBy(() -> clinicalAccessService.validateClinicalHistoryAccess(adminPublicId, pacientePublicId, authorities))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("El personal administrativo no tiene autorizacion para acceder a contenido clinico");

        // El admin es bloqueado sin consultar repositorios clínicos
        verify(appointmentRepository, never()).existsActiveFutureAppointment(any(), any(), any());
        verify(attentionRepository, never()).existsPreviousAttentionInWindow(any(), any(), any());
    }

    @Test
    @DisplayName("Profesional con cita activa futura: SÍ puede acceder al historial clínico")
    void professionalWithAppointmentFuturaActive_canAcceder() {
        String usuarioMedPublicId = "u-med-1";
        String profesionalPublicId = "pro-001";
        String pacientePublicId = "pac-001";

        User usuarioMed = crearUsuario(20L, usuarioMedPublicId, "med@test.com");
        Professional profesional = new Professional(200L, 20L, profesionalPublicId, 5L, "RM-12345", "Carlos", "Gomez", AHORA, AHORA);
        Patient paciente = crearPaciente(100L, 10L, pacientePublicId);

        when(userRepository.findByPublicId(usuarioMedPublicId)).thenReturn(Optional.of(usuarioMed));
        when(professionalRepository.findByUserId(20L)).thenReturn(Optional.of(profesional));
        when(patientRepository.findByPublicId(pacientePublicId)).thenReturn(Optional.of(paciente));

        // Cita activa futura confirmada
        when(appointmentRepository.existsActiveFutureAppointment(200L, 100L, AHORA)).thenReturn(true);

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"));

        assertThatCode(() -> clinicalAccessService.validateClinicalHistoryAccess(usuarioMedPublicId, pacientePublicId, authorities))
                .doesNotThrowAnyException();

        // Al haber cita futura, no es necesario consultar atención previa
        verify(attentionRepository, never()).existsPreviousAttentionInWindow(any(), any(), any());
    }

    @Test
    @DisplayName("Profesional con atención previa propia dentro de 12 meses: SÍ puede acceder")
    void professionalWithAttentionPreviousInWindow_canAcceder() {
        String usuarioMedPublicId = "u-med-1";
        String profesionalPublicId = "pro-001";
        String pacientePublicId = "pac-001";

        User usuarioMed = crearUsuario(20L, usuarioMedPublicId, "med@test.com");
        Professional profesional = new Professional(200L, 20L, profesionalPublicId, 5L, "RM-12345", "Carlos", "Gomez", AHORA, AHORA);
        Patient paciente = crearPaciente(100L, 10L, pacientePublicId);

        when(userRepository.findByPublicId(usuarioMedPublicId)).thenReturn(Optional.of(usuarioMed));
        when(professionalRepository.findByUserId(20L)).thenReturn(Optional.of(profesional));
        when(patientRepository.findByPublicId(pacientePublicId)).thenReturn(Optional.of(paciente));

        // Sin cita futura
        when(appointmentRepository.existsActiveFutureAppointment(200L, 100L, AHORA)).thenReturn(false);

        // Pero con atención médica previa en los últimos 12 meses
        Instant fechaLimite12Meses = ZonedDateTime.ofInstant(AHORA, BOGOTA_ZONE).minusMonths(12).toInstant();
        when(attentionRepository.existsPreviousAttentionInWindow(200L, 100L, fechaLimite12Meses)).thenReturn(true);

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"));

        assertThatCode(() -> clinicalAccessService.validateClinicalHistoryAccess(usuarioMedPublicId, pacientePublicId, authorities))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Profesional sin relación asistencial: NO puede acceder (403 Forbidden)")
    void professionalWithoutRelationshipCare_notCanAcceder() {
        String usuarioMedPublicId = "u-med-1";
        String profesionalPublicId = "pro-001";
        String pacientePublicId = "pac-001";

        User usuarioMed = crearUsuario(20L, usuarioMedPublicId, "med@test.com");
        Professional profesional = new Professional(200L, 20L, profesionalPublicId, 5L, "RM-12345", "Carlos", "Gomez", AHORA, AHORA);
        Patient paciente = crearPaciente(100L, 10L, pacientePublicId);

        when(userRepository.findByPublicId(usuarioMedPublicId)).thenReturn(Optional.of(usuarioMed));
        when(professionalRepository.findByUserId(20L)).thenReturn(Optional.of(profesional));
        when(patientRepository.findByPublicId(pacientePublicId)).thenReturn(Optional.of(paciente));

        // Sin cita futura y sin atención previa en ventana
        when(appointmentRepository.existsActiveFutureAppointment(200L, 100L, AHORA)).thenReturn(false);
        Instant fechaLimite = ZonedDateTime.ofInstant(AHORA, BOGOTA_ZONE).minusMonths(12).toInstant();
        when(attentionRepository.existsPreviousAttentionInWindow(200L, 100L, fechaLimite)).thenReturn(false);

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"));

        assertThatThrownBy(() -> clinicalAccessService.validateClinicalHistoryAccess(usuarioMedPublicId, pacientePublicId, authorities))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("No existe una relacion asistencial activa con el paciente");
    }

    @Test
    @DisplayName("Validación por ID numérico de paciente resuelve entidad y delega")
    void validateAccessByIdNumerico_findsPatientAndValid() {
        String usuarioPublicId = "u-paciente-1";
        Long pacienteId = 100L;
        String pacientePublicId = "pac-001";

        Patient paciente = crearPaciente(pacienteId, 10L, pacientePublicId);
        User usuario = crearUsuario(10L, usuarioPublicId, "paciente@test.com");

        when(patientRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        when(userRepository.findByPublicId(usuarioPublicId)).thenReturn(Optional.of(usuario));
        when(patientRepository.findByUserId(10L)).thenReturn(Optional.of(paciente));

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PACIENTE"));

        assertThatCode(() -> clinicalAccessService.validateClinicalHistoryAccess(usuarioPublicId, pacienteId, authorities))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Usuario sin roles o no autenticado: es rechazado categóricamente")
    void userWithoutRoles_isRejected() {
        assertThatThrownBy(() -> clinicalAccessService.validateClinicalHistoryAccess("u-1", "pac-1", List.of()))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Usuario no autenticado o sin roles");

        assertThatThrownBy(() -> clinicalAccessService.validateClinicalHistoryAccess(null, "pac-1", List.of(new SimpleGrantedAuthority("ROLE_PACIENTE"))))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    @DisplayName("Paciente objetivo inexistente: lanza RecursoNoEncontradoException")
    void patientTargetNotExists_throwsResourceNotFound() {
        String usuarioMedPublicId = "u-med-1";
        String pacienteInexistentePublicId = "pac-no-existe";

        User usuarioMed = crearUsuario(20L, usuarioMedPublicId, "med@test.com");
        Professional profesional = new Professional(200L, 20L, "pro-001", 5L, "RM-12345", "Carlos", "Gomez", AHORA, AHORA);

        when(userRepository.findByPublicId(usuarioMedPublicId)).thenReturn(Optional.of(usuarioMed));
        when(professionalRepository.findByUserId(20L)).thenReturn(Optional.of(profesional));
        when(patientRepository.findByPublicId(pacienteInexistentePublicId)).thenReturn(Optional.empty());

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"));

        assertThatThrownBy(() -> clinicalAccessService.validateClinicalHistoryAccess(usuarioMedPublicId, pacienteInexistentePublicId, authorities))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Paciente no encontrado");
    }

    @Test
    @DisplayName("Sobrecarga tieneRelacionAsistencial con UUIDs busca entidades y retorna resultado")
    void hasCareRelationship_withUUIDs() {
        String proPublicId = "pro-001";
        String pacPublicId = "pac-001";

        Professional profesional = new Professional(200L, 20L, proPublicId, 5L, "RM-12345", "Carlos", "Gomez", AHORA, AHORA);
        Patient paciente = crearPaciente(100L, 10L, pacPublicId);

        when(professionalRepository.findByPublicId(proPublicId)).thenReturn(Optional.of(profesional));
        when(patientRepository.findByPublicId(pacPublicId)).thenReturn(Optional.of(paciente));
        when(appointmentRepository.existsActiveFutureAppointment(200L, 100L, AHORA)).thenReturn(true);

        boolean tieneRelacion = clinicalAccessService.hasCareRelationship(proPublicId, pacPublicId);

        assertThat(tieneRelacion).isTrue();
    }

    @Test
    @DisplayName("Profesional sin cita ni atención previa pero con Break-Glass activo: acceso permitido (ADR-017)")
    void professional_withoutOrdinaryRelationship_withBreakGlassActive_accessAllowed() {
        String usuarioMedPublicId = "u-med-1";
        String pacientePublicId = "pac-001";

        User usuarioMed = crearUsuario(20L, usuarioMedPublicId, "med@test.com");
        Professional profesional = new Professional(200L, 20L, "pro-001", 5L, "RM-12345", "Carlos", "Gomez", AHORA, AHORA);
        Patient paciente = crearPaciente(100L, 10L, pacientePublicId);

        when(userRepository.findByPublicId(usuarioMedPublicId)).thenReturn(Optional.of(usuarioMed));
        when(professionalRepository.findByUserId(20L)).thenReturn(Optional.of(profesional));
        when(patientRepository.findByPublicId(pacientePublicId)).thenReturn(Optional.of(paciente));

        // No hay cita activa futura
        when(appointmentRepository.existsActiveFutureAppointment(eq(200L), eq(100L), any(Instant.class))).thenReturn(false);
        // No hay atención previa en ventana de 12 meses
        when(attentionRepository.existsPreviousAttentionInWindow(eq(200L), eq(100L), any(Instant.class))).thenReturn(false);
        // SÍ hay acceso Break-Glass activo
        when(breakGlassRepository.existsActiveAccess(eq(200L), eq(100L), any(Instant.class))).thenReturn(true);

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"));

        assertThatCode(() -> clinicalAccessService.validateClinicalHistoryAccess(usuarioMedPublicId, pacientePublicId, authorities))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Profesional sin relación ordinaria y sin Break-Glass activo: acceso rechazado 403 (ADR-007, ADR-017)")
    void professional_withoutOrdinaryRelationship_withoutBreakGlass_accessRechazado() {
        String usuarioMedPublicId = "u-med-1";
        String pacientePublicId = "pac-001";

        User usuarioMed = crearUsuario(20L, usuarioMedPublicId, "med@test.com");
        Professional profesional = new Professional(200L, 20L, "pro-001", 5L, "RM-12345", "Carlos", "Gomez", AHORA, AHORA);
        Patient paciente = crearPaciente(100L, 10L, pacientePublicId);

        when(userRepository.findByPublicId(usuarioMedPublicId)).thenReturn(Optional.of(usuarioMed));
        when(professionalRepository.findByUserId(20L)).thenReturn(Optional.of(profesional));
        when(patientRepository.findByPublicId(pacientePublicId)).thenReturn(Optional.of(paciente));

        when(appointmentRepository.existsActiveFutureAppointment(eq(200L), eq(100L), any(Instant.class))).thenReturn(false);
        when(attentionRepository.existsPreviousAttentionInWindow(eq(200L), eq(100L), any(Instant.class))).thenReturn(false);
        when(breakGlassRepository.existsActiveAccess(eq(200L), eq(100L), any(Instant.class))).thenReturn(false);

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"));

        assertThatThrownBy(() -> clinicalAccessService.validateClinicalHistoryAccess(usuarioMedPublicId, pacientePublicId, authorities))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("No existe una relacion asistencial activa con el paciente");
    }
}
