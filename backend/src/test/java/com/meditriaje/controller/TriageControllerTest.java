package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.triage.SymptomCatalogResponse;
import com.meditriaje.dto.triage.CreateTriageRequest;
import com.meditriaje.dto.triage.SymptomItemRequest;
import com.meditriaje.dto.triage.SymptomItemResponse;
import com.meditriaje.dto.triage.TriageResponse;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.TriageService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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

@WebMvcTest(controllers = TriageController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class TriageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TriageService triajeService;

    @MockBean
    private JwtService jwtService;

    private static final String TOKEN_PACIENTE = "token.paciente.valido";
    private static final String TOKEN_PROFESIONAL = "token.profesional.valido";
    private static final String TOKEN_ADMIN = "token.admin.valido";

    private static final String PACIENTE_UUID = "paciente-usr-uuid";
    private static final String PROFESIONAL_UUID = "profesional-usr-uuid";
    private static final String ADMIN_UUID = "admin-usr-uuid";

    @BeforeEach
    void setUpTokens() {
        when(jwtService.isValid(TOKEN_PACIENTE)).thenReturn(true);
        when(jwtService.extractPublicId(TOKEN_PACIENTE)).thenReturn(PACIENTE_UUID);
        when(jwtService.extractRoles(TOKEN_PACIENTE)).thenReturn(List.of("ROLE_PACIENTE"));

        when(jwtService.isValid(TOKEN_PROFESIONAL)).thenReturn(true);
        when(jwtService.extractPublicId(TOKEN_PROFESIONAL)).thenReturn(PROFESIONAL_UUID);
        when(jwtService.extractRoles(TOKEN_PROFESIONAL)).thenReturn(List.of("ROLE_PROFESIONAL"));

        when(jwtService.isValid(TOKEN_ADMIN)).thenReturn(true);
        when(jwtService.extractPublicId(TOKEN_ADMIN)).thenReturn(ADMIN_UUID);
        when(jwtService.extractRoles(TOKEN_ADMIN)).thenReturn(List.of("ROLE_ADMINISTRADOR"));
    }

    @Test
    void postTriage_authenticatedPatient_returns201CreatedWithLocation() throws Exception {
        CreateTriageRequest request = new CreateTriageRequest(
                List.of(new SymptomItemRequest("FIEBRE", BigDecimal.valueOf(12.0), 3)),
                "Observacion leve"
        );

        TriageResponse mockResponse = new TriageResponse(
                "triaje-uuid-1", "pac-uuid-1", "IV", "CITA_TELEMEDICINA", false, "v1-prototipo",
                "Se sugiere una cita de telemedicina.", "Aviso", List.of(),
                List.of(new SymptomItemResponse("FIEBRE", "Fiebre", BigDecimal.valueOf(12.0), 3, false)),
                "Observacion leve", Instant.now()
        );

        when(triajeService.evaluateAndSaveTriage(any(), eq(PACIENTE_UUID), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/triage")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/triage/triaje-uuid-1"))
                .andExpect(jsonPath("$.publicId").value("triaje-uuid-1"))
                .andExpect(jsonPath("$.nivelPrioridad").value("IV"))
                .andExpect(jsonPath("$.rutaSugerida").value("CITA_TELEMEDICINA"))
                .andExpect(jsonPath("$.esEmergencia").value(false));
    }

    @Test
    void postTriage_authenticatedProfessional_returns403Forbidden() throws Exception {
        CreateTriageRequest request = new CreateTriageRequest(
                List.of(new SymptomItemRequest("FIEBRE", BigDecimal.valueOf(12.0), 3)),
                null
        );

        mockMvc.perform(post("/api/v1/triage")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void postTriage_authenticatedAdmin_returns403Forbidden() throws Exception {
        CreateTriageRequest request = new CreateTriageRequest(
                List.of(new SymptomItemRequest("FIEBRE", BigDecimal.valueOf(12.0), 3)),
                null
        );

        mockMvc.perform(post("/api/v1/triage")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void postTriage_withoutSession_returns401Unauthorized() throws Exception {
        CreateTriageRequest request = new CreateTriageRequest(
                List.of(new SymptomItemRequest("FIEBRE", BigDecimal.valueOf(12.0), 3)),
                null
        );

        mockMvc.perform(post("/api/v1/triage")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postTriage_invalidData_symptomsEmpty_returns400BadRequest() throws Exception {
        String bodyInvalido = """
            {
              "sintomas": []
            }
            """;

        mockMvc.perform(post("/api/v1/triage")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyInvalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    void postTriage_invalidData_intensityGreater10_returns400BadRequest() throws Exception {
        String bodyInvalido = """
            {
              "sintomas": [
                {
                  "codigo": "FIEBRE",
                  "duracionHoras": 2.0,
                  "intensidad": 15
                }
              ]
            }
            """;

        mockMvc.perform(post("/api/v1/triage")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyInvalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    void getTriage_ownPatient_returns200Ok() throws Exception {
        String triajePublicId = "triaje-uuid-1";
        TriageResponse mockResponse = new TriageResponse(
                triajePublicId, "pac-uuid-1", "IV", "CITA_TELEMEDICINA", false, "v1-prototipo",
                "Se sugiere una cita de telemedicina.", "Aviso", List.of(),
                List.of(new SymptomItemResponse("FIEBRE", "Fiebre", BigDecimal.valueOf(12.0), 3, false)),
                null, Instant.now()
        );

        when(triajeService.getByPublicId(eq(triajePublicId), eq(PACIENTE_UUID), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/triage/{publicId}", triajePublicId)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value(triajePublicId))
                .andExpect(jsonPath("$.nivelPrioridad").value("IV"));
    }

    @Test
    void getTriage_otherPatient_returns403Forbidden() throws Exception {
        String triajePublicId = "triaje-uuid-ajeno";

        when(triajeService.getByPublicId(eq(triajePublicId), eq(PACIENTE_UUID), any()))
                .thenThrow(new UnauthorizedAccessException("No tiene autorizacion para acceder al triaje de otro paciente."));

        mockMvc.perform(get("/api/v1/triage/{publicId}", triajePublicId)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_NO_AUTORIZADO"));
    }

    @Test
    void getTriage_withoutSession_returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/triage/{publicId}", "triaje-uuid-1")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSymptoms_authenticated_returns200OkWithCatalog() throws Exception {
        List<SymptomCatalogResponse> catalogoMock = List.of(
                new SymptomCatalogResponse("sintoma-uuid-1", "DOLOR_TORACICO_OPRESIVO", "Dolor torácico opresivo", "CARDIOVASCULAR", true),
                new SymptomCatalogResponse("sintoma-uuid-2", "FIEBRE", "Fiebre", "GENERAL", false)
        );

        when(triajeService.getSymptomCatalog()).thenReturn(catalogoMock);

        mockMvc.perform(get("/api/v1/triage/symptoms")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].codigo").value("DOLOR_TORACICO_OPRESIVO"))
                .andExpect(jsonPath("$[0].esAlarma").value(true))
                .andExpect(jsonPath("$[1].codigo").value("FIEBRE"))
                .andExpect(jsonPath("$[1].esAlarma").value(false));
    }
}
