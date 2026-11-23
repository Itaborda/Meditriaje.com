package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.operational.*;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.OperationalAnalyticsService;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OperationalController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class OperationalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OperationalAnalyticsService analyticsService;

    @MockBean
    private JwtService jwtService;

    private static final String TOKEN_ADMIN = "token.admin.valido";
    private static final String TOKEN_ENFERMERIA = "token.enfermeria.valido";

    private final Instant now = Instant.parse("2026-03-30T10:00:00Z");

    @BeforeEach
    void setUp() {
        when(jwtService.isValid(TOKEN_ADMIN)).thenReturn(true);
        when(jwtService.extractPublicId(TOKEN_ADMIN)).thenReturn("usr-admin-uuid");
        when(jwtService.extractRoles(TOKEN_ADMIN)).thenReturn(List.of("ROLE_ADMINISTRADOR"));

        when(jwtService.isValid(TOKEN_ENFERMERIA)).thenReturn(true);
        when(jwtService.extractPublicId(TOKEN_ENFERMERIA)).thenReturn("usr-enf-uuid");
        when(jwtService.extractRoles(TOKEN_ENFERMERIA)).thenReturn(List.of("ROLE_ENFERMERIA"));
    }

    private Cookie cookie(String token) {
        return new Cookie("access_token", token);
    }

    @Test
    @DisplayName("SEC-01: Petición anónima al dashboard hospitalario retorna 401 Unauthorized")
    void dashboard_sinSesion_retorna401() throws Exception {
        mockMvc.perform(get("/api/v1/operational/dashboard")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("O01: Administrador o enfermería consulta dashboard hospitalario y retorna 200 OK")
    void dashboard_conSesion_retorna200Ok() throws Exception {
        HospitalDashboardResponse resp = new HospitalDashboardResponse(
                "sede-1", "Sede Central", 50, 10, 38, 2, 0, 76.0, 5,
                Map.of("II", 2L, "III", 3L), Map.of("II", 20.0), List.of()
        );
        when(analyticsService.getHospitalDashboard(anyString(), anyString())).thenReturn(resp);

        mockMvc.perform(get("/api/v1/operational/dashboard")
                        .cookie(cookie(TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sedeNombre").value("Sede Central"))
                .andExpect(jsonPath("$.tasaOcupacionPorcentaje").value(76.0))
                .andExpect(jsonPath("$.camasOcupadas").value(38));
    }

    @Test
    @DisplayName("O02: Reconocer alerta operativa retorna 204 No Content")
    void acknowledgeAlert_returns204NotContent() throws Exception {
        AcknowledgeAlertRequest req = new AcknowledgeAlertRequest("Atendido por supervisor");

        mockMvc.perform(post("/api/v1/operational/alerts/alerta-1/acknowledge")
                        .cookie(cookie(TOKEN_ENFERMERIA))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("O03: Generar código QR seguro de seguimiento retorna 201 Created")
    void generateTrackingQr_returns201Created() throws Exception {
        TrackingQrResponse resp = new TrackingQrResponse(
                "qr-1", "ep-1", "NN-001", "QROP-ABCD", "/track/QROP-ABCD",
                "SALA OBSERVACION", "EN_VALORACION", "II", "ACTIVO", now, now.plusSeconds(86400)
        );
        when(analyticsService.generateTrackingQr(eq("ep-1"), anyString(), anyString())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/operational/episodes/ep-1/tracking-qr")
                        .cookie(cookie(TOKEN_ENFERMERIA))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenQr").value("QROP-ABCD"))
                .andExpect(jsonPath("$.ubicacionActual").value("SALA OBSERVACION"));
    }

    @Test
    @DisplayName("O03: Consulta pública de QR de seguimiento retorna 200 OK con ubicación sin PHI")
    void getTrackingQr_returns200Ok() throws Exception {
        TrackingQrResponse resp = new TrackingQrResponse(
                "qr-1", "ep-1", "NN-001", "QROP-ABCD", "/track/QROP-ABCD",
                "PABELLON SAN ROQUE", "HOSPITALIZADO", "III", "ACTIVO", now, now.plusSeconds(86400)
        );
        when(analyticsService.getByQrToken(eq("QROP-ABCD"), anyString())).thenReturn(resp);

        mockMvc.perform(get("/api/v1/operational/tracking-qr/QROP-ABCD")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoIdentidadProvisional").value("NN-001"))
                .andExpect(jsonPath("$.ubicacionActual").value("PABELLON SAN ROQUE"));
    }

    @Test
    @DisplayName("O02: Evaluar alertas operativas bajo demanda retorna 200 OK con alertas activas")
    void evaluateAlerts_nursing_returns200() throws Exception {
        OperationalAlertResponse alerta = new OperationalAlertResponse(
                "alt-1", "sede-1", "Sede Central", "SATURACION_CAMAS", "CRITICA", "Ocupación crítica", "ACTIVA", null, null, null, now
        );
        when(analyticsService.evaluateAndListActiveAlerts(eq("usr-enf-uuid"), anyString()))
                .thenReturn(List.of(alerta));

        mockMvc.perform(post("/api/v1/operational/alerts/evaluate")
                        .cookie(cookie(TOKEN_ENFERMERIA))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipoAlerta").value("SATURACION_CAMAS"));
    }
}
