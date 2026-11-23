package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.assistant.AssistantQuestionRequest;
import com.meditriaje.dto.assistant.AssistantAnswerResponse;
import com.meditriaje.dto.assistant.ActionSuggestionDto;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.AssistantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AssistantController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AssistantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AssistantService assistantService;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("POST /chat con orientación válida retorna 200 OK y contenido guiado")
    void chat_withQuestionValid_returns200Ok() throws Exception {
        AssistantQuestionRequest req = new AssistantQuestionRequest("¿Cómo agendo una cita médica?");
        AssistantAnswerResponse resp = new AssistantAnswerResponse(
                "Para agendar una cita médica en MediTriaje, ingresa a la sección Agendar Cita.",
                false,
                "CITAS",
                List.of(new ActionSuggestionDto("Agendar Cita", "#/patient/book", "calendar")),
                AssistantService.LEGAL_NOTICE
        );

        when(assistantService.processQuery(any(AssistantQuestionRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esEmergencia").value(false))
                .andExpect(jsonPath("$.categoria").value("CITAS"))
                .andExpect(jsonPath("$.respuesta").value("Para agendar una cita médica en MediTriaje, ingresa a la sección Agendar Cita."))
                .andExpect(jsonPath("$.sugerencias[0].etiqueta").value("Agendar Cita"))
                .andExpect(jsonPath("$.avisoLegal").value(AssistantService.LEGAL_NOTICE));
    }

    @Test
    @DisplayName("POST /chat con alarma médica retorna 200 OK con alerta de emergencia y llamada al 123")
    void chat_withSymptomAlarm_returns200OkWithEmergency() throws Exception {
        AssistantQuestionRequest req = new AssistantQuestionRequest("Tengo un fuerte dolor de pecho y me falta el aire");
        AssistantAnswerResponse resp = new AssistantAnswerResponse(
                "⚠️ ALERTA DE EMERGENCIA MÉDICA: Por favor comunícate de inmediato al 123.",
                true,
                "EMERGENCIA",
                List.of(new ActionSuggestionDto("Llamar al 123", "tel:123", "phone")),
                AssistantService.LEGAL_NOTICE
        );

        when(assistantService.processQuery(any(AssistantQuestionRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esEmergencia").value(true))
                .andExpect(jsonPath("$.categoria").value("EMERGENCIA"))
                .andExpect(jsonPath("$.sugerencias[0].rutaSpa").value("tel:123"));
    }

    @Test
    @DisplayName("POST /chat sin mensaje o mensaje en blanco retorna 400 Bad Request")
    void chat_withMessageBlank_returns400BadRequest() throws Exception {
        AssistantQuestionRequest req = new AssistantQuestionRequest("   ");

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    @DisplayName("POST /chat sin autenticación previa es permitido (acceso universal de orientación)")
    void chat_withoutSession_isAllowed() throws Exception {
        AssistantQuestionRequest req = new AssistantQuestionRequest("¿Qué es el triaje?");
        AssistantAnswerResponse resp = new AssistantAnswerResponse(
                "El triaje clasifica la urgencia clínica.",
                false,
                "TRIAJE",
                List.of(),
                AssistantService.LEGAL_NOTICE
        );

        when(assistantService.processQuery(any(AssistantQuestionRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria").value("TRIAJE"));
    }
}
