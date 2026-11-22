package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.availability.AvailabilitySlotResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.AvailabilityService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AvailabilityController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AvailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AvailabilityService availabilityService;

    @MockBean
    private JwtService jwtService;

    private static final String TOKEN_PACIENTE = "token.paciente.valido";
    private static final String TOKEN_PROFESIONAL = "token.profesional.valido";
    private static final String TOKEN_ADMIN = "token.admin.valido";

    @BeforeEach
    void setUpTokens() {
        when(jwtService.isValid(TOKEN_PACIENTE)).thenReturn(true);
        when(jwtService.extractPublicId(TOKEN_PACIENTE)).thenReturn("paciente-uuid-1");
        when(jwtService.extractRoles(TOKEN_PACIENTE)).thenReturn(List.of("ROLE_PACIENTE"));

        when(jwtService.isValid(TOKEN_PROFESIONAL)).thenReturn(true);
        when(jwtService.extractPublicId(TOKEN_PROFESIONAL)).thenReturn("profesional-uuid-1");
        when(jwtService.extractRoles(TOKEN_PROFESIONAL)).thenReturn(List.of("ROLE_PROFESIONAL"));

        when(jwtService.isValid(TOKEN_ADMIN)).thenReturn(true);
        when(jwtService.extractPublicId(TOKEN_ADMIN)).thenReturn("admin-uuid-1");
        when(jwtService.extractRoles(TOKEN_ADMIN)).thenReturn(List.of("ROLE_ADMINISTRADOR"));
    }

    // =========================================================================
    // SEGURIDAD Y CONTROL DE ACCESO (HU-03, ADR-002)
    // =========================================================================

    @Test
    void getAvailability_sinToken_retorna401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/availability"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void getAvailability_withPatientRole_returns200Ok() throws Exception {
        PaginatedResponse<AvailabilitySlotResponse> respuestaVacia = PaginatedResponse.of(List.of(), 0, 10, 0L);
        when(availabilityService.checkAvailability(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(respuestaVacia);

        mockMvc.perform(get("/api/v1/availability")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getAvailability_withProfessionalRole_returns200Ok() throws Exception {
        PaginatedResponse<AvailabilitySlotResponse> respuestaVacia = PaginatedResponse.of(List.of(), 0, 10, 0L);
        when(availabilityService.checkAvailability(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(respuestaVacia);

        mockMvc.perform(get("/api/v1/availability")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL)))
                .andExpect(status().isOk());
    }

    @Test
    void getAvailability_withAdminRole_returns200Ok() throws Exception {
        PaginatedResponse<AvailabilitySlotResponse> respuestaVacia = PaginatedResponse.of(List.of(), 0, 10, 0L);
        when(availabilityService.checkAvailability(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(respuestaVacia);

        mockMvc.perform(get("/api/v1/availability")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN)))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // CONSULTA Y PARÁMETROS (HU-03, ADR-003, ADR-005, ADR-006)
    // =========================================================================

    @Test
    void getAvailability_withDataValid_returnsSlotsPaginados() throws Exception {
        Instant inicio = Instant.parse("2026-10-15T14:00:00Z");
        Instant fin = Instant.parse("2026-10-15T14:20:00Z");

        AvailabilitySlotResponse slot = new AvailabilitySlotResponse(
                "slot-1", "prof-1", "Dra. Carolina Gomez",
                "esp-1", "Pediatria",
                "sede-1", "Sede Central", "Carrera 7 # 40-62", "Bogota",
                inicio, fin, "PRESENCIAL", 20
        );
        PaginatedResponse<AvailabilitySlotResponse> paginated = PaginatedResponse.of(List.of(slot), 0, 10, 1L);

        when(availabilityService.checkAvailability(
                eq("esp-1"), eq("PRESENCIAL"), eq(LocalDate.of(2026, 10, 15)), eq(0), eq(10)
        )).thenReturn(paginated);

        mockMvc.perform(get("/api/v1/availability")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .param("especialidadPublicId", "esp-1")
                        .param("modalidad", "PRESENCIAL")
                        .param("fecha", "2026-10-15")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].slotPublicId").value("slot-1"))
                .andExpect(jsonPath("$.content[0].profesionalNombre").value("Dra. Carolina Gomez"))
                .andExpect(jsonPath("$.content[0].especialidadNombre").value("Pediatria"))
                .andExpect(jsonPath("$.content[0].sedeNombre").value("Sede Central"))
                .andExpect(jsonPath("$.content[0].sedeDireccion").value("Carrera 7 # 40-62"))
                .andExpect(jsonPath("$.content[0].sedeCiudad").value("Bogota"))
                .andExpect(jsonPath("$.content[0].duracionMinutos").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getAvailability_prioritizesSpecialtyPublicIdOverSpecialty() throws Exception {
        PaginatedResponse<AvailabilitySlotResponse> respuesta = PaginatedResponse.of(List.of(), 0, 10, 0L);
        when(availabilityService.checkAvailability(eq("esp-uuid-prioritario"), any(), any(), anyInt(), anyInt()))
                .thenReturn(respuesta);

        mockMvc.perform(get("/api/v1/availability")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .param("especialidad", "esp-secundario")
                        .param("especialidadPublicId", "esp-uuid-prioritario"))
                .andExpect(status().isOk());

        verify(availabilityService).checkAvailability(
                eq("esp-uuid-prioritario"), isNull(), isNull(), eq(0), eq(10)
        );
    }

    @Test
    void getAvailability_usesParamsAlternativosWhenPublicIdNotViene() throws Exception {
        PaginatedResponse<AvailabilitySlotResponse> respuesta = PaginatedResponse.of(List.of(), 0, 10, 0L);
        when(availabilityService.checkAvailability(eq("esp-1"), any(), any(), anyInt(), anyInt()))
                .thenReturn(respuesta);

        mockMvc.perform(get("/api/v1/availability")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .param("especialidad", "esp-1"))
                .andExpect(status().isOk());

        verify(availabilityService).checkAvailability(
                eq("esp-1"), isNull(), isNull(), eq(0), eq(10)
        );
    }

    @Test
    void getAvailability_modalityInvalid_returns400BadRequest() throws Exception {
        when(availabilityService.checkAvailability(any(), eq("VIRTUAL"), any(), anyInt(), anyInt()))
                .thenThrow(new InvalidDataException("Modalidad inválida: 'VIRTUAL'"));

        mockMvc.perform(get("/api/v1/availability")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .param("modalidad", "VIRTUAL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    void getAvailability_withoutAvailability_returnsListEmpty() throws Exception {
        PaginatedResponse<AvailabilitySlotResponse> respuestaVacia = PaginatedResponse.of(List.of(), 0, 10, 0L);
        when(availabilityService.checkAvailability(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(respuestaVacia);

        mockMvc.perform(get("/api/v1/availability")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE))
                        .param("fecha", "2026-10-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));
    }
}
