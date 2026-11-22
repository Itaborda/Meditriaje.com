package com.meditriaje.service;

import com.meditriaje.dto.triage.SymptomCatalogResponse;
import com.meditriaje.dto.triage.CreateTriageRequest;
import com.meditriaje.dto.triage.SymptomItemRequest;
import com.meditriaje.dto.triage.SymptomItemResponse;
import com.meditriaje.dto.triage.TriageResponse;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.Patient;
import com.meditriaje.model.AuditResult;
import com.meditriaje.model.Triage;
import com.meditriaje.model.User;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.TriageRepository;
import com.meditriaje.repository.UserRepository;
import com.meditriaje.triage.TriageEngine;
import com.meditriaje.triage.PriorityLevel;
import com.meditriaje.triage.TriageResult;
import com.meditriaje.triage.SuggestedRoute;
import com.meditriaje.triage.TriageEngineFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriageServiceTest {

    @Mock
    private TriageRepository triageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private TriageEngineFactory triageEngineFactory;
    @Mock
    private AuditService auditService;
    @Mock
    private TriageEngine motorTriaje;

    private TriageService triageService;

    private static final String USUARIO_PUBLIC_ID = "usr-uuid-paciente";
    private static final String IP_ORIGEN = "192.168.1.50";
    private static final Instant AHORA = Instant.parse("2026-10-10T12:00:00Z");

    private User usuarioMock;
    private Patient pacienteMock;

    @BeforeEach
    void setUp() {
        triageService = new TriageService(
                triageRepository,
                userRepository,
                patientRepository,
                triageEngineFactory,
                auditService
        );

        usuarioMock = new User(
                1L, USUARIO_PUBLIC_ID, "paciente@test.com", "hash", "ACTIVO", 0, null, AHORA, false
        );
        pacienteMock = new Patient(
                10L, 1L, "pac-uuid-1", "CC", "12345678", "Pepito", "Perez",
                LocalDate.of(1990, 1, 1), "3001234567", AHORA, null
        );
    }

    @Test
    void evaluateAndSaveTriage_notUrgente_persistsCorrectlyAndAuditsRealizado() {
        CreateTriageRequest request = new CreateTriageRequest(
                List.of(new SymptomItemRequest("FIEBRE", BigDecimal.valueOf(12.0), 3)),
                "Observacion leve"
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(pacienteMock));
        when(triageEngineFactory.getEngine()).thenReturn(motorTriaje);

        TriageResult resultado = new TriageResult(
                PriorityLevel.IV,
                SuggestedRoute.CITA_TELEMEDICINA,
                false,
                "v1-prototipo",
                "Se sugiere una cita de telemedicina.",
                "Aviso de prototipo",
                List.of()
        );
        when(motorTriaje.evaluate(any())).thenReturn(resultado);
        when(triageRepository.getSymptomCodeToIdMap()).thenReturn(Map.of("FIEBRE", 101L));
        when(triageRepository.saveTriage(any(Triage.class))).thenReturn(50L);

        TriageResponse responseMock = new TriageResponse(
                "triaje-uuid-1", "pac-uuid-1", "IV", "CITA_TELEMEDICINA", false, "v1-prototipo",
                "Se sugiere una cita de telemedicina.", "Aviso", List.of(),
                List.of(new SymptomItemResponse("FIEBRE", "Fiebre", BigDecimal.valueOf(12.0), 3, false)),
                "Observacion leve", AHORA
        );
        when(triageRepository.findByPublicId(anyString())).thenReturn(Optional.of(responseMock));

        TriageResponse res = triageService.evaluateAndSaveTriage(request, USUARIO_PUBLIC_ID, IP_ORIGEN);

        assertThat(res).isNotNull();
        assertThat(res.isEmergency()).isFalse();
        assertThat(res.priorityLevel()).isEqualTo("IV");

        // Verifica inserción de síntomas
        verify(triageRepository).saveSymptoms(eq(50L), anyList());

        // Verifica auditoría: solo TRIAJE_REALIZADO, no TRIAJE_EMERGENCIA
        ArgumentCaptor<AuditEvent> audCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, times(1)).audit(audCaptor.capture());
        AuditEvent evento = audCaptor.getValue();
        assertThat(evento.action()).isEqualTo(AuditableAction.TRIAJE_REALIZADO);
        assertThat(evento.resourceType()).isEqualTo("TRIAJE");
        assertThat(evento.userId()).isEqualTo(1L);
    }

    @Test
    void evaluateAndSaveTriage_cutoffEmergency_auditsEmergencyAndRealizado() {
        CreateTriageRequest request = new CreateTriageRequest(
                List.of(new SymptomItemRequest("DOLOR_TORACICO_OPRESIVO", BigDecimal.valueOf(1.0), 8)),
                "Dolor en el pecho"
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(pacienteMock));
        when(triageEngineFactory.getEngine()).thenReturn(motorTriaje);

        TriageResult resultadoEmergencia = new TriageResult(
                PriorityLevel.I,
                SuggestedRoute.URGENCIAS,
                true,
                "v1-prototipo",
                "Llama al 123 o acude a urgencias de inmediato.",
                "Aviso de prototipo",
                List.of("DOLOR_TORACICO_OPRESIVO")
        );
        when(motorTriaje.evaluate(any())).thenReturn(resultadoEmergencia);
        when(triageRepository.getSymptomCodeToIdMap()).thenReturn(Map.of("DOLOR_TORACICO_OPRESIVO", 1L));
        when(triageRepository.saveTriage(any(Triage.class))).thenReturn(51L);

        TriageResponse responseMock = new TriageResponse(
                "triaje-uuid-emergencia", "pac-uuid-1", "I", "URGENCIAS", true, "v1-prototipo",
                "Llama al 123 o acude a urgencias de inmediato.", "Aviso",
                List.of("DOLOR_TORACICO_OPRESIVO"),
                List.of(new SymptomItemResponse("DOLOR_TORACICO_OPRESIVO", "Dolor torácico", BigDecimal.valueOf(1.0), 8, true)),
                "Dolor en el pecho", AHORA
        );
        when(triageRepository.findByPublicId(anyString())).thenReturn(Optional.of(responseMock));

        TriageResponse res = triageService.evaluateAndSaveTriage(request, USUARIO_PUBLIC_ID, IP_ORIGEN);

        assertThat(res).isNotNull();
        assertThat(res.isEmergency()).isTrue();
        assertThat(res.priorityLevel()).isEqualTo("I");
        assertThat(res.suggestedRoute()).isEqualTo("URGENCIAS");

        // Verifica ambas auditorías: TRIAJE_EMERGENCIA y TRIAJE_REALIZADO
        ArgumentCaptor<AuditEvent> audCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, times(2)).audit(audCaptor.capture());
        List<AuditEvent> eventos = audCaptor.getAllValues();
        assertThat(eventos.get(0).action()).isEqualTo(AuditableAction.TRIAJE_EMERGENCIA);
        assertThat(eventos.get(1).action()).isEqualTo(AuditableAction.TRIAJE_REALIZADO);
    }

    @Test
    void evaluateAndSaveTriage_symptomDuplicate_throwsInvalidDataException() {
        CreateTriageRequest request = new CreateTriageRequest(
                List.of(
                        new SymptomItemRequest("FIEBRE", BigDecimal.valueOf(2.0), 5),
                        new SymptomItemRequest("FIEBRE", BigDecimal.valueOf(4.0), 6)
                ),
                null
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(pacienteMock));

        assertThatThrownBy(() -> triageService.evaluateAndSaveTriage(request, USUARIO_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Síntoma duplicado");

        verify(triageRepository, never()).saveTriage(any());
    }

    @Test
    void evaluateAndSaveTriage_userNotIsPatient_throwsUnauthorizedAccessException() {
        CreateTriageRequest request = new CreateTriageRequest(
                List.of(new SymptomItemRequest("FIEBRE", BigDecimal.valueOf(2.0), 5)),
                null
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> triageService.evaluateAndSaveTriage(request, USUARIO_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Solo pacientes registrados pueden realizar el triaje");

        verify(triageRepository, never()).saveTriage(any());
    }

    @Test
    void evaluateAndSaveTriage_userInexistente_throwsResourceNotFoundException() {
        CreateTriageRequest request = new CreateTriageRequest(
                List.of(new SymptomItemRequest("FIEBRE", BigDecimal.valueOf(2.0), 5)),
                null
        );

        when(userRepository.findByPublicId("usr-inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> triageService.evaluateAndSaveTriage(request, "usr-inexistente", IP_ORIGEN))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    @Test
    void evaluateAndSaveTriage_symptomDesconocidoInCatalog_throwsInvalidDataException() {
        CreateTriageRequest request = new CreateTriageRequest(
                List.of(new SymptomItemRequest("SINTOMA_INEXISTENTE", BigDecimal.valueOf(2.0), 5)),
                null
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(pacienteMock));
        when(triageEngineFactory.getEngine()).thenReturn(motorTriaje);
        when(motorTriaje.evaluate(any())).thenReturn(new TriageResult(
                PriorityLevel.III, SuggestedRoute.CITA_PRESENCIAL, false, "v1", "msg", "aviso", List.of()
        ));
        when(triageRepository.getSymptomCodeToIdMap()).thenReturn(Map.of("FIEBRE", 1L));

        assertThatThrownBy(() -> triageService.evaluateAndSaveTriage(request, USUARIO_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Síntoma desconocido");
    }

    @Test
    void getByPublicId_ownPatient_returnsTriage() {
        String triajePublicId = "triaje-uuid-1";
        TriageResponse responseMock = new TriageResponse(
                triajePublicId, "pac-uuid-1", "III", "CITA_PRESENCIAL", false, "v1-prototipo",
                "Mensaje", "Aviso", List.of(), List.of(), null, AHORA
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(triageRepository.findByPublicId(triajePublicId)).thenReturn(Optional.of(responseMock));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(pacienteMock));

        TriageResponse res = triageService.getByPublicId(
                triajePublicId,
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE"))
        );

        assertThat(res).isNotNull();
        assertThat(res.publicId()).isEqualTo(triajePublicId);
    }

    @Test
    void getByPublicId_otherPatient_throwsUnauthorizedAccessException() {
        String triajePublicId = "triaje-uuid-ajeno";
        TriageResponse responseMock = new TriageResponse(
                triajePublicId, "pac-uuid-OTRO", "III", "CITA_PRESENCIAL", false, "v1-prototipo",
                "Mensaje", "Aviso", List.of(), List.of(), null, AHORA
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(triageRepository.findByPublicId(triajePublicId)).thenReturn(Optional.of(responseMock));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(pacienteMock));

        assertThatThrownBy(() -> triageService.getByPublicId(
                triajePublicId,
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE"))
        ))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("No tiene autorizacion para acceder al triaje de otro paciente");
    }

    @Test
    void getByPublicId_administrator_throwsUnauthorizedAccessException() {
        String triajePublicId = "triaje-uuid-1";
        TriageResponse responseMock = new TriageResponse(
                triajePublicId, "pac-uuid-1", "III", "CITA_PRESENCIAL", false, "v1-prototipo",
                "Mensaje", "Aviso", List.of(), List.of(), null, AHORA
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(triageRepository.findByPublicId(triajePublicId)).thenReturn(Optional.of(responseMock));

        assertThatThrownBy(() -> triageService.getByPublicId(
                triajePublicId,
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))
        ))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("El personal administrativo no tiene acceso a informacion clinica");
    }

    @Test
    void getByPublicId_professional_accessAllowed() {
        String triajePublicId = "triaje-uuid-1";
        TriageResponse responseMock = new TriageResponse(
                triajePublicId, "pac-uuid-1", "III", "CITA_PRESENCIAL", false, "v1-prototipo",
                "Mensaje", "Aviso", List.of(), List.of(), null, AHORA
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(triageRepository.findByPublicId(triajePublicId)).thenReturn(Optional.of(responseMock));

        TriageResponse res = triageService.getByPublicId(
                triajePublicId,
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"))
        );

        assertThat(res).isNotNull();
        assertThat(res.publicId()).isEqualTo(triajePublicId);
    }

    @Test
    void getCatalogSymptoms_returnsCatalogOfRepository() {
        List<SymptomCatalogResponse> catalogoMock = List.of(
                new SymptomCatalogResponse("uuid-1", "FIEBRE", "Fiebre", "GENERAL", false)
        );
        when(triageRepository.listActiveSymptomCatalog()).thenReturn(catalogoMock);

        List<SymptomCatalogResponse> resultado = triageService.getSymptomCatalog();
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).code()).isEqualTo("FIEBRE");
    }

    @Test
    void getByPublicId_withAccessClinical_professionalWithoutRelationshipLanza403() {
        ClinicalAccessService accesoClinicoMock = org.mockito.Mockito.mock(ClinicalAccessService.class);
        TriageService servicioConAcl = new TriageService(
                triageRepository,
                userRepository,
                patientRepository,
                triageEngineFactory,
                auditService,
                accesoClinicoMock
        );

        String triajePublicId = "triaje-uuid-acl";
        TriageResponse responseMock = new TriageResponse(
                triajePublicId, "pac-uuid-1", "III", "CITA_PRESENCIAL", false, "v1-prototipo",
                "Mensaje", "Aviso", List.of(), List.of(), null, AHORA
        );

        when(userRepository.findByPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(triageRepository.findByPublicId(triajePublicId)).thenReturn(Optional.of(responseMock));
        org.mockito.Mockito.doThrow(new UnauthorizedAccessException("No existe una relacion asistencial activa con el paciente."))
                .when(accesoClinicoMock).validateClinicalHistoryAccess(eq(usuarioMock.publicId()), eq("pac-uuid-1"), any());

        assertThatThrownBy(() -> servicioConAcl.getByPublicId(
                triajePublicId,
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"))
        ))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("No existe una relacion asistencial activa");
    }
}

