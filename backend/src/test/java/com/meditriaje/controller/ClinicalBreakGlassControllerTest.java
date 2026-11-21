package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.clinical.BreakGlassAccessResponse;
import com.meditriaje.dto.clinical.ActivateBreakGlassRequest;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.BreakGlassService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

@WebMvcTest(controllers = ClinicalBreakGlassController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class ClinicalBreakGlassControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BreakGlassService breakGlassService;

    @MockBean
    private JwtService jwtService;

    private final String tokenProfesional = "token.profesional.valido";
    private final String tokenPaciente = "token.paciente.valido";
    private final String tokenAdmin = "token.admin.valido";

    private void mockAuth(String token, String publicId, String role) {
        when(jwtService.isValid(token)).thenReturn(true);
        when(jwtService.extractPublicId(token)).thenReturn(publicId);
        when(jwtService.extractRoles(token)).thenReturn(List.of(role));
    }

    @Test
    @DisplayName("POST /break-glass: 201 Created para ROLE_PROFESIONAL con cabecera Location")
    void activateBreakGlass_professional_201Created() throws Exception {
        mockAuth(tokenProfesional, "u-prof-1", "ROLE_PROFESIONAL");

        ActivateBreakGlassRequest request = new ActivateBreakGlassRequest(
                "pac-1234",
                "Paciente en estado de shock severo requiere consulta urgente de historial clinico."
        );

        Instant ahora = Instant.now();
        Instant exp = ahora.plus(24, ChronoUnit.HOURS);
        BreakGlassAccessResponse response = new BreakGlassAccessResponse(
                "bg-uuid-1",
                "prof-uuid-1",
                "Dr. Roberto Gomez",
                "pac-1234",
                "Carlos Sanchez",
                request.reason(),
                exp,
                ahora,
                true
        );

        when(breakGlassService.activateBreakGlass(eq(request), eq("u-prof-1"), anyString()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/clinical/break-glass")
                        .cookie(new Cookie("access_token", tokenProfesional))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/clinical/break-glass/bg-uuid-1"))
                .andExpect(jsonPath("$.publicId").value("bg-uuid-1"))
                .andExpect(jsonPath("$.pacienteNombre").value("Carlos Sanchez"))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    @DisplayName("POST /break-glass: 403 Forbidden para ROLE_PACIENTE")
    void activateBreakGlass_patient_403Forbidden() throws Exception {
        mockAuth(tokenPaciente, "u-pac-1", "ROLE_PACIENTE");

        ActivateBreakGlassRequest request = new ActivateBreakGlassRequest(
                "pac-1234",
                "Paciente en estado de shock severo requiere consulta urgente de historial clinico."
        );

        mockMvc.perform(post("/api/v1/clinical/break-glass")
                        .cookie(new Cookie("access_token", tokenPaciente))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /break-glass: 403 Forbidden para ROLE_ADMINISTRADOR")
    void activarBreakGlass_admin_403Forbidden() throws Exception {
        mockAuth(tokenAdmin, "u-admin-1", "ROLE_ADMINISTRADOR");

        ActivateBreakGlassRequest request = new ActivateBreakGlassRequest(
                "pac-1234",
                "Paciente en estado de shock severo requiere consulta urgente de historial clinico."
        );

        mockMvc.perform(post("/api/v1/clinical/break-glass")
                        .cookie(new Cookie("access_token", tokenAdmin))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /break-glass: 401 Unauthorized sin sesión")
    void activarBreakGlass_sinSesion_401Unauthorized() throws Exception {
        ActivateBreakGlassRequest request = new ActivateBreakGlassRequest(
                "pac-1234",
                "Paciente en estado de shock severo requiere consulta urgente de historial clinico."
        );

        mockMvc.perform(post("/api/v1/clinical/break-glass")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /break-glass: 400 Bad Request cuando el motivo es menor a 20 caracteres")
    void activateBreakGlass_shortReason_400BadRequest() throws Exception {
        mockAuth(tokenProfesional, "u-prof-1", "ROLE_PROFESIONAL");

        ActivateBreakGlassRequest request = new ActivateBreakGlassRequest(
                "pac-1234",
                "Muy corto"
        );

        mockMvc.perform(post("/api/v1/clinical/break-glass")
                        .cookie(new Cookie("access_token", tokenProfesional))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /break-glass/active: 200 OK para ROLE_PROFESIONAL")
    void listMyAccessesActive_professional_200Ok() throws Exception {
        mockAuth(tokenProfesional, "u-prof-1", "ROLE_PROFESIONAL");

        BreakGlassAccessResponse item = new BreakGlassAccessResponse(
                "bg-1",
                "prof-1",
                "Dr. Roberto Gomez",
                "pac-1",
                "Carlos Sanchez",
                "Justificacion medica urgente de al menos veinte caracteres",
                Instant.now().plus(12, ChronoUnit.HOURS),
                Instant.now(),
                true
        );

        when(breakGlassService.listMyActiveAccesses("u-prof-1")).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/clinical/break-glass/active")
                        .cookie(new Cookie("access_token", tokenProfesional)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].publicId").value("bg-1"))
                .andExpect(jsonPath("$[0].pacienteNombre").value("Carlos Sanchez"));
    }

    @Test
    @DisplayName("GET /break-glass/{publicId}: 200 OK para ROLE_PROFESIONAL")
    void getByPublicId_professional_200Ok() throws Exception {
        mockAuth(tokenProfesional, "u-prof-1", "ROLE_PROFESIONAL");

        BreakGlassAccessResponse item = new BreakGlassAccessResponse(
                "bg-99",
                "prof-1",
                "Dr. Roberto Gomez",
                "pac-1",
                "Carlos Sanchez",
                "Justificacion medica urgente de al menos veinte caracteres",
                Instant.now().plus(12, ChronoUnit.HOURS),
                Instant.now(),
                true
        );

        when(breakGlassService.getByPublicId("bg-99", "u-prof-1")).thenReturn(item);

        mockMvc.perform(get("/api/v1/clinical/break-glass/bg-99")
                        .cookie(new Cookie("access_token", tokenProfesional)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("bg-99"))
                .andExpect(jsonPath("$.activo").value(true));
    }
}
