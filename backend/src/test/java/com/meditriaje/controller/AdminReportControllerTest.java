package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.controller.admin.AdminReportController;
import com.meditriaje.dto.report.DistributionItemDto;
import com.meditriaje.dto.report.BreakGlassMetricsDto;
import com.meditriaje.dto.report.AppointmentMetricsDto;
import com.meditriaje.dto.report.PharmacyMetricsDto;
import com.meditriaje.dto.report.TriageMetricsDto;
import com.meditriaje.dto.report.OperationalSummaryResponse;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.ReportService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminReportController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AdminReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReportService reporteService;

    @MockBean
    private JwtService jwtService;

    private static final String TOKEN_ADMIN = "token.admin.valido";
    private static final String TOKEN_PACIENTE = "token.paciente.valido";
    private static final String TOKEN_PROFESIONAL = "token.profesional.valido";
    private static final String ADMIN_UUID = "admin-uuid-1";

    @BeforeEach
    void setUpTokens() {
        when(jwtService.isValid(TOKEN_ADMIN)).thenReturn(true);
        when(jwtService.extractPublicId(TOKEN_ADMIN)).thenReturn(ADMIN_UUID);
        when(jwtService.extractRoles(TOKEN_ADMIN)).thenReturn(List.of("ROLE_ADMINISTRADOR"));

        when(jwtService.isValid(TOKEN_PACIENTE)).thenReturn(true);
        when(jwtService.extractPublicId(TOKEN_PACIENTE)).thenReturn("paciente-uuid-1");
        when(jwtService.extractRoles(TOKEN_PACIENTE)).thenReturn(List.of("ROLE_PACIENTE"));

        when(jwtService.isValid(TOKEN_PROFESIONAL)).thenReturn(true);
        when(jwtService.extractRoles(TOKEN_PROFESIONAL)).thenReturn(List.of("ROLE_PROFESIONAL"));
    }

    // =========================================================================
    // SEGURIDAD Y AUTORIZACIÓN (ADR-007, ADR-018)
    // =========================================================================

    @Test
    @DisplayName("GET /operational sin autenticación retorna 401 Unauthorized")
    void getOperational_sinAutenticacion_retorna401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/operational"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    @DisplayName("GET /operational con rol PACIENTE retorna 403 Forbidden")
    void getOperational_withPatientRole_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/operational")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    @DisplayName("GET /operational con rol PROFESIONAL retorna 403 Forbidden")
    void getOperational_withProfessionalRole_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/operational")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    // =========================================================================
    // CONSULTA RESUMEN OPERATIVO
    // =========================================================================

    @Test
    @DisplayName("GET /operational con rol ADMIN retorna 200 OK con métricas agregadas")
    void getOperational_withAdminRole_returns200Ok() throws Exception {
        OperationalSummaryResponse mockResponse = new OperationalSummaryResponse(
                null,
                null,
                new AppointmentMetricsDto(100L, 20L, 30L, 40L, 5L, 3L, 2L, 40.0, 8.0,
                        List.of(new DistributionItemDto("Medicina General", 60L)),
                        List.of(new DistributionItemDto("Sede Norte", 100L))),
                new TriageMetricsDto(50L, 5L, 10L, 20L, 10L, 5L, 5L, 10.0),
                new PharmacyMetricsDto(80L, 10L, 20L, 50L, 240L),
                new BreakGlassMetricsDto(2L, 0L, List.of(new DistributionItemDto("Urgencias", 2L))),
                Instant.now()
        );

        when(reporteService.getOperationalSummary(any(), any(), eq(ADMIN_UUID), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/admin/reports/operational")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citas.totalCitas").value(100))
                .andExpect(jsonPath("$.citas.tasaCumplimiento").value(40.0))
                .andExpect(jsonPath("$.triaje.totalTriajes").value(50))
                .andExpect(jsonPath("$.triaje.emergencias").value(5))
                .andExpect(jsonPath("$.farmacia.totalRecetas").value(80))
                .andExpect(jsonPath("$.breakGlass.totalActivaciones").value(2));
    }

    @Test
    @DisplayName("GET /operational con fechas invertidas retorna 400 Bad Request")
    void getOperational_withDatesInvertidas_returns400BadRequest() throws Exception {
        when(reporteService.getOperationalSummary(eq(LocalDate.of(2026, 12, 31)), eq(LocalDate.of(2026, 1, 1)), eq(ADMIN_UUID), anyString()))
                .thenThrow(new InvalidDataException("La fecha desde no puede ser posterior a la fecha hasta."));

        mockMvc.perform(get("/api/v1/admin/reports/operational")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .param("desde", "2026-12-31")
                        .param("hasta", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.mensaje").value("La fecha desde no puede ser posterior a la fecha hasta."));
    }

    // =========================================================================
    // CONSULTA REPORTES ESPECÍFICOS
    // =========================================================================

    @Test
    @DisplayName("GET /appointments con rol ADMIN retorna 200 OK")
    void getAppointments_withAdminRole_returns200Ok() throws Exception {
        AppointmentMetricsDto mockDto = new AppointmentMetricsDto(
                50L, 10L, 15L, 20L, 2L, 1L, 2L, 40.0, 6.0,
                List.of(new DistributionItemDto("Pediatría", 30L)),
                List.of(new DistributionItemDto("Sede Centro", 50L))
        );

        when(reporteService.getAppointmentsReport(any(), any(), eq(ADMIN_UUID), anyString()))
                .thenReturn(mockDto);

        mockMvc.perform(get("/api/v1/admin/reports/appointments")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .param("desde", "2026-10-01")
                        .param("hasta", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCitas").value(50))
                .andExpect(jsonPath("$.porEspecialidad[0].etiqueta").value("Pediatría"));
    }

    @Test
    @DisplayName("GET /appointments con rol PACIENTE retorna 403 Forbidden")
    void getAppointments_withPatientRole_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/appointments")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    @DisplayName("GET /triage con rol ADMIN retorna 200 OK")
    void getTriage_withAdminRole_returns200Ok() throws Exception {
        TriageMetricsDto mockDto = new TriageMetricsDto(40L, 2L, 8L, 15L, 10L, 5L, 2L, 5.0);

        when(reporteService.getTriageReport(any(), any(), eq(ADMIN_UUID), anyString()))
                .thenReturn(mockDto);

        mockMvc.perform(get("/api/v1/admin/reports/triage")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTriajes").value(40))
                .andExpect(jsonPath("$.emergencias").value(2))
                .andExpect(jsonPath("$.tasaEmergencia").value(5.0));
    }

    @Test
    @DisplayName("GET /triage con rol PROFESIONAL retorna 403 Forbidden")
    void getTriage_withProfessionalRole_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/triage")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }
}
