package com.meditriaje.service;

import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.dto.prescription.CreatePrescriptionDetailRequest;
import com.meditriaje.dto.prescription.CreatePrescriptionRequest;
import com.meditriaje.dto.prescription.MedicationResponse;
import com.meditriaje.dto.prescription.PrescriptionDetailResponse;
import com.meditriaje.dto.prescription.PrescriptionResponse;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.ClinicalAttention;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.Medication;
import com.meditriaje.model.Patient;
import com.meditriaje.model.Professional;
import com.meditriaje.model.Prescription;
import com.meditriaje.model.User;
import com.meditriaje.repository.ClinicalAttentionRepository;
import com.meditriaje.repository.MedicationRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.ProfessionalRepository;
import com.meditriaje.repository.PrescriptionRepository;
import com.meditriaje.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrescriptionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfessionalRepository professionalRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private ClinicalAttentionRepository attentionRepository;

    @Mock
    private MedicationRepository medicationRepository;

    @Mock
    private PrescriptionRepository prescriptionRepository;

    @Mock
    private ClinicalAccessService clinicalAccessService;

    @Mock
    private AuditService auditService;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-10-03T10:00:00Z"), ZoneId.of("America/Bogota"));

    private PrescriptionService service;

    private static final String USUARIO_PROF_UUID = "usr-prof-uuid";
    private static final String USUARIO_PACIENTE_UUID = "usr-pac-uuid";
    private static final String USUARIO_ADMIN_UUID = "usr-admin-uuid";

    private static final Long PROFESIONAL_ID = 10L;
    private static final Long PACIENTE_ID = 20L;
    private static final Long ATENCION_ID = 30L;

    @BeforeEach
    void setUp() {
        service = new PrescriptionService(
                userRepository,
                professionalRepository,
                patientRepository,
                attentionRepository,
                medicationRepository,
                prescriptionRepository,
                clinicalAccessService,
                auditService,
                fixedClock
        );
    }

    @Test
    @DisplayName("emitirReceta - Emisión exitosa con snapshot de medicamentos y auditoría obligatoria CREACION_RECETA")
    void issuePrescription_success_issuesPrescriptionAndAudits() {
        User usuario = new User(1L, USUARIO_PROF_UUID, "dr.carlos@test.com", "hash", "ACTIVO", 0, null, Instant.now());
        Professional profesional = new Professional(PROFESIONAL_ID, 1L, "prof-uuid", 1L, "RM-12345", "Carlos", "Gomez", Instant.now(), null);
        ClinicalAttention atencion = new ClinicalAttention(ATENCION_ID, "atencion-uuid", 100L, PACIENTE_ID, PROFESIONAL_ID, "CERRADA");

        Medication med = new Medication(
                50L,
                "med-uuid-1",
                "MED-ACE-500",
                "Acetaminofen",
                "Acetaminofen",
                "Tableta",
                "500 mg",
                "ACTIVO"
        );

        CreatePrescriptionDetailRequest detalleReq = new CreatePrescriptionDetailRequest(
                "med-uuid-1",
                "500 mg",
                "Cada 8 horas",
                5,
                15,
                "Tomar despues de comidas"
        );
        CreatePrescriptionRequest request = new CreatePrescriptionRequest("atencion-uuid", 30, List.of(detalleReq));

        when(userRepository.findByPublicId(USUARIO_PROF_UUID)).thenReturn(Optional.of(usuario));
        when(professionalRepository.findByUserId(1L)).thenReturn(Optional.of(profesional));
        when(attentionRepository.findEntityByPublicId("atencion-uuid")).thenReturn(Optional.of(atencion));
        when(medicationRepository.findByPublicId("med-uuid-1")).thenReturn(Optional.of(med));
        when(prescriptionRepository.createPrescription(any(Prescription.class))).thenReturn(555L);

        PrescriptionDetailResponse detalleResp = new PrescriptionDetailResponse(
                "med-uuid-1",
                "MED-ACE-500",
                "Acetaminofen",
                "Acetaminofen",
                "Tableta",
                "500 mg",
                "500 mg",
                "Cada 8 horas",
                5,
                15,
                "Tomar despues de comidas"
        );
        PrescriptionResponse recetaResponse = new PrescriptionResponse(
                "receta-uuid-1",
                "atencion-uuid",
                "pac-uuid",
                "Juan Perez",
                "prof-uuid",
                "Carlos Gomez",
                "Medicina General",
                30,
                Instant.now(),
                List.of(detalleResp)
        );
        when(prescriptionRepository.findByPublicId(anyString())).thenReturn(Optional.of(recetaResponse));

        PrescriptionResponse resultado = service.issuePrescription(request, USUARIO_PROF_UUID, "192.168.1.1");

        assertThat(resultado).isNotNull();
        assertThat(resultado.publicId()).isEqualTo("receta-uuid-1");
        assertThat(resultado.details()).hasSize(1);
        assertThat(resultado.details().get(0).brandName()).isEqualTo("Acetaminofen");

        // Verificar que los detalles persistidos congelan el snapshot
        verify(prescriptionRepository).saveDetails(eq(555L), anyList());

        // Verificar auditoría inmutable
        ArgumentCaptor<AuditEvent> eventoCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).audit(eventoCaptor.capture());
        AuditEvent evento = eventoCaptor.getValue();
        assertThat(evento.action()).isEqualTo(AuditableAction.CREACION_RECETA);
        assertThat(evento.resourceType()).isEqualTo("RECETA");
        assertThat(evento.userId()).isEqualTo(1L);
        assertThat(evento.sourceIp()).isEqualTo("192.168.1.1");
    }

    @Test
    @DisplayName("emitirReceta - Rechazo cuando medicamento no existe o no está activo")
    void issuePrescription_medicationInactiveOrInexistente_throwsException() {
        User usuario = new User(1L, USUARIO_PROF_UUID, "dr.carlos@test.com", "hash", "ACTIVO", 0, null, Instant.now());
        Professional profesional = new Professional(PROFESIONAL_ID, 1L, "prof-uuid", 1L, "RM-12345", "Carlos", "Gomez", Instant.now(), null);
        ClinicalAttention atencion = new ClinicalAttention(ATENCION_ID, "atencion-uuid", 100L, PACIENTE_ID, PROFESIONAL_ID, "CERRADA");

        Medication medInactivo = new Medication(
                50L,
                "med-inactivo-uuid",
                "MED-INA-100",
                "InactivoMed",
                "Inactivo",
                "Tableta",
                "100 mg",
                "INACTIVO"
        );

        CreatePrescriptionDetailRequest detalleReq = new CreatePrescriptionDetailRequest(
                "med-inactivo-uuid",
                "100 mg",
                "Cada 12 horas",
                3,
                6,
                null
        );
        CreatePrescriptionRequest request = new CreatePrescriptionRequest("atencion-uuid", 30, List.of(detalleReq));

        when(userRepository.findByPublicId(USUARIO_PROF_UUID)).thenReturn(Optional.of(usuario));
        when(professionalRepository.findByUserId(1L)).thenReturn(Optional.of(profesional));
        when(attentionRepository.findEntityByPublicId("atencion-uuid")).thenReturn(Optional.of(atencion));
        when(medicationRepository.findByPublicId("med-inactivo-uuid")).thenReturn(Optional.of(medInactivo));

        assertThatThrownBy(() -> service.issuePrescription(request, USUARIO_PROF_UUID, "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("no existe o no se encuentra activo");

        verify(prescriptionRepository, never()).createPrescription(any());
        verify(auditService, never()).audit(any());
    }

    @Test
    @DisplayName("emitirReceta - Si falla el guardado de detalles, propaga excepción para rollback transaccional sin auditar éxito")
    void issuePrescription_failureInDetails_propagatesExcepcionForRollback() {
        User usuario = new User(1L, USUARIO_PROF_UUID, "dr.carlos@test.com", "hash", "ACTIVO", 0, null, Instant.now());
        Professional profesional = new Professional(PROFESIONAL_ID, 1L, "prof-uuid", 1L, "RM-12345", "Carlos", "Gomez", Instant.now(), null);
        ClinicalAttention atencion = new ClinicalAttention(ATENCION_ID, "atencion-uuid", 100L, PACIENTE_ID, PROFESIONAL_ID, "CERRADA");

        Medication med = new Medication(
                50L,
                "med-uuid-1",
                "MED-ACE-500",
                "Acetaminofén",
                "Acetaminofén",
                "Tableta",
                "500 mg",
                "ACTIVO"
        );

        CreatePrescriptionDetailRequest detalleReq = new CreatePrescriptionDetailRequest(
                "med-uuid-1",
                "500 mg",
                "Cada 8 horas",
                3,
                9,
                "Tomar con agua"
        );
        CreatePrescriptionRequest request = new CreatePrescriptionRequest("atencion-uuid", 30, List.of(detalleReq));

        when(userRepository.findByPublicId(USUARIO_PROF_UUID)).thenReturn(Optional.of(usuario));
        when(professionalRepository.findByUserId(1L)).thenReturn(Optional.of(profesional));
        when(attentionRepository.findEntityByPublicId("atencion-uuid")).thenReturn(Optional.of(atencion));
        when(medicationRepository.findByPublicId("med-uuid-1")).thenReturn(Optional.of(med));
        when(prescriptionRepository.createPrescription(any())).thenReturn(101L);
        doThrow(new org.springframework.dao.DataIntegrityViolationException("Fallo en constraint de clave foránea"))
                .when(prescriptionRepository).saveDetails(eq(101L), anyList());

        assertThatThrownBy(() -> service.issuePrescription(request, USUARIO_PROF_UUID, "127.0.0.1"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                .hasMessageContaining("Fallo en constraint");

        verify(auditService, never()).audit(any());
    }

    @Test
    @DisplayName("emitirReceta - Rechazo si usuario autenticado no está registrado como profesional")
    void issuePrescription_userNotProfessional_throws403() {
        User usuario = new User(2L, "usr-pac-1", "paciente@test.com", "hash", "ACTIVO", 0, null, Instant.now());
        when(userRepository.findByPublicId("usr-pac-1")).thenReturn(Optional.of(usuario));
        when(professionalRepository.findByUserId(2L)).thenReturn(Optional.empty());

        CreatePrescriptionRequest request = new CreatePrescriptionRequest("atencion-uuid", 30, List.of(
                new CreatePrescriptionDetailRequest("med-uuid-1", "500 mg", "8h", 3, 9, null)
        ));

        assertThatThrownBy(() -> service.issuePrescription(request, "usr-pac-1", "127.0.0.1"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Usuario no registrado como profesional asistencial");
    }

    @Test
    @DisplayName("emitirReceta - Médico no autor valida relación asistencial y es rechazado si no la tiene")
    void issuePrescription_nonAuthorPhysicianWithoutRelationship_throws403() {
        User usuario = new User(1L, USUARIO_PROF_UUID, "dr.otro@test.com", "hash", "ACTIVO", 0, null, Instant.now());
        Professional profesional = new Professional(PROFESIONAL_ID, 1L, "prof-uuid", 1L, "RM-12345", "Otro", "Medico", Instant.now(), null);
        // La atención fue realizada por otro profesional (ID 99L)
        ClinicalAttention atencion = new ClinicalAttention(ATENCION_ID, "atencion-uuid", 100L, PACIENTE_ID, 99L, "CERRADA");

        when(userRepository.findByPublicId(USUARIO_PROF_UUID)).thenReturn(Optional.of(usuario));
        when(professionalRepository.findByUserId(1L)).thenReturn(Optional.of(profesional));
        when(attentionRepository.findEntityByPublicId("atencion-uuid")).thenReturn(Optional.of(atencion));

        doThrow(new UnauthorizedAccessException("El profesional no cuenta con una relacion asistencial activa con el paciente."))
                .when(clinicalAccessService).validateCareRelationship(PROFESIONAL_ID, PACIENTE_ID);

        CreatePrescriptionRequest request = new CreatePrescriptionRequest("atencion-uuid", 30, List.of(
                new CreatePrescriptionDetailRequest("med-uuid-1", "500 mg", "8h", 3, 9, null)
        ));

        assertThatThrownBy(() -> service.issuePrescription(request, USUARIO_PROF_UUID, "127.0.0.1"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("relacion asistencial activa");

        verify(prescriptionRepository, never()).createPrescription(any());
    }

    @Test
    @DisplayName("obtenerPorPublicId - Profesional autor consulta exitosamente")
    void getByPublicId_authorPhysician_success() {
        Prescription receta = new Prescription(555L, "receta-uuid-1", ATENCION_ID, PACIENTE_ID, PROFESIONAL_ID, 30, Instant.now());
        User usuario = new User(1L, USUARIO_PROF_UUID, "dr.carlos@test.com", "hash", "ACTIVO", 0, null, Instant.now());
        Professional profesional = new Professional(PROFESIONAL_ID, 1L, "prof-uuid", 1L, "RM-12345", "Carlos", "Gomez", Instant.now(), null);

        PrescriptionResponse recetaResponse = new PrescriptionResponse(
                "receta-uuid-1", "atencion-uuid", "pac-uuid", "Juan", "prof-uuid", "Carlos", "General", 30, Instant.now(), List.of()
        );

        when(prescriptionRepository.findEntityByPublicId("receta-uuid-1")).thenReturn(Optional.of(receta));
        when(userRepository.findByPublicId(USUARIO_PROF_UUID)).thenReturn(Optional.of(usuario));
        when(professionalRepository.findByUserId(1L)).thenReturn(Optional.of(profesional));
        when(prescriptionRepository.findByPublicId("receta-uuid-1")).thenReturn(Optional.of(recetaResponse));

        PrescriptionResponse resp = service.getByPublicId(
                "receta-uuid-1",
                USUARIO_PROF_UUID,
                List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"))
        );

        assertThat(resp).isNotNull();
        assertThat(resp.publicId()).isEqualTo("receta-uuid-1");
        verify(clinicalAccessService, never()).validateCareRelationship(anyLong(), anyLong());
    }

    @Test
    @DisplayName("obtenerPorPublicId - Profesional no autor con relación activa consulta exitosamente")
    void getByPublicId_nonAuthorPhysicianWithRelationship_success() {
        Prescription receta = new Prescription(555L, "receta-uuid-1", ATENCION_ID, PACIENTE_ID, 99L, 30, Instant.now());
        User usuario = new User(1L, USUARIO_PROF_UUID, "dr.carlos@test.com", "hash", "ACTIVO", 0, null, Instant.now());
        Professional profesional = new Professional(PROFESIONAL_ID, 1L, "prof-uuid", 1L, "RM-12345", "Carlos", "Gomez", Instant.now(), null);

        PrescriptionResponse recetaResponse = new PrescriptionResponse(
                "receta-uuid-1", "atencion-uuid", "pac-uuid", "Juan", "prof-uuid-99", "Otro", "General", 30, Instant.now(), List.of()
        );

        when(prescriptionRepository.findEntityByPublicId("receta-uuid-1")).thenReturn(Optional.of(receta));
        when(userRepository.findByPublicId(USUARIO_PROF_UUID)).thenReturn(Optional.of(usuario));
        when(professionalRepository.findByUserId(1L)).thenReturn(Optional.of(profesional));
        doNothing().when(clinicalAccessService).validateCareRelationship(PROFESIONAL_ID, PACIENTE_ID);
        when(prescriptionRepository.findByPublicId("receta-uuid-1")).thenReturn(Optional.of(recetaResponse));

        PrescriptionResponse resp = service.getByPublicId(
                "receta-uuid-1",
                USUARIO_PROF_UUID,
                List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"))
        );

        assertThat(resp).isNotNull();
        verify(clinicalAccessService).validateCareRelationship(PROFESIONAL_ID, PACIENTE_ID);
    }

    @Test
    @DisplayName("obtenerPorPublicId - Administrador es rechazado categóricamente (ADR-007)")
    void getByPublicId_admin_throws403() {
        assertThatThrownBy(() -> service.getByPublicId(
                "receta-uuid-1",
                USUARIO_ADMIN_UUID,
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))
        )).isInstanceOf(UnauthorizedAccessException.class)
          .hasMessageContaining("El personal administrativo no tiene acceso a recetas ni contenido clinico");

        verify(prescriptionRepository, never()).findEntityByPublicId(anyString());
        verify(prescriptionRepository, never()).findByPublicId(anyString());
    }

    @Test
    @DisplayName("obtenerPorPublicId - Paciente ajeno es rechazado (403)")
    void getByPublicId_otherPatient_throws403() {
        Prescription receta = new Prescription(555L, "receta-uuid-1", ATENCION_ID, PACIENTE_ID, PROFESIONAL_ID, 30, Instant.now());
        User usuario = new User(2L, USUARIO_PACIENTE_UUID, "paciente@test.com", "hash", "ACTIVO", 0, null, Instant.now());
        // Paciente con ID distinto (999L != PACIENTE_ID)
        Patient paciente = new Patient(999L, 2L, "pac-uuid-ajeno", "CC", "12345", "Pedro", "Gomez", LocalDate.of(1990, 1, 1), null);

        when(prescriptionRepository.findEntityByPublicId("receta-uuid-1")).thenReturn(Optional.of(receta));
        when(userRepository.findByPublicId(USUARIO_PACIENTE_UUID)).thenReturn(Optional.of(usuario));
        when(patientRepository.findByUserId(2L)).thenReturn(Optional.of(paciente));

        assertThatThrownBy(() -> service.getByPublicId(
                "receta-uuid-1",
                USUARIO_PACIENTE_UUID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE"))
        )).isInstanceOf(UnauthorizedAccessException.class)
          .hasMessageContaining("No tiene autorizacion para acceder a la receta de otro paciente");

        verify(prescriptionRepository, never()).findByPublicId(anyString());
    }

    @Test
    @DisplayName("obtenerPorPublicId - Paciente dueño consulta su propia receta (200)")
    void getByPublicId_ownPatient_success() {
        Prescription receta = new Prescription(555L, "receta-uuid-1", ATENCION_ID, PACIENTE_ID, PROFESIONAL_ID, 30, Instant.now());
        User usuario = new User(2L, USUARIO_PACIENTE_UUID, "paciente@test.com", "hash", "ACTIVO", 0, null, Instant.now());
        Patient paciente = new Patient(PACIENTE_ID, 2L, "pac-uuid-propio", "CC", "12345", "Juan", "Perez", LocalDate.of(1990, 1, 1), null);

        PrescriptionResponse recetaResponse = new PrescriptionResponse(
                "receta-uuid-1", "atencion-uuid", "pac-uuid-propio", "Juan Perez", "prof-uuid", "Carlos Gomez", "General", 30, Instant.now(), List.of()
        );

        when(prescriptionRepository.findEntityByPublicId("receta-uuid-1")).thenReturn(Optional.of(receta));
        when(userRepository.findByPublicId(USUARIO_PACIENTE_UUID)).thenReturn(Optional.of(usuario));
        when(patientRepository.findByUserId(2L)).thenReturn(Optional.of(paciente));
        when(prescriptionRepository.findByPublicId("receta-uuid-1")).thenReturn(Optional.of(recetaResponse));

        PrescriptionResponse resp = service.getByPublicId(
                "receta-uuid-1",
                USUARIO_PACIENTE_UUID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE"))
        );

        assertThat(resp).isNotNull();
        assertThat(resp.publicId()).isEqualTo("receta-uuid-1");
    }

    @Test
    @DisplayName("listarCatalogo - Paginación segura y retorno de datos")
    void listCatalog_returnsPage() {
        MedicationResponse med = new MedicationResponse(
                "med-uuid-1", "MED-ACE-500", "Acetaminofen", "Acetaminofen", "Tableta", "500 mg", "ACTIVO"
        );
        when(medicationRepository.listActive(eq("aceta"), eq(0), eq(10)))
                .thenReturn(List.of(med));
        when(medicationRepository.countActive("aceta")).thenReturn(1);

        PaginatedResponse<MedicationResponse> resp = service.listCatalog("aceta", 0, 10);

        assertThat(resp).isNotNull();
        assertThat(resp.content()).hasSize(1);
        assertThat(resp.totalElements()).isEqualTo(1L);
        assertThat(resp.page()).isZero();
    }

    @Test
    @DisplayName("obtenerMisRecetas - Paciente autenticado consulta sus recetas exitosamente y audita CONSULTA_HISTORIA")
    void getMyPrescriptions_success() {
        User usuario = new User(2L, USUARIO_PACIENTE_UUID, "paciente@test.com", "hash", "ACTIVO", 0, null, Instant.now());
        Patient paciente = new Patient(PACIENTE_ID, 2L, "pac-uuid-propio", "CC", "12345", "Juan", "Perez", LocalDate.of(1990, 1, 1), null);

        PrescriptionResponse recetaResponse = new PrescriptionResponse(
                "receta-uuid-1", "atencion-uuid", "pac-uuid-propio", "Juan Perez", "prof-uuid", "Carlos Gomez", "General", 30, Instant.now(), List.of()
        );

        when(userRepository.findByPublicId(USUARIO_PACIENTE_UUID)).thenReturn(Optional.of(usuario));
        when(patientRepository.findByUserId(2L)).thenReturn(Optional.of(paciente));
        when(prescriptionRepository.listByPatientId(PACIENTE_ID, 0, 10)).thenReturn(List.of(recetaResponse));
        when(prescriptionRepository.countByPatientId(PACIENTE_ID)).thenReturn(1);

        PaginatedResponse<PrescriptionResponse> resp = service.getMyPrescriptions(USUARIO_PACIENTE_UUID, 0, 10, "192.168.1.50");

        assertThat(resp).isNotNull();
        assertThat(resp.content()).hasSize(1);
        assertThat(resp.totalElements()).isEqualTo(1L);
        assertThat(resp.content().get(0).publicId()).isEqualTo("receta-uuid-1");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).audit(captor.capture());
        AuditEvent evento = captor.getValue();
        assertThat(evento.action()).isEqualTo(AuditableAction.CONSULTA_HISTORIA);
        assertThat(evento.resourceType()).isEqualTo("RECETA");
        assertThat(evento.resourcePublicId()).isEqualTo("pac-uuid-propio");
        assertThat(evento.sourceIp()).isEqualTo("192.168.1.50");
    }

    @Test
    @DisplayName("obtenerMisRecetas - Falla si usuario autenticado no existe")
    void getMyPrescriptions_rejectsWhenUserNotFound() {
        when(userRepository.findByPublicId("uuid-inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyPrescriptions("uuid-inexistente", 0, 10, "127.0.0.1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Usuario autenticado no encontrado");
    }

    @Test
    @DisplayName("obtenerMisRecetas - Falla si usuario no está registrado como paciente")
    void getMyPrescriptions_rejectsWhenUserNotPatient() {
        User usuario = new User(10L, "uuid-no-paciente", "otro@test.com", "hash", "ACTIVO", 0, null, Instant.now());
        when(userRepository.findByPublicId("uuid-no-paciente")).thenReturn(Optional.of(usuario));
        when(patientRepository.findByUserId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyPrescriptions("uuid-no-paciente", 0, 10, "127.0.0.1"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Usuario no registrado como paciente");
    }

    @Test
    @DisplayName("obtenerMisRecetas - Falla si usuarioAutenticadoPublicId es nulo o blanco")
    void getMyPrescriptions_rejectsWhenUserNullOrBlank() {
        assertThatThrownBy(() -> service.getMyPrescriptions(null, 0, 10, "127.0.0.1"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Usuario no autenticado");

        assertThatThrownBy(() -> service.getMyPrescriptions("   ", 0, 10, "127.0.0.1"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Usuario no autenticado");
    }
}
