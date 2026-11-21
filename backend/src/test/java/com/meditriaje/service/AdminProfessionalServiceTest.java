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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminProfessionalServiceTest {

    @Mock
    private ProfessionalRepository professionalRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SpecialtyRepository specialtyRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    private AdminProfessionalService service;

    private static final String ADMIN_PUBLIC_ID = "admin-uuid-1";
    private static final Long ADMIN_ID = 99L;
    private static final String IP_ORIGEN = "127.0.0.1";

    @BeforeEach
    void setUp() {
        service = new AdminProfessionalService(
                professionalRepository,
                userRepository,
                specialtyRepository,
                auditService,
                passwordEncoder,
                emailService
        );
    }

    private void mockAdmin() {
        User admin = new User(ADMIN_ID, ADMIN_PUBLIC_ID, "admin@meditriaje.com", "hash", "ACTIVO", 0, null, Instant.now(), false);
        when(userRepository.findByPublicId(ADMIN_PUBLIC_ID)).thenReturn(Optional.of(admin));
    }

    // =========================================================================
    // 1. ALTA DE PROFESIONAL
    // =========================================================================

    @Test
    void registerProfessional_withDataValid_createsUserAndProfessionalWithPasswordTemporaryAndAudits() {
        mockAdmin();
        CreateProfessionalRequest req = new CreateProfessionalRequest(
                "RM-12345",
                "Carlos",
                "Perez",
                "carlos.perez@hospital.com",
                "esp-med-int"
        );

        Specialty esp = new Specialty(10L, "esp-med-int", "Medicina Interna", 20, "ACTIVO");
        when(specialtyRepository.findByPublicId("esp-med-int")).thenReturn(Optional.of(esp));
        when(professionalRepository.existsByDocument(anyString(), anyString())).thenReturn(false);
        when(userRepository.existsByEmail("carlos.perez@hospital.com")).thenReturn(false);
        when(professionalRepository.existsByMedicalLicense("RM-12345")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("argon2id_mock_hash");
        when(userRepository.create(anyString(), eq("carlos.perez@hospital.com"), eq("argon2id_mock_hash"), eq(true))).thenReturn(101L);
        when(userRepository.findRoleIdByName("ROLE_PROFESIONAL")).thenReturn(Optional.of(2L));
        when(professionalRepository.create(any())).thenReturn(201L);

        CreateProfessionalResponse resp = service.registerProfessional(req, ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp).isNotNull();
        assertThat(resp.publicId()).isNotBlank();
        assertThat(resp.userPublicId()).isNotBlank();
        assertThat(resp.medicalLicense()).isEqualTo("RM-12345");
        assertThat(resp.firstNames()).isEqualTo("Carlos");
        assertThat(resp.lastNames()).isEqualTo("Perez");
        assertThat(resp.email()).isEqualTo("carlos.perez@hospital.com");
        assertThat(resp.specialtyPublicId()).isEqualTo("esp-med-int");
        assertThat(resp.specialtyName()).isEqualTo("Medicina Interna");
        assertThat(resp.mustChangePassword()).isTrue();

        // Validar que la contraseña temporal sea segura
        assertThat(resp.temporaryPassword()).isNotBlank();
        assertThat(resp.temporaryPassword().length()).isGreaterThanOrEqualTo(12);
        assertThat(resp.temporaryPassword()).matches(".*[A-Z].*");
        assertThat(resp.temporaryPassword()).matches(".*[a-z].*");
        assertThat(resp.temporaryPassword()).matches(".*[0-9].*");
        assertThat(resp.temporaryPassword()).matches(".*[!@#$%&*+\\-_=].*");

        verify(userRepository).assignRole(101L, 2L);
        verify(auditService).recordEvent(
                eq(ADMIN_ID),
                eq(AuditableAction.CAMBIO_ADMINISTRATIVO),
                eq("PROFESIONAL"),
                eq(resp.publicId()),
                eq(AuditResult.EXITO),
                eq(IP_ORIGEN)
        );
        verify(emailService).sendInitialCredentials(
                eq("carlos.perez@hospital.com"),
                eq("Carlos Perez"),
                eq("Profesional Asistencial (Medicina Interna)"),
                eq(resp.temporaryPassword())
        );
    }

    @Test
    void registerProfessional_withNamesInvalid_throwsInvalidDataException() {
        CreateProfessionalRequest req = new CreateProfessionalRequest(
                "RM-12345",
                "Carlos123",
                "Perez",
                "carlos.perez@hospital.com",
                "esp-med-int"
        );

        assertThatThrownBy(() -> service.registerProfessional(req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("solo puede contener letras, espacios, tildes y guiones");
    }

    @Test
    void registerProfessional_specialtyInexistente_throwsResourceNotFoundException() {
        CreateProfessionalRequest req = new CreateProfessionalRequest(
                "RM-12345",
                "Carlos",
                "Perez",
                "carlos.perez@hospital.com",
                "esp-no-existe"
        );
        when(specialtyRepository.findByPublicId("esp-no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerProfessional(req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Especialidad no encontrada");

        verify(userRepository, never()).create(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void registerProfessional_specialtyInactive_throwsInvalidDataException() {
        CreateProfessionalRequest req = new CreateProfessionalRequest(
                "RM-12345",
                "Carlos",
                "Perez",
                "carlos.perez@hospital.com",
                "esp-inactiva"
        );
        Specialty espInactiva = new Specialty(10L, "esp-inactiva", "Inactiva", 20, "INACTIVO");
        when(specialtyRepository.findByPublicId("esp-inactiva")).thenReturn(Optional.of(espInactiva));

        assertThatThrownBy(() -> service.registerProfessional(req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("no se encuentra activa");

        verify(userRepository, never()).create(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void registerProfessional_emailDuplicate_throwsInvalidDataException() {
        CreateProfessionalRequest req = new CreateProfessionalRequest(
                "RM-12345",
                "Carlos",
                "Perez",
                "carlos.perez@hospital.com",
                "esp-activa"
        );
        Specialty esp = new Specialty(10L, "esp-activa", "Medicina", 20, "ACTIVO");
        when(specialtyRepository.findByPublicId("esp-activa")).thenReturn(Optional.of(esp));
        when(userRepository.existsByEmail("carlos.perez@hospital.com")).thenReturn(true);

        assertThatThrownBy(() -> service.registerProfessional(req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("El correo electronico ya se encuentra registrado");

        verify(userRepository, never()).create(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void registerProfessional_typeDocumentInvalid_throwsInvalidDataException() {
        CreateProfessionalRequest req = new CreateProfessionalRequest(
                "TI",
                "1020304050",
                "RM-12345",
                "Carlos",
                "Perez",
                "carlos.perez@hospital.com",
                "3001234567",
                "esp-activa"
        );

        assertThatThrownBy(() -> service.registerProfessional(req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Tipo de documento no valido para profesionales en Colombia. Permitidos: CC, CE.");
    }

    @Test
    void registerProfessional_numberDocumentInvalid_throwsInvalidDataException() {
        CreateProfessionalRequest req = new CreateProfessionalRequest(
                "CC",
                "123", // Menos de 6 dígitos
                "RM-12345",
                "Carlos",
                "Perez",
                "carlos.perez@hospital.com",
                "3001234567",
                "esp-activa"
        );

        assertThatThrownBy(() -> service.registerProfessional(req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("La Cedula de Ciudadania (CC) debe contener entre 6 y 10 digitos numericos.");
    }

    @Test
    void registerProfessional_documentDuplicate_throwsInvalidDataException() {
        CreateProfessionalRequest req = new CreateProfessionalRequest(
                "CC",
                "1020304050",
                "RM-12345",
                "Carlos",
                "Perez",
                "carlos.perez@hospital.com",
                "3001234567",
                "esp-activa"
        );
        when(professionalRepository.existsByDocument("CC", "1020304050")).thenReturn(true);

        assertThatThrownBy(() -> service.registerProfessional(req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Ya existe un profesional registrado con el documento ingresado.");
    }

    @Test
    void registerProfessional_phoneInvalid_throwsInvalidDataException() {
        CreateProfessionalRequest req = new CreateProfessionalRequest(
                "CC",
                "1020304050",
                "RM-12345",
                "Carlos",
                "Perez",
                "carlos.perez@hospital.com",
                "1234567", // No es celular de 10 dígitos iniciando en 3
                "esp-activa"
        );
        when(professionalRepository.existsByDocument("CC", "1020304050")).thenReturn(false);

        assertThatThrownBy(() -> service.registerProfessional(req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("El telefono debe ser un celular colombiano valido de 10 digitos");
    }

    @Test
    void registerProfessional_recordPhysicianShort_throwsInvalidDataException() {
        CreateProfessionalRequest req = new CreateProfessionalRequest(
                "CC",
                "1020304050",
                "RM", // < 4 caracteres
                "Carlos",
                "Perez",
                "carlos.perez@hospital.com",
                "3001234567",
                "esp-activa"
        );
        Specialty esp = new Specialty(10L, "esp-activa", "Medicina", 20, "ACTIVO");
        when(professionalRepository.existsByDocument("CC", "1020304050")).thenReturn(false);
        when(specialtyRepository.findByPublicId("esp-activa")).thenReturn(Optional.of(esp));
        when(userRepository.existsByEmail("carlos.perez@hospital.com")).thenReturn(false);

        assertThatThrownBy(() -> service.registerProfessional(req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("El registro medico / ReTHUS debe tener al menos 4 caracteres.");
    }

    @Test
    void registerProfessional_duplicateMedicalLicense_throwsInvalidDataException() {
        CreateProfessionalRequest req = new CreateProfessionalRequest(
                "RM-12345",
                "Carlos",
                "Perez",
                "carlos.perez@hospital.com",
                "esp-activa"
        );
        Specialty esp = new Specialty(10L, "esp-activa", "Medicina", 20, "ACTIVO");
        when(professionalRepository.existsByDocument(anyString(), anyString())).thenReturn(false);
        when(specialtyRepository.findByPublicId("esp-activa")).thenReturn(Optional.of(esp));
        when(userRepository.existsByEmail("carlos.perez@hospital.com")).thenReturn(false);
        when(professionalRepository.existsByMedicalLicense("RM-12345")).thenReturn(true);

        assertThatThrownBy(() -> service.registerProfessional(req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("El registro medico ya se encuentra registrado");

        verify(userRepository, never()).create(anyString(), anyString(), anyString(), anyBoolean());
    }

    // =========================================================================
    // 2. ACTUALIZACIÓN DE PROFESIONAL
    // =========================================================================

    @Test
    void updateProfessional_withDataValid_updatesAndAudits() {
        mockAdmin();
        String profPublicId = "prof-uuid-1";
        UpdateProfessionalRequest req = new UpdateProfessionalRequest("Carlos Alberto", "Perez Gomez", "esp-ped");

        Professional existente = new Professional(50L, 101L, profPublicId, 10L, "RM-12345", "Carlos", "Perez", Instant.now(), null);
        Specialty nuevaEsp = new Specialty(20L, "esp-ped", "Pediatría", 30, "ACTIVO");
        User usuario = new User(101L, "user-uuid-1", "carlos@hospital.com", "hash", "ACTIVO", 0, null, Instant.now(), false);

        when(professionalRepository.findByPublicId(profPublicId)).thenReturn(Optional.of(existente));
        when(specialtyRepository.findByPublicId("esp-ped")).thenReturn(Optional.of(nuevaEsp));
        when(userRepository.findById(101L)).thenReturn(Optional.of(usuario));

        ProfessionalResponse resp = service.updateProfessional(profPublicId, req, ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.firstNames()).isEqualTo("Carlos Alberto");
        assertThat(resp.lastNames()).isEqualTo("Perez Gomez");
        assertThat(resp.specialtyPublicId()).isEqualTo("esp-ped");
        assertThat(resp.specialtyName()).isEqualTo("Pediatría");

        ArgumentCaptor<Professional> captor = ArgumentCaptor.forClass(Professional.class);
        verify(professionalRepository).update(captor.capture());
        assertThat(captor.getValue().firstNames()).isEqualTo("Carlos Alberto");
        assertThat(captor.getValue().lastNames()).isEqualTo("Perez Gomez");
        assertThat(captor.getValue().specialtyId()).isEqualTo(20L);

        verify(auditService).recordEvent(
                eq(ADMIN_ID),
                eq(AuditableAction.CAMBIO_ADMINISTRATIVO),
                eq("PROFESIONAL"),
                eq(profPublicId),
                eq(AuditResult.EXITO),
                eq(IP_ORIGEN)
        );
    }

    @Test
    void updateProfessional_specialtyInactive_throwsInvalidDataException() {
        String profPublicId = "prof-uuid-1";
        UpdateProfessionalRequest req = new UpdateProfessionalRequest("Carlos", "Perez", "esp-inactiva");
        Professional existente = new Professional(50L, 101L, profPublicId, 10L, "RM-12345", "Carlos", "Perez", Instant.now(), null);
        Specialty espInactiva = new Specialty(20L, "esp-inactiva", "Inactiva", 30, "INACTIVO");

        when(professionalRepository.findByPublicId(profPublicId)).thenReturn(Optional.of(existente));
        when(specialtyRepository.findByPublicId("esp-inactiva")).thenReturn(Optional.of(espInactiva));

        assertThatThrownBy(() -> service.updateProfessional(profPublicId, req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("no se encuentra activa");
    }

    // =========================================================================
    // 3. CAMBIAR ESTADO (ACTIVAR / DESACTIVAR)
    // =========================================================================

    @Test
    void changeStatus_toInactive_updatesUserAndAudits() {
        mockAdmin();
        String profPublicId = "prof-uuid-1";
        Professional existente = new Professional(50L, 101L, profPublicId, 10L, "RM-12345", "Carlos", "Perez", Instant.now(), null);
        User usuario = new User(101L, "user-uuid-1", "carlos@hospital.com", "hash", "ACTIVO", 0, null, Instant.now(), false);
        Specialty esp = new Specialty(10L, "esp-med", "Medicina", 20, "ACTIVO");

        when(professionalRepository.findByPublicId(profPublicId)).thenReturn(Optional.of(existente));
        when(userRepository.findById(101L)).thenReturn(Optional.of(usuario));
        when(specialtyRepository.findById(10L)).thenReturn(Optional.of(esp));

        ProfessionalResponse resp = service.changeStatus(profPublicId, "INACTIVO", ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.status()).isEqualTo("INACTIVO");
        verify(userRepository).updateStatus(101L, "INACTIVO");
        verify(auditService).recordEvent(
                eq(ADMIN_ID),
                eq(AuditableAction.CAMBIO_ADMINISTRATIVO),
                eq("PROFESIONAL"),
                eq(profPublicId),
                eq(AuditResult.EXITO),
                eq(IP_ORIGEN)
        );
    }

    @Test
    void changeStatus_statusInvalid_throwsInvalidDataException() {
        assertThatThrownBy(() -> service.changeStatus("prof-uuid-1", "ESTADO_RARO", ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Estado invalido");
    }

    // =========================================================================
    // 4. OBTENER Y LISTAR
    // =========================================================================

    @Test
    void getByPublicId_existing_returnsProfessionalResponse() {
        String profPublicId = "prof-uuid-1";
        Professional existente = new Professional(50L, 101L, profPublicId, 10L, "RM-12345", "Carlos", "Perez", Instant.now(), null);
        User usuario = new User(101L, "user-uuid-1", "carlos@hospital.com", "hash", "ACTIVO", 0, null, Instant.now(), true);
        Specialty esp = new Specialty(10L, "esp-med", "Medicina", 20, "ACTIVO");

        when(professionalRepository.findByPublicId(profPublicId)).thenReturn(Optional.of(existente));
        when(userRepository.findById(101L)).thenReturn(Optional.of(usuario));
        when(specialtyRepository.findById(10L)).thenReturn(Optional.of(esp));

        ProfessionalResponse resp = service.getByPublicId(profPublicId);

        assertThat(resp.publicId()).isEqualTo(profPublicId);
        assertThat(resp.userPublicId()).isEqualTo("user-uuid-1");
        assertThat(resp.medicalLicense()).isEqualTo("RM-12345");
        assertThat(resp.email()).isEqualTo("carlos@hospital.com");
        assertThat(resp.mustChangePassword()).isTrue();
    }

    @Test
    void list_withFilters_returnsPageCorrect() {
        ProfessionalResponse item = new ProfessionalResponse(
                "prof-1", "user-1", "RM-1", "Juan", "Gomez", "juan@test.com", "esp-1", "Pediatría", "ACTIVO", false, Instant.now()
        );
        when(professionalRepository.list(0, 10, "esp-1", "ACTIVO")).thenReturn(List.of(item));
        when(professionalRepository.count("esp-1", "ACTIVO")).thenReturn(1);

        PaginatedResponse<ProfessionalResponse> resp = service.list(0, 10, "esp-1", "ACTIVO");

        assertThat(resp.content()).hasSize(1);
        assertThat(resp.totalElements()).isEqualTo(1L);
        assertThat(resp.content().get(0).medicalLicense()).isEqualTo("RM-1");
    }

    @Test
    void updateProfessional_professionalInexistente_throwsResourceNotFoundException() {
        UpdateProfessionalRequest req = new UpdateProfessionalRequest("Carlos", "Perez", "esp-1");
        when(professionalRepository.findByPublicId("prof-no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProfessional("prof-no-existe", req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Profesional no encontrado");
    }

    @Test
    void changeStatus_toActive_updatesUserAndAudits() {
        mockAdmin();
        String profPublicId = "prof-uuid-1";
        Professional existente = new Professional(50L, 101L, profPublicId, 10L, "RM-12345", "Carlos", "Perez", Instant.now(), null);
        User usuario = new User(101L, "user-uuid-1", "carlos@hospital.com", "hash", "INACTIVO", 0, null, Instant.now(), false);
        Specialty esp = new Specialty(10L, "esp-med", "Medicina", 20, "ACTIVO");

        when(professionalRepository.findByPublicId(profPublicId)).thenReturn(Optional.of(existente));
        when(userRepository.findById(101L)).thenReturn(Optional.of(usuario));
        when(specialtyRepository.findById(10L)).thenReturn(Optional.of(esp));

        ProfessionalResponse resp = service.changeStatus(profPublicId, "ACTIVO", ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.status()).isEqualTo("ACTIVO");
        verify(userRepository).updateStatus(101L, "ACTIVO");
        verify(auditService).recordEvent(
                eq(ADMIN_ID),
                eq(AuditableAction.CAMBIO_ADMINISTRATIVO),
                eq("PROFESIONAL"),
                eq(profPublicId),
                eq(AuditResult.EXITO),
                eq(IP_ORIGEN)
        );
    }

    @Test
    void changeStatus_professionalInexistente_throwsResourceNotFoundException() {
        when(professionalRepository.findByPublicId("prof-no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus("prof-no-existe", "ACTIVO", ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Profesional no encontrado");
    }

    @Test
    void getByPublicId_notFound_throwsResourceNotFoundException() {
        when(professionalRepository.findByPublicId("prof-no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByPublicId("prof-no-existe"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Profesional no encontrado");
    }

    @Test
    void list_statusInvalid_throwsInvalidDataException() {
        assertThatThrownBy(() -> service.list(0, 10, null, "ESTADO_DESCONOCIDO"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Estado invalido");
    }
}
