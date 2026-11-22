package com.meditriaje.controller;

import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.followup.CreateFollowUpRequest;
import com.meditriaje.dto.followup.FollowUpResponse;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.FollowUpService;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FollowUpController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class FollowUpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FollowUpService followUpService;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("POST /api/v1/attentions/{id}/follow-ups - Profesional: 201 Created con cabecera Location")
    void prescribeFollowUp_professional_returns201() throws Exception {
        String tokenProf = "valid.token.prof";
        when(jwtService.isValid(tokenProf)).thenReturn(true);
        when(jwtService.extractPublicId(tokenProf)).thenReturn("uuid-prof-1");
        when(jwtService.extractRoles(tokenProf)).thenReturn(List.of("ROLE_PROFESIONAL"));

        FollowUpResponse resp = new FollowUpResponse(
                "seg-uuid-1", "at-1", "pac-1", "Carlos Gomez",
                "prof-1", "Dra. Laura Perez", "Medicina General",
                "CONTROL_MEDICO", "Control de presión en 7 días",
                LocalDate.now().plusDays(7), "PENDIENTE",
                null, null, Instant.now(), Instant.now()
        );

        when(followUpService.prescribeFollowUp(eq("at-1"), any(CreateFollowUpRequest.class), eq("uuid-prof-1"), anyString()))
                .thenReturn(resp);

        String jsonBody = """
                {
                    "tipo": "CONTROL_MEDICO",
                    "indicaciones": "Control de presión en 7 días",
                    "fechaSugeridaControl": "2026-10-15"
                }
                """;

        mockMvc.perform(post("/api/v1/attentions/at-1/follow-ups")
                        .cookie(new Cookie("access_token", tokenProf))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/follow-ups/seg-uuid-1"))
                .andExpect(jsonPath("$.publicId").value("seg-uuid-1"))
                .andExpect(jsonPath("$.tipo").value("CONTROL_MEDICO"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    @DisplayName("POST /api/v1/attentions/{id}/follow-ups - Paciente: 403 Forbidden")
    void prescribeFollowUp_patient_returns403() throws Exception {
        String tokenPac = "valid.token.pac";
        when(jwtService.isValid(tokenPac)).thenReturn(true);
        when(jwtService.extractPublicId(tokenPac)).thenReturn("uuid-pac-1");
        when(jwtService.extractRoles(tokenPac)).thenReturn(List.of("ROLE_PACIENTE"));

        String jsonBody = """
                {
                    "tipo": "CONTROL_MEDICO",
                    "indicaciones": "Indicaciones",
                    "fechaSugeridaControl": "2026-10-15"
                }
                """;

        mockMvc.perform(post("/api/v1/attentions/at-1/follow-ups")
                        .cookie(new Cookie("access_token", tokenPac))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/attentions/{id}/follow-ups - Administrador: 403 Forbidden")
    void prescribeFollowUp_admin_returns403() throws Exception {
        String tokenAdmin = "valid.token.admin";
        when(jwtService.isValid(tokenAdmin)).thenReturn(true);
        when(jwtService.extractPublicId(tokenAdmin)).thenReturn("uuid-admin-1");
        when(jwtService.extractRoles(tokenAdmin)).thenReturn(List.of("ROLE_ADMINISTRADOR"));

        String jsonBody = """
                {
                    "tipo": "CONTROL_MEDICO",
                    "indicaciones": "Indicaciones",
                    "fechaSugeridaControl": "2026-10-15"
                }
                """;

        mockMvc.perform(post("/api/v1/attentions/at-1/follow-ups")
                        .cookie(new Cookie("access_token", tokenAdmin))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/attentions/{id}/follow-ups - Sin autenticación: 401 Unauthorized")
    void prescribeFollowUp_withoutAuth_returns401() throws Exception {
        String jsonBody = """
                {
                    "tipo": "CONTROL_MEDICO",
                    "indicaciones": "Indicaciones"
                }
                """;

        mockMvc.perform(post("/api/v1/attentions/at-1/follow-ups")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/attentions/{id}/follow-ups - Usuario autenticado: 200 OK")
    void listByAttention_authenticated_returns200() throws Exception {
        String token = "valid.token";
        when(jwtService.isValid(token)).thenReturn(true);
        when(jwtService.extractPublicId(token)).thenReturn("uuid-user-1");
        when(jwtService.extractRoles(token)).thenReturn(List.of("ROLE_PROFESIONAL"));

        when(followUpService.listByAttention(eq("at-1"), eq("uuid-user-1"), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/attentions/at-1/follow-ups")
                        .cookie(new Cookie("access_token", token)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/follow-ups/{id} - Usuario autenticado: 200 OK")
    void getByPublicId_authenticated_returns200() throws Exception {
        String token = "valid.token";
        when(jwtService.isValid(token)).thenReturn(true);
        when(jwtService.extractPublicId(token)).thenReturn("uuid-user-1");
        when(jwtService.extractRoles(token)).thenReturn(List.of("ROLE_PACIENTE"));

        FollowUpResponse resp = new FollowUpResponse(
                "seg-1", "at-1", "pac-1", "Carlos Gomez",
                "prof-1", "Dra. Laura Perez", "Medicina General",
                "CONTROL_MEDICO", "Control de presión",
                null, "PENDIENTE", null, null, Instant.now(), Instant.now()
        );

        when(followUpService.getByPublicId(eq("seg-1"), eq("uuid-user-1"), any()))
                .thenReturn(resp);

        mockMvc.perform(get("/api/v1/follow-ups/seg-1")
                        .cookie(new Cookie("access_token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("seg-1"));
    }

    @Test
    @DisplayName("PATCH /api/v1/follow-ups/{id}/cancel - Profesional: 200 OK")
    void cancelFollowUp_professional_returns200() throws Exception {
        String tokenProf = "valid.token.prof";
        when(jwtService.isValid(tokenProf)).thenReturn(true);
        when(jwtService.extractPublicId(tokenProf)).thenReturn("uuid-prof-1");
        when(jwtService.extractRoles(tokenProf)).thenReturn(List.of("ROLE_PROFESIONAL"));

        FollowUpResponse cancelado = new FollowUpResponse(
                "seg-1", "at-1", "pac-1", "Carlos Gomez",
                "prof-1", "Dra. Laura Perez", "Medicina General",
                "CONTROL_MEDICO", "Control cancelado",
                null, "CANCELADO", null, null, Instant.now(), Instant.now()
        );

        when(followUpService.cancelFollowUp(eq("seg-1"), eq("uuid-prof-1"), any()))
                .thenReturn(cancelado);

        mockMvc.perform(patch("/api/v1/follow-ups/seg-1/cancel")
                        .cookie(new Cookie("access_token", tokenProf))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));
    }
}
