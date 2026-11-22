package com.meditriaje.service;

import com.meditriaje.dto.clinical.BreakGlassAccessResponse;
import com.meditriaje.dto.clinical.ActivateBreakGlassRequest;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.BreakGlassAccess;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.Patient;
import com.meditriaje.model.Professional;
import com.meditriaje.model.User;
import com.meditriaje.repository.BreakGlassRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.ProfessionalRepository;
import com.meditriaje.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BreakGlassServiceTest {

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private static final Instant AHORA = Instant.parse("2026-10-04T12:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfessionalRepository professionalRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private BreakGlassRepository breakGlassRepository;

    @Mock
    private AuditService auditService;

    private Clock clock;
    private BreakGlassService breakGlassService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(AHORA, BOGOTA_ZONE);
        breakGlassService = new BreakGlassService(
                userRepository,
                professionalRepository,
                patientRepository,
                breakGlassRepository,
                auditService,
                clock
        );
    }

    private User crearUsuario(Long id, String publicId, String email) {
        return new User(id, publicId, email, "hash", "ACTIVO", 0, null, AHORA, false);
    }

    private Professional crearProfesional(Long id, Long usuarioId, String publicId) {
        return new Professional(id, usuarioId, publicId, 1L, "RM-998877", "Roberto", "Gomez", AHORA, AHORA);
    }

    private Patient crearPaciente(Long id, Long usuarioId, String publicId) {
        return new Patient(id, usuarioId, publicId, "CC", "10203040", "Carlos", "Sanchez",
                LocalDate.of(1985, 5, 20), "3001234567", AHORA, AHORA);
    }

    @Test
    @DisplayName("activarBreakGlass: registro exitoso con vigencia de 24 horas y auditoría ACCESO_BREAK_GLASS")
    void activarBreakGlass_exitoso() {
        String usuarioMedPubId = "usr-med-1";
        String pacPubId = "pac-100";
        String motivo = "Paciente en shock hipovolemico por hemorragia aguda; requiere acceso urgente al historial.";
        ActivateBreakGlassRequest request = new ActivateBreakGlassRequest(pacPubId, motivo);

        User usuario = crearUsuario(10L, usuarioMedPubId, "medico@test.com");
        Professional profesional = crearProfesional(100L, 10L, "prof-100");
        Patient paciente = crearPaciente(200L, 20L, pacPubId);

        when(userRepository.findByPublicId(usuarioMedPubId)).thenReturn(Optional.of(usuario));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.of(profesional));
        when(patientRepository.findByPublicId(pacPubId)).thenReturn(Optional.of(paciente));
        when(breakGlassRepository.recordAccess(any(BreakGlassAccess.class))).thenReturn(1L);

        Instant expiracionEsperada = AHORA.plus(24, ChronoUnit.HOURS);
        BreakGlassAccessResponse respuestaMock = new BreakGlassAccessResponse(
                "bg-created-uuid",
                "prof-100",
                "Roberto Gomez",
                pacPubId,
                "Carlos Sanchez",
                motivo,
                expiracionEsperada,
                AHORA,
                true
        );
        when(breakGlassRepository.findByPublicId(any(String.class), eq(AHORA))).thenReturn(Optional.of(respuestaMock));

        BreakGlassAccessResponse response = breakGlassService.activateBreakGlass(request, usuarioMedPubId, "192.168.1.100");

        assertThat(response).isNotNull();
        assertThat(response.publicId()).isEqualTo("bg-created-uuid");
        assertThat(response.active()).isTrue();
        assertThat(response.expirationDate()).isEqualTo(expiracionEsperada);

        // Verificar que el acceso guardado tenga los IDs y expiración exacta
        ArgumentCaptor<BreakGlassAccess> accesoCaptor = ArgumentCaptor.forClass(BreakGlassAccess.class);
        verify(breakGlassRepository).recordAccess(accesoCaptor.capture());
        BreakGlassAccess guardado = accesoCaptor.getValue();
        assertThat(guardado.professionalId()).isEqualTo(100L);
        assertThat(guardado.patientId()).isEqualTo(200L);
        assertThat(guardado.expirationDate()).isEqualTo(expiracionEsperada);
        assertThat(guardado.reason()).isEqualTo(motivo);

        // Verificar auditoría reforzada inmutable
        ArgumentCaptor<AuditEvent> audCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).audit(audCaptor.capture());
        AuditEvent aud = audCaptor.getValue();
        assertThat(aud.userId()).isEqualTo(10L);
        assertThat(aud.action()).isEqualTo(AuditableAction.ACCESO_BREAK_GLASS);
        assertThat(aud.resourceType()).isEqualTo("PACIENTE");
        assertThat(aud.resourcePublicId()).isEqualTo(pacPubId);
        assertThat(aud.sourceIp()).isEqualTo("192.168.1.100");
    }

    @Test
    @DisplayName("activarBreakGlass: motivo con menos de 20 caracteres lanza DatosInvalidosException")
    void activateBreakGlass_shortReason_throwsInvalidData() {
        String usuarioMedPubId = "usr-med-1";
        String pacPubId = "pac-100";
        ActivateBreakGlassRequest request = new ActivateBreakGlassRequest(pacPubId, "Urgente");

        User usuario = crearUsuario(10L, usuarioMedPubId, "medico@test.com");
        Professional profesional = crearProfesional(100L, 10L, "prof-100");
        Patient paciente = crearPaciente(200L, 20L, pacPubId);

        when(userRepository.findByPublicId(usuarioMedPubId)).thenReturn(Optional.of(usuario));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.of(profesional));
        when(patientRepository.findByPublicId(pacPubId)).thenReturn(Optional.of(paciente));

        assertThatThrownBy(() -> breakGlassService.activateBreakGlass(request, usuarioMedPubId, "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("al menos 20 caracteres");
    }

    @Test
    @DisplayName("activarBreakGlass: usuario no registrado como profesional lanza AccesoNoAutorizadoException")
    void activateBreakGlass_userNotProfessional_throwsUnauthorizedAccess() {
        String usuarioAdminPubId = "usr-admin-1";
        ActivateBreakGlassRequest request = new ActivateBreakGlassRequest("pac-100", "Justificación de emergencia con más de veinte caracteres.");

        User usuario = crearUsuario(5L, usuarioAdminPubId, "admin@test.com");
        when(userRepository.findByPublicId(usuarioAdminPubId)).thenReturn(Optional.of(usuario));
        when(professionalRepository.findByUserId(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> breakGlassService.activateBreakGlass(request, usuarioAdminPubId, "127.0.0.1"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Usuario no registrado como profesional");
    }

    @Test
    @DisplayName("activarBreakGlass: paciente inexistente lanza RecursoNoEncontradoException")
    void activateBreakGlass_patientInexistente_throwsResourceNotFound() {
        String usuarioMedPubId = "usr-med-1";
        ActivateBreakGlassRequest request = new ActivateBreakGlassRequest("pac-fantasma", "Justificación de emergencia con más de veinte caracteres.");

        User usuario = crearUsuario(10L, usuarioMedPubId, "medico@test.com");
        Professional profesional = crearProfesional(100L, 10L, "prof-100");

        when(userRepository.findByPublicId(usuarioMedPubId)).thenReturn(Optional.of(usuario));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.of(profesional));
        when(patientRepository.findByPublicId("pac-fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> breakGlassService.activateBreakGlass(request, usuarioMedPubId, "127.0.0.1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Paciente no encontrado");
    }

    @Test
    @DisplayName("listarMisAccesosActivos: retorna autorizaciones vigentes del profesional")
    void listMyAccessesActive_success() {
        String usuarioMedPubId = "usr-med-1";
        User usuario = crearUsuario(10L, usuarioMedPubId, "medico@test.com");
        Professional profesional = crearProfesional(100L, 10L, "prof-100");

        when(userRepository.findByPublicId(usuarioMedPubId)).thenReturn(Optional.of(usuario));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.of(profesional));

        BreakGlassAccessResponse acceso = new BreakGlassAccessResponse(
                "bg-1",
                "prof-100",
                "Roberto Gomez",
                "pac-1",
                "Carlos Sanchez",
                "Motivo de urgencia médica con suficientes caracteres",
                AHORA.plus(10, ChronoUnit.HOURS),
                AHORA,
                true
        );
        when(breakGlassRepository.listActiveByProfessional(100L, AHORA)).thenReturn(List.of(acceso));

        List<BreakGlassAccessResponse> lista = breakGlassService.listMyActiveAccesses(usuarioMedPubId);

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).publicId()).isEqualTo("bg-1");
    }

    @Test
    @DisplayName("obtenerPorPublicId: si pertenece a otro profesional lanza AccesoNoAutorizadoException")
    void getByPublicId_otherProfessional_throwsUnauthorizedAccess() {
        String usuarioMedPubId = "usr-med-1";
        User usuario = crearUsuario(10L, usuarioMedPubId, "medico@test.com");
        Professional profesional = crearProfesional(100L, 10L, "prof-100");

        when(userRepository.findByPublicId(usuarioMedPubId)).thenReturn(Optional.of(usuario));
        when(professionalRepository.findByUserId(10L)).thenReturn(Optional.of(profesional));

        BreakGlassAccessResponse accesoDeOtro = new BreakGlassAccessResponse(
                "bg-99",
                "prof-OTRO",
                "Dra. Maria Perez",
                "pac-1",
                "Carlos Sanchez",
                "Motivo de urgencia médica con suficientes caracteres",
                AHORA.plus(10, ChronoUnit.HOURS),
                AHORA,
                true
        );
        when(breakGlassRepository.findByPublicId("bg-99", AHORA)).thenReturn(Optional.of(accesoDeOtro));

        assertThatThrownBy(() -> breakGlassService.getByPublicId("bg-99", usuarioMedPubId))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("No tiene autorizacion para consultar el acceso break-glass de otro profesional");
    }
}
