package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.emergency.CalculateTriageLevelRequest;
import com.meditriaje.dto.emergency.EmergencyEpisodeResponse;
import com.meditriaje.dto.emergency.EmergencyPatientLookupResponse;
import com.meditriaje.dto.emergency.EmergencyQueueItemResponse;
import com.meditriaje.dto.emergency.ReconcileIdentityRequest;
import com.meditriaje.dto.emergency.RegisterEmergencyAdmissionRequest;
import com.meditriaje.dto.emergency.RegisterTriageAssessmentRequest;
import com.meditriaje.dto.emergency.TriageAssessmentResponse;
import com.meditriaje.dto.emergency.TriageCalculationResponse;
import com.meditriaje.dto.triage.SymptomItemRequest;
import com.meditriaje.dto.triage.SymptomItemResponse;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.EmergencyService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EmergencyController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class EmergencyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmergencyService emergencyService;

    @MockBean
    private JwtService jwtService;

    private static final String TOKEN_ENFERMERIA = "token.enfermeria.valido";
    private static final String TOKEN_PROFESIONAL = "token.profesional.valido";
    private static final String TOKEN_PACIENTE = "token.paciente.valido";
    private static final String TOKEN_ADMIN = "token.admin.valido";

    private static final String ENFERMERA_UUID = "usr-enf-uuid";
    private static final String PACIENTE_UUID = "usr-pac-uuid";
    private static final String ADMIN_UUID = "usr-adm-uuid";

    @BeforeEach
    void setUp() {
        when(jwtService.isValid(TOKEN_ENFERMERIA)).thenReturn(true);
        when(jwtService.extractPublicId(TOKEN_ENFERMERIA)).thenReturn(ENFERMERA_UUID);
        when(jwtService.extractRoles(TOKEN_ENFERMERIA)).thenReturn(List.of("ROLE_ENFERMERIA"));

        when(jwtService.isValid(TOKEN_PROFESIONAL)).thenReturn(true);
        when(jwtService.extractPublicId(TOKEN_PROFESIONAL)).thenReturn("usr-prof-uuid");
        when(jwtService.extractRoles(TOKEN_PROFESIONAL)).thenReturn(List.of("ROLE_PROFESIONAL"));

        when(jwtService.isValid(TOKEN_PACIENTE)).thenReturn(true);
        when(jwtService.extractPublicId(TOKEN_PACIENTE)).thenReturn(PACIENTE_UUID);
        when(jwtService.extractRoles(TOKEN_PACIENTE)).thenReturn(List.of("ROLE_PACIENTE"));

        when(jwtService.isValid(TOKEN_ADMIN)).thenReturn(true);
        when(jwtService.extractPublicId(TOKEN_ADMIN)).thenReturn(ADMIN_UUID);
        when(jwtService.extractRoles(TOKEN_ADMIN)).thenReturn(List.of("ROLE_ADMINISTRADOR"));
    }

    private Cookie cookie(String token) {
        return new Cookie("access_token", token);
    }

    @Test
    @DisplayName("SEC01: Petición anónima a admisiones retorna 401 Unauthorized")
    void registerAdmission_withoutSession_returns401() throws Exception {
        RegisterEmergencyAdmissionRequest req = new RegisterEmergencyAdmissionRequest(
                null, true, "ESPONTANEO", "Dolor fuerte", null, null, null, null, null, null, null
        );

        mockMvc.perform(post("/api/v1/emergency/admissions")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SEC02: Paciente intentando registrar admisión retorna 403 Forbidden")
    void registerAdmission_patient_returns403() throws Exception {
        RegisterEmergencyAdmissionRequest req = new RegisterEmergencyAdmissionRequest(
                null, true, "ESPONTANEO", "Dolor fuerte", null, null, null, null, null, null, null
        );

        mockMvc.perform(post("/api/v1/emergency/admissions")
                        .cookie(cookie(TOKEN_PACIENTE))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("U02: Enfermería registrando admisión presencial retorna 201 Created y Location")
    void registerAdmission_nursing_returns201() throws Exception {
        RegisterEmergencyAdmissionRequest req = new RegisterEmergencyAdmissionRequest(
                null, true, "AMBULANCIA", "Trauma", null, null, null, null, null, null, null
        );

        EmergencyEpisodeResponse respMock = new EmergencyEpisodeResponse(
                "ep-pub-123", null, null, "sede-pub", "Hospital", "URGENCIA",
                "REGISTRADO", true, "NN-20261010-ABCD", "PROVISIONAL", "AMBULANCIA",
                "Trauma", "PENDIENTE_VALORACION", null, Instant.now(), null, Instant.now()
        );
        when(emergencyService.registerEmergencyAdmission(any(), eq(ENFERMERA_UUID), anyString()))
                .thenReturn(respMock);

        mockMvc.perform(post("/api/v1/emergency/admissions")
                        .cookie(cookie(TOKEN_ENFERMERIA))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/emergency/admissions/ep-pub-123"))
                .andExpect(jsonPath("$.episodioPublicId").value("ep-pub-123"))
                .andExpect(jsonPath("$.codigoProvisional").value("NN-20261010-ABCD"));
    }

    @Test
    @DisplayName("U02: Consultar detalle de episodio vía /episodes/{id} retorna 200 OK")
    void getEpisodeDetail_viaEpisodesPath_returns200() throws Exception {
        EmergencyEpisodeResponse respMock = new EmergencyEpisodeResponse(
                "ep-pub-123", "pac-pub-1", "Juan Gomez", "sede-pub", "Hospital", "URGENCIA",
                "REGISTRADO", false, null, null, "ESPONTANEO",
                "Fiebre persistente", "PENDIENTE_VALORACION", null, Instant.now(), null, Instant.now()
        );
        when(emergencyService.getEpisodeDetail(eq("ep-pub-123"), eq(ENFERMERA_UUID), any()))
                .thenReturn(respMock);

        mockMvc.perform(get("/api/v1/emergency/episodes/ep-pub-123")
                        .cookie(cookie(TOKEN_ENFERMERIA))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.episodioPublicId").value("ep-pub-123"))
                .andExpect(jsonPath("$.pacienteNombreCompleto").value("Juan Gomez"));
    }

    @Test
    @DisplayName("U04: Previsualización del cálculo automático por síntomas retorna 200 OK para enfermería")
    void calculateTriageLevel_nursing_returns200() throws Exception {
        CalculateTriageLevelRequest req = new CalculateTriageLevelRequest(
                List.of(new SymptomItemRequest("FIEBRE", new BigDecimal("6"), 8))
        );
        TriageCalculationResponse respMock = new TriageCalculationResponse(
                "II", "ATENCION_PRIORITARIA", false, "v1-prototipo", List.of(), "Se sugiere atención prioritaria."
        );
        when(emergencyService.calculateTriageLevel(any())).thenReturn(respMock);

        mockMvc.perform(post("/api/v1/emergency/triage-assessments/calculate")
                        .cookie(cookie(TOKEN_ENFERMERIA))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivel").value("II"))
                .andExpect(jsonPath("$.esEmergencia").value(false));
    }

    @Test
    @DisplayName("SEC04: Paciente intentando usar endpoint de cálculo de enfermería recibe 403 Forbidden")
    void calculateTriageLevel_patient_returns403() throws Exception {
        CalculateTriageLevelRequest req = new CalculateTriageLevelRequest(
                List.of(new SymptomItemRequest("FIEBRE", new BigDecimal("6"), 8))
        );

        mockMvc.perform(post("/api/v1/emergency/triage-assessments/calculate")
                        .cookie(cookie(TOKEN_PACIENTE))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("U04: Enfermería registrando valoración presencial con síntomas retorna 201 Created y nivel calculado")
    void registerAssessmentTriage_nursing_returns201() throws Exception {
        RegisterTriageAssessmentRequest req = new RegisterTriageAssessmentRequest(
                List.of(new SymptomItemRequest("FIEBRE", new BigDecimal("6"), 8)),
                "Dolor precordial", "Diaforesis", "130/80", 95, 20, 96, new BigDecimal("36.8"), 15, false, null
        );

        TriageAssessmentResponse respMock = new TriageAssessmentResponse(
                "val-pub-456", "ep-pub-123", 1, "II", "Dolor precordial",
                "Diaforesis", "130/80", 95, 20, 96, new BigDecimal("36.8"), 15,
                "Maria Perez", false, null, Instant.now(),
                List.of(new SymptomItemResponse("FIEBRE", "Fiebre", new BigDecimal("6"), 8, false))
        );
        when(emergencyService.recordTriageAssessment(eq("ep-pub-123"), any(), eq(ENFERMERA_UUID), any(), anyString()))
                .thenReturn(respMock);

        mockMvc.perform(post("/api/v1/emergency/episodes/ep-pub-123/triage-assessments")
                        .cookie(cookie(TOKEN_ENFERMERIA))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/emergency/episodes/ep-pub-123/triage-assessments/val-pub-456"))
                .andExpect(jsonPath("$.nivel").value("II"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.sintomas[0].codigo").value("FIEBRE"));
    }

    @Test
    @DisplayName("U04: Valoración sin síntomas retorna 400 Bad Request")
    void registerAssessmentTriage_withoutSymptoms_returns400() throws Exception {
        RegisterTriageAssessmentRequest req = new RegisterTriageAssessmentRequest(
                List.of(), "Consulta general", null, null, null, null, null, null, null, false, null
        );

        mockMvc.perform(post("/api/v1/emergency/episodes/ep-pub-123/triage-assessments")
                        .cookie(cookie(TOKEN_ENFERMERIA))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SEC03: Administrador intentando registrar triaje clínico presencial recibe 403 Forbidden")
    void registerAssessmentTriage_admin_returns403() throws Exception {
        RegisterTriageAssessmentRequest req = new RegisterTriageAssessmentRequest(
                List.of(new SymptomItemRequest("FIEBRE", new BigDecimal("4"), 5)),
                "Consulta", null, null, null, null, null, null, null, false, null
        );

        mockMvc.perform(post("/api/v1/emergency/episodes/ep-pub-123/triage-assessments")
                        .cookie(cookie(TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("U05: Consultar cola de urgencias retorna 200 OK con elementos priorizados")
    void getQueue_nursing_returns200() throws Exception {
        EmergencyQueueItemResponse item = new EmergencyQueueItemResponse(
                "ep-pub-123", null, "NN (NN-20261010-ABCD)", "NN-20261010-ABCD",
                "sede-pub", "Hospital", "URGENCIA", "EN_TRIAJE", "II", "Dolor", 10L, Instant.now(), null
        );
        when(emergencyService.listEmergencyQueue(any())).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/emergency/queue")
                        .cookie(cookie(TOKEN_ENFERMERIA))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].episodioPublicId").value("ep-pub-123"))
                .andExpect(jsonPath("$[0].nivelTriaje").value("II"));
    }

    @Test
    @DisplayName("U03: Reconciliar identidad provisional de paciente NN retorna 200 OK")
    void reconcileIdentity_nursing_returns200() throws Exception {
        ReconcileIdentityRequest req = new ReconcileIdentityRequest("pac-real-pub", "Documento verificado");

        EmergencyEpisodeResponse respMock = new EmergencyEpisodeResponse(
                "ep-pub-123", "pac-real-pub", "Juan Gomez", "sede-pub", "Hospital", "URGENCIA",
                "REGISTRADO", true, "NN-20261010-ABCD", "VINCULADA", "AMBULANCIA",
                "Trauma", "PENDIENTE_VALORACION", null, Instant.now(), null, Instant.now()
        );
        when(emergencyService.reconcileIdentity(eq("ep-pub-123"), any(), eq(ENFERMERA_UUID), anyString()))
                .thenReturn(respMock);

        mockMvc.perform(post("/api/v1/emergency/episodes/ep-pub-123/reconcile")
                        .cookie(cookie(TOKEN_ENFERMERIA))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identidadEstado").value("VINCULADA"))
                .andExpect(jsonPath("$.pacientePublicId").value("pac-real-pub"));
    }

    @Test
    @DisplayName("U03: Alias /reconcile-identity para reconciliar identidad NN retorna 200 OK")
    void reconcileIdentity_viaReconcileIdentityAlias_returns200() throws Exception {
        ReconcileIdentityRequest req = new ReconcileIdentityRequest("1134169348", "Documento verificado");

        EmergencyEpisodeResponse respMock = new EmergencyEpisodeResponse(
                "ep-pub-123", "pac-real-pub", "Juan Gomez", "sede-pub", "Hospital", "URGENCIA",
                "REGISTRADO", true, "NN-20261010-ABCD", "VINCULADA", "AMBULANCIA",
                "Trauma", "PENDIENTE_VALORACION", null, Instant.now(), null, Instant.now()
        );
        when(emergencyService.reconcileIdentity(eq("ep-pub-123"), any(), eq(ENFERMERA_UUID), anyString()))
                .thenReturn(respMock);

        mockMvc.perform(post("/api/v1/emergency/episodes/ep-pub-123/reconcile-identity")
                        .cookie(cookie(TOKEN_ENFERMERIA))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identidadEstado").value("VINCULADA"));
    }

    @Test
    @DisplayName("U02: Enfermería buscando paciente por documento recibe 200 OK")
    void searchPatients_nursing_returns200() throws Exception {
        EmergencyPatientLookupResponse lookup = new EmergencyPatientLookupResponse(
                "pac-pub-1", "CC", "1134169348", "Maria", "Lopez", "Maria Lopez",
                LocalDate.of(1992, 3, 15), "3007654321", true
        );
        when(emergencyService.searchPatientsByDocument("1134169348", "CC")).thenReturn(List.of(lookup));

        mockMvc.perform(get("/api/v1/emergency/patients/search")
                        .param("numeroDocumento", "1134169348")
                        .param("tipoDocumento", "CC")
                        .cookie(cookie(TOKEN_ENFERMERIA))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].numeroDocumento").value("1134169348"))
                .andExpect(jsonPath("$[0].tieneCuentaMediTriaje").value(true));
    }

    @Test
    @DisplayName("SEC04: Paciente intentando buscar pacientes por documento recibe 403 Forbidden")
    void searchPatients_patient_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/emergency/patients/search")
                        .param("numeroDocumento", "1134169348")
                        .cookie(cookie(TOKEN_PACIENTE))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isForbidden());
    }
}
