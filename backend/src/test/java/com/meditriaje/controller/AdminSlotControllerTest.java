package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.controller.admin.AdminSlotController;
import com.meditriaje.dto.admin.GenerateSlotsRequest;
import com.meditriaje.dto.admin.GenerateSlotsResponse;
import com.meditriaje.dto.admin.SlotResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.SlotGeneratorService;
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

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminSlotController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AdminSlotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SlotGeneratorService slotGeneratorService;

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
    // SEGURIDAD Y CONTROL DE ACCESO (HU-10, ADR-002)
    // =========================================================================

    @Test
    void getSlots_sinAutenticacion_retorna401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/slots"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void getSlots_withPatientRole_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/slots")
                        .cookie(new Cookie("access_token", TOKEN_PACIENTE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void postGenerate_withProfessionalRole_returns403Forbidden() throws Exception {
        GenerateSlotsRequest req = new GenerateSlotsRequest(
                "prof-1", LocalDate.now().plusDays(1), LocalDate.now().plusDays(1),
                LocalTime.of(8, 0), LocalTime.of(10, 0), 20, "PRESENCIAL", null
        );

        mockMvc.perform(post("/api/v1/admin/slots/generate")
                        .cookie(new Cookie("access_token", TOKEN_PROFESIONAL))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    // =========================================================================
    // OPERACIONES CRUD Y GENERACION CON ROLE_ADMINISTRADOR
    // =========================================================================

    @Test
    void postGenerate_withDataValid_returns201Created() throws Exception {
        GenerateSlotsRequest req = new GenerateSlotsRequest(
                "prof-1", LocalDate.now().plusDays(1), LocalDate.now().plusDays(1),
                LocalTime.of(8, 0), LocalTime.of(9, 0), 20, "PRESENCIAL", List.of(DayOfWeek.MONDAY)
        );

        SlotResponse slotResp = new SlotResponse(
                "slot-1", "prof-1", "Carlos Perez", "sede-1", "Sede Norte",
                "esp-1", "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "LIBRE"
        );
        GenerateSlotsResponse resp = new GenerateSlotsResponse(1, List.of(slotResp), "Se generaron exitosamente 1 slots.");

        when(slotGeneratorService.generateSlots(any(GenerateSlotsRequest.class), eq(ADMIN_UUID), anyString()))
                .thenReturn(resp);

        mockMvc.perform(post("/api/v1/admin/slots/generate")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slotsGenerados").value(1))
                .andExpect(jsonPath("$.slots[0].publicId").value("slot-1"))
                .andExpect(jsonPath("$.slots[0].profesionalNombre").value("Carlos Perez"));
    }

    @Test
    void postGenerate_invalidDataInBody_returns400BadRequest() throws Exception {
        // Falta fechaInicio
        GenerateSlotsRequest req = new GenerateSlotsRequest(
                "prof-1", null, LocalDate.now().plusDays(1),
                LocalTime.of(8, 0), LocalTime.of(10, 0), 20, "PRESENCIAL", null
        );

        mockMvc.perform(post("/api/v1/admin/slots/generate")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    void postGenerate_durationOutOfRange_returns400BadRequest() throws Exception {
        // Duración menor a 5 min
        GenerateSlotsRequest req = new GenerateSlotsRequest(
                "prof-1", LocalDate.now().plusDays(1), LocalDate.now().plusDays(1),
                LocalTime.of(8, 0), LocalTime.of(10, 0), 3, "PRESENCIAL", null
        );

        mockMvc.perform(post("/api/v1/admin/slots/generate")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    void postGenerate_overlapConflict_returns400BadRequest() throws Exception {
        GenerateSlotsRequest req = new GenerateSlotsRequest(
                "prof-1", LocalDate.now().plusDays(1), LocalDate.now().plusDays(1),
                LocalTime.of(8, 0), LocalTime.of(9, 0), 20, "PRESENCIAL", null
        );

        when(slotGeneratorService.generateSlots(any(), eq(ADMIN_UUID), anyString()))
                .thenThrow(new InvalidDataException("Existe un solape de horario para el profesional."));

        mockMvc.perform(post("/api/v1/admin/slots/generate")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    void getSlots_paginado_retorna200Ok() throws Exception {
        SlotResponse slotResp = new SlotResponse(
                "slot-1", "prof-1", "Carlos Perez", "sede-1", "Sede Norte",
                "esp-1", "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "LIBRE"
        );
        PaginatedResponse<SlotResponse> paginated = PaginatedResponse.of(List.of(slotResp), 0, 10, 1L);

        when(slotGeneratorService.list(anyInt(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(paginated);

        mockMvc.perform(get("/api/v1/admin/slots")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].publicId").value("slot-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getSlot_existente_retorna200Ok() throws Exception {
        SlotResponse slotResp = new SlotResponse(
                "slot-1", "prof-1", "Carlos Perez", "sede-1", "Sede Norte",
                "esp-1", "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "LIBRE"
        );
        when(slotGeneratorService.getByPublicId("slot-1")).thenReturn(slotResp);

        mockMvc.perform(get("/api/v1/admin/slots/slot-1")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("slot-1"))
                .andExpect(jsonPath("$.estado").value("LIBRE"));
    }

    @Test
    void getSlot_inexistente_retorna404NotFound() throws Exception {
        when(slotGeneratorService.getByPublicId("inexistente"))
                .thenThrow(new ResourceNotFoundException("Slot no encontrado: inexistente"));

        mockMvc.perform(get("/api/v1/admin/slots/inexistente")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void patchBlock_exitoso_retorna200Ok() throws Exception {
        SlotResponse slotResp = new SlotResponse(
                "slot-1", "prof-1", "Carlos Perez", "sede-1", "Sede Norte",
                "esp-1", "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "BLOQUEADO"
        );
        when(slotGeneratorService.blockSlot(eq("slot-1"), eq(ADMIN_UUID), anyString()))
                .thenReturn(slotResp);

        mockMvc.perform(patch("/api/v1/admin/slots/slot-1/block")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BLOQUEADO"));
    }

    @Test
    void patchUnblock_exitoso_retorna200Ok() throws Exception {
        SlotResponse slotResp = new SlotResponse(
                "slot-1", "prof-1", "Carlos Perez", "sede-1", "Sede Norte",
                "esp-1", "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "LIBRE"
        );
        when(slotGeneratorService.unblockSlot(eq("slot-1"), eq(ADMIN_UUID), anyString()))
                .thenReturn(slotResp);

        mockMvc.perform(patch("/api/v1/admin/slots/slot-1/unblock")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LIBRE"));
    }

    @Test
    void deleteSlot_success_returns204NotContent() throws Exception {
        doNothing().when(slotGeneratorService).deleteSlot(eq("slot-1"), eq(ADMIN_UUID), anyString());

        mockMvc.perform(delete("/api/v1/admin/slots/slot-1")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteSlot_notFree_returns400BadRequest() throws Exception {
        doThrow(new InvalidDataException("Solo se pueden eliminar slots en estado LIBRE."))
                .when(slotGeneratorService).deleteSlot(eq("slot-1"), eq(ADMIN_UUID), anyString());

        mockMvc.perform(delete("/api/v1/admin/slots/slot-1")
                        .cookie(new Cookie("access_token", TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }
}
