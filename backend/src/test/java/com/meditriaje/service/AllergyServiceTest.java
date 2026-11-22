package com.meditriaje.service;

import com.meditriaje.dto.allergy.AllergyResponse;
import com.meditriaje.dto.allergy.InactivateAllergyRequest;
import com.meditriaje.dto.allergy.RegisterAllergyRequest;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.Allergy;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.Patient;
import com.meditriaje.model.Professional;
import com.meditriaje.model.User;
import com.meditriaje.repository.AllergyRepository;
import com.meditriaje.repository.ClinicalAttentionRepository;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllergyServiceTest {

    @Mock
    private AllergyRepository allergyRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProfessionalRepository professionalRepository;
    @Mock
    private ClinicalAttentionRepository attentionRepository;
    @Mock
    private ClinicalAccessService clinicalAccessService;
    @Mock
    private AuditService auditService;

    private Clock clock;
    private AllergyService allergyService;

    private final Instant now = Instant.parse("2026-10-06T15:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(now, ZoneId.of("America/Bogota"));
        allergyService = new AllergyService(
                allergyRepository,
                patientRepository,
                userRepository,
                professionalRepository,
                attentionRepository,
                clinicalAccessService,
                auditService,
                clock
        );
    }

    // =========================================================================
    // LISTADO POR PROFESIONAL
    // =========================================================================

    @Test
    @DisplayName("listarAlergiasPacienteParaProfesional: profesional con relación asistencial consulta y audita ALERGIA_CONSULTADA")
    void listAllergies_professionalWithRelationship_success() {
        User usuario = new User(1L, "usr-med-1", "med@hospital.co", "hash", "ACTIVO", 0, null, now);
        Patient paciente = new Patient(10L, 2L, "pac-pub-1", "CC", "123456", "Juan", "Perez", null, null, now, now);
        AllergyResponse response = new AllergyResponse(
                "ale-pub-1", "pac-pub-1", "Penicilina", "Urticaria", "MODERADA", "ACTIVA", "PROFESIONAL", false, null, now, null, null
        );

        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuario));
        when(patientRepository.findByPublicId("pac-pub-1")).thenReturn(Optional.of(paciente));
        when(allergyRepository.listByPatientPublicId("pac-pub-1", false)).thenReturn(List.of(response));

        List<AllergyResponse> resultado = allergyService.listPatientAllergiesForProfessional(
                "pac-pub-1", false, "usr-med-1", List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL")), "127.0.0.1"
        );

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).substance()).isEqualTo("Penicilina");
        verify(clinicalAccessService).validateClinicalHistoryAccess(eq("usr-med-1"), eq("pac-pub-1"), any());

        ArgumentCaptor<AuditEvent> eventoCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).audit(eventoCaptor.capture());
        AuditEvent evento = eventoCaptor.getValue();
        assertThat(evento.action()).isEqualTo(AuditableAction.ALERGIA_CONSULTADA);
        assertThat(evento.resourcePublicId()).isEqualTo("pac-pub-1");
    }

    @Test
    @DisplayName("listarAlergiasPacienteParaProfesional: sin autorización clínica lanza AccesoNoAutorizadoException")
    void listAllergies_withoutClinicalAuth_throwsUnauthorizedAccess() {
        User usuario = new User(1L, "usr-med-1", "med@hospital.co", "hash", "ACTIVO", 0, null, now);
        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuario));
        doThrow(new UnauthorizedAccessException("No existe relación asistencial activa."))
                .when(clinicalAccessService).validateClinicalHistoryAccess(anyString(), anyString(), any());

        assertThatThrownBy(() -> allergyService.listPatientAllergiesForProfessional(
                "pac-pub-1", false, "usr-med-1", List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL")), "127.0.0.1"
        )).isInstanceOf(UnauthorizedAccessException.class);

        verify(allergyRepository, never()).listByPatientPublicId(anyString(), anyBoolean());
    }

    // =========================================================================
    // REGISTRO POR PROFESIONAL
    // =========================================================================

    @Test
    @DisplayName("registrarAlergiaPorProfesional: profesional con relación ordinaria registra exitosamente y audita ALERGIA_REGISTRADA")
    void registerAllergy_professionalWithRelationshipOrdinaria_success() {
        User usuario = new User(1L, "usr-med-1", "med@hospital.co", "hash", "ACTIVO", 0, null, now);
        Professional profesional = new Professional(5L, 1L, "prof-pub-1", 100L, "RM-123", "Carlos", "Med", now, now);
        Patient paciente = new Patient(10L, 2L, "pac-pub-1", "CC", "123456", "Juan", "Perez", null, null, now, now);

        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuario));
        when(professionalRepository.findByUserId(1L)).thenReturn(Optional.of(profesional));
        when(patientRepository.findByPublicId("pac-pub-1")).thenReturn(Optional.of(paciente));
        doNothing().when(clinicalAccessService).validateClinicalWriteAccess(5L, 10L);
        when(allergyRepository.existsActiveBySubstance(10L, "Amoxicilina")).thenReturn(false);
        when(allergyRepository.create(any(Allergy.class))).thenReturn(50L);

        AllergyResponse response = new AllergyResponse(
                "ale-new-uuid", "pac-pub-1", "Amoxicilina", "Edema", "GRAVE", "ACTIVA", "PROFESIONAL", false, null, now, null, null
        );
        when(allergyRepository.findByPublicId(anyString())).thenReturn(Optional.of(response));

        RegisterAllergyRequest req = new RegisterAllergyRequest("Amoxicilina", "Edema", "GRAVE");
        AllergyResponse creada = allergyService.registerAllergyByProfessional("pac-pub-1", req, "usr-med-1", "127.0.0.1");

        assertThat(creada.substance()).isEqualTo("Amoxicilina");
        assertThat(creada.selfReported()).isFalse();

        verify(clinicalAccessService).validateClinicalWriteAccess(5L, 10L);
        verify(auditService).audit(any(AuditEvent.class));
    }

    @Test
    @DisplayName("registrarAlergiaPorProfesional: break-glass solo lectura es rechazado para registrar alergia (403)")
    void registerAllergy_breakGlassOnlyLectura_throwsUnauthorizedAccess() {
        User usuario = new User(1L, "usr-med-1", "med@hospital.co", "hash", "ACTIVO", 0, null, now);
        Professional profesional = new Professional(5L, 1L, "prof-pub-1", 100L, "RM-123", "Carlos", "Med", now, now);
        Patient paciente = new Patient(10L, 2L, "pac-pub-1", "CC", "123456", "Juan", "Perez", null, null, now, now);

        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuario));
        when(professionalRepository.findByUserId(1L)).thenReturn(Optional.of(profesional));
        when(patientRepository.findByPublicId("pac-pub-1")).thenReturn(Optional.of(paciente));
        doThrow(new UnauthorizedAccessException("El acceso Break-Glass es de solo lectura; no permite registrar ni modificar informacion clinica."))
                .when(clinicalAccessService).validateClinicalWriteAccess(5L, 10L);

        RegisterAllergyRequest req = new RegisterAllergyRequest("Aspirina", null, "MODERADA");

        assertThatThrownBy(() -> allergyService.registerAllergyByProfessional("pac-pub-1", req, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Break-Glass es de solo lectura");

        verify(allergyRepository, never()).create(any());
    }

    @Test
    @DisplayName("registrarAlergiaPorProfesional: rechaza si la sustancia ya está activa para el paciente (400 DatosInvalidos)")
    void registerAllergy_substanceActiveDuplicada_throwsInvalidData() {
        User usuario = new User(1L, "usr-med-1", "med@hospital.co", "hash", "ACTIVO", 0, null, now);
        Professional profesional = new Professional(5L, 1L, "prof-pub-1", 100L, "RM-123", "Carlos", "Med", now, now);
        Patient paciente = new Patient(10L, 2L, "pac-pub-1", "CC", "123456", "Juan", "Perez", null, null, now, now);

        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuario));
        when(professionalRepository.findByUserId(1L)).thenReturn(Optional.of(profesional));
        when(patientRepository.findByPublicId("pac-pub-1")).thenReturn(Optional.of(paciente));
        when(allergyRepository.existsActiveBySubstance(10L, "Penicilina")).thenReturn(true);

        RegisterAllergyRequest req = new RegisterAllergyRequest("Penicilina", "Shock", "GRAVE");

        assertThatThrownBy(() -> allergyService.registerAllergyByProfessional("pac-pub-1", req, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("ya cuenta con una alergia activa");

        verify(allergyRepository, never()).create(any());
    }

    // =========================================================================
    // INACTIVACIÓN POR PROFESIONAL
    // =========================================================================

    @Test
    @DisplayName("inactivarAlergiaPorProfesional: profesional con relación ordinaria inactiva exitosamente y audita ALERGIA_INACTIVADA")
    void inactivateAllergy_professional_success() {
        User usuario = new User(1L, "usr-med-1", "med@hospital.co", "hash", "ACTIVO", 0, null, now);
        Professional profesional = new Professional(5L, 1L, "prof-pub-1", 100L, "RM-123", "Carlos", "Med", now, now);
        Allergy alergia = new Allergy(20L, "ale-pub-1", 10L, "Penicilina", "Urticaria", "MODERADA", "ACTIVA", "PROFESIONAL", 1L, null, null, null, null, now, now);

        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuario));
        when(professionalRepository.findByUserId(1L)).thenReturn(Optional.of(profesional));
        when(allergyRepository.findEntityByPublicId("ale-pub-1")).thenReturn(Optional.of(alergia));
        doNothing().when(clinicalAccessService).validateClinicalWriteAccess(5L, 10L);
        when(allergyRepository.inactivate(eq(20L), eq(1L), eq("Prueba negativa de hipersensibilidad"), any())).thenReturn(1);

        AllergyResponse response = new AllergyResponse(
                "ale-pub-1", "pac-pub-1", "Penicilina", "Urticaria", "MODERADA", "INACTIVA", "PROFESIONAL", false, null, now, now, "Prueba negativa de hipersensibilidad"
        );
        when(allergyRepository.findByPublicId("ale-pub-1")).thenReturn(Optional.of(response));

        InactivateAllergyRequest req = new InactivateAllergyRequest("Prueba negativa de hipersensibilidad");
        AllergyResponse res = allergyService.inactivateAllergyByProfessional("ale-pub-1", req, "usr-med-1", "127.0.0.1");

        assertThat(res.status()).isEqualTo("INACTIVA");
        assertThat(res.inactivationReason()).isEqualTo("Prueba negativa de hipersensibilidad");
        verify(auditService).audit(any(AuditEvent.class));
    }

    @Test
    @DisplayName("inactivarAlergiaPorProfesional: alergia ya inactiva lanza DatosInvalidosException")
    void inactivateAllergy_alreadyInactive_throwsInvalidData() {
        User usuario = new User(1L, "usr-med-1", "med@hospital.co", "hash", "ACTIVO", 0, null, now);
        Professional profesional = new Professional(5L, 1L, "prof-pub-1", 100L, "RM-123", "Carlos", "Med", now, now);
        Allergy alergia = new Allergy(20L, "ale-pub-1", 10L, "Penicilina", "Urticaria", "MODERADA", "INACTIVA", "PROFESIONAL", 1L, null, now, 1L, "Inactivada antes", now, now);

        when(userRepository.findByPublicId("usr-med-1")).thenReturn(Optional.of(usuario));
        when(professionalRepository.findByUserId(1L)).thenReturn(Optional.of(profesional));
        when(allergyRepository.findEntityByPublicId("ale-pub-1")).thenReturn(Optional.of(alergia));

        InactivateAllergyRequest req = new InactivateAllergyRequest("Intento repetido de inactivacion");

        assertThatThrownBy(() -> allergyService.inactivateAllergyByProfessional("ale-pub-1", req, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("ya se encuentra inactiva");

        verify(allergyRepository, never()).inactivate(anyLong(), anyLong(), anyString(), any());
    }

    // =========================================================================
    // GESTIÓN POR PACIENTE (AUTORREPORTADAS)
    // =========================================================================

    @Test
    @DisplayName("listarMisAlergias: paciente consulta sus alergias y audita ALERGIA_CONSULTADA")
    void listMyAllergies_patient_success() {
        User usuario = new User(2L, "usr-pac-1", "pac@test.co", "hash", "ACTIVO", 0, null, now);
        Patient paciente = new Patient(10L, 2L, "pac-pub-1", "CC", "123456", "Juan", "Perez", null, null, now, now);

        when(userRepository.findByPublicId("usr-pac-1")).thenReturn(Optional.of(usuario));
        when(patientRepository.findByUserId(2L)).thenReturn(Optional.of(paciente));
        when(allergyRepository.listByPatientPublicId("pac-pub-1", true)).thenReturn(List.of());

        List<AllergyResponse> lista = allergyService.listMyAllergies("usr-pac-1", "127.0.0.1");

        assertThat(lista).isEmpty();
        verify(auditService).audit(any(AuditEvent.class));
    }

    @Test
    @DisplayName("registrarMiAlergia: paciente declara alergia autorreportada (origen PACIENTE)")
    void registerMyAllergy_patient_success() {
        User usuario = new User(2L, "usr-pac-1", "pac@test.co", "hash", "ACTIVO", 0, null, now);
        Patient paciente = new Patient(10L, 2L, "pac-pub-1", "CC", "123456", "Juan", "Perez", null, null, now, now);

        when(userRepository.findByPublicId("usr-pac-1")).thenReturn(Optional.of(usuario));
        when(patientRepository.findByUserId(2L)).thenReturn(Optional.of(paciente));
        when(allergyRepository.existsActiveBySubstance(10L, "Mariscos")).thenReturn(false);
        when(allergyRepository.create(any(Allergy.class))).thenReturn(60L);

        AllergyResponse response = new AllergyResponse(
                "ale-mar-1", "pac-pub-1", "Mariscos", "Prurito", "LEVE", "ACTIVA", "PACIENTE", true, null, now, null, null
        );
        when(allergyRepository.findByPublicId(anyString())).thenReturn(Optional.of(response));

        RegisterAllergyRequest req = new RegisterAllergyRequest("Mariscos", "Prurito", "LEVE");
        AllergyResponse res = allergyService.registerMyAllergy(req, "usr-pac-1", "127.0.0.1");

        assertThat(res.substance()).isEqualTo("Mariscos");
        assertThat(res.origin()).isEqualTo("PACIENTE");
        assertThat(res.selfReported()).isTrue();

        ArgumentCaptor<Allergy> alergiaCaptor = ArgumentCaptor.forClass(Allergy.class);
        verify(allergyRepository).create(alergiaCaptor.capture());
        assertThat(alergiaCaptor.getValue().origin()).isEqualTo("PACIENTE");
        assertThat(alergiaCaptor.getValue().registeredByUserId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("inactivarMiAlergia: paciente puede inactivar su propia alergia autorreportada")
    void inactivateMyAllergy_selfReportedPropia_success() {
        User usuario = new User(2L, "usr-pac-1", "pac@test.co", "hash", "ACTIVO", 0, null, now);
        Patient paciente = new Patient(10L, 2L, "pac-pub-1", "CC", "123456", "Juan", "Perez", null, null, now, now);
        Allergy alergia = new Allergy(30L, "ale-pac-30", 10L, "Chocolate", "Granos", "LEVE", "ACTIVA", "PACIENTE", 2L, null, null, null, null, now, now);

        when(userRepository.findByPublicId("usr-pac-1")).thenReturn(Optional.of(usuario));
        when(patientRepository.findByUserId(2L)).thenReturn(Optional.of(paciente));
        when(allergyRepository.findEntityByPublicId("ale-pac-30")).thenReturn(Optional.of(alergia));
        when(allergyRepository.inactivate(eq(30L), eq(2L), eq("Ya no me genera síntomas"), any())).thenReturn(1);

        AllergyResponse response = new AllergyResponse(
                "ale-pac-30", "pac-pub-1", "Chocolate", "Granos", "LEVE", "INACTIVA", "PACIENTE", true, null, now, now, "Ya no me genera síntomas"
        );
        when(allergyRepository.findByPublicId("ale-pac-30")).thenReturn(Optional.of(response));

        InactivateAllergyRequest req = new InactivateAllergyRequest("Ya no me genera síntomas");
        AllergyResponse res = allergyService.inactivateMyAllergy("ale-pac-30", req, "usr-pac-1", "127.0.0.1");

        assertThat(res.status()).isEqualTo("INACTIVA");
        verify(auditService).audit(any(AuditEvent.class));
    }

    @Test
    @DisplayName("inactivarMiAlergia: paciente NO puede inactivar una alergia diagnosticada por el profesional (403)")
    void inactivateMyAllergy_originProfessional_throwsUnauthorizedAccess() {
        User usuario = new User(2L, "usr-pac-1", "pac@test.co", "hash", "ACTIVO", 0, null, now);
        Patient paciente = new Patient(10L, 2L, "pac-pub-1", "CC", "123456", "Juan", "Perez", null, null, now, now);
        Allergy alergia = new Allergy(40L, "ale-med-40", 10L, "Penicilina", "Shock", "GRAVE", "ACTIVA", "PROFESIONAL", 1L, null, null, null, null, now, now);

        when(userRepository.findByPublicId("usr-pac-1")).thenReturn(Optional.of(usuario));
        when(patientRepository.findByUserId(2L)).thenReturn(Optional.of(paciente));
        when(allergyRepository.findEntityByPublicId("ale-med-40")).thenReturn(Optional.of(alergia));

        InactivateAllergyRequest req = new InactivateAllergyRequest("Deseo quitarla porque no me gusta");

        assertThatThrownBy(() -> allergyService.inactivateMyAllergy("ale-med-40", req, "usr-pac-1", "127.0.0.1"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("solo puede inactivar alergias autorreportadas");

        verify(allergyRepository, never()).inactivate(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("inactivarMiAlergia: paciente intentando inactivar alergia de otro paciente lanza AccesoNoAutorizadoException (403)")
    void inactivateMyAllergy_otherPatient_throwsUnauthorizedAccess() {
        User usuario = new User(2L, "usr-pac-1", "pac@test.co", "hash", "ACTIVO", 0, null, now);
        Patient paciente = new Patient(10L, 2L, "pac-pub-1", "CC", "123456", "Juan", "Perez", null, null, now, now);
        Allergy alergiaAjena = new Allergy(50L, "ale-ajena-50", 99L, "Polen", "Rinitis", "LEVE", "ACTIVA", "PACIENTE", 99L, null, null, null, null, now, now);

        when(userRepository.findByPublicId("usr-pac-1")).thenReturn(Optional.of(usuario));
        when(patientRepository.findByUserId(2L)).thenReturn(Optional.of(paciente));
        when(allergyRepository.findEntityByPublicId("ale-ajena-50")).thenReturn(Optional.of(alergiaAjena));

        InactivateAllergyRequest req = new InactivateAllergyRequest("Intento no autorizado");

        assertThatThrownBy(() -> allergyService.inactivateMyAllergy("ale-ajena-50", req, "usr-pac-1", "127.0.0.1"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("otro paciente");

        verify(allergyRepository, never()).inactivate(anyLong(), anyLong(), anyString(), any());
    }
}
