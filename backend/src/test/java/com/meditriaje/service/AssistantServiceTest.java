package com.meditriaje.service;

import com.meditriaje.dto.assistant.AssistantQuestionRequest;
import com.meditriaje.dto.assistant.AssistantAnswerResponse;
import com.meditriaje.exception.InvalidDataException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantServiceTest {

    private AssistantService assistantService;

    @BeforeEach
    void setUp() {
        assistantService = new AssistantService();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Siento un dolor de pecho opresivo que se va al brazo",
            "Mi hermano no puede respirar y tiene mucho ahogo",
            "El paciente está inconsciente en el piso",
            "Presenta convulsiones continuas",
            "Hay un sangrado abundante incontrolable",
            "Posible acv con boca torcida y perdida de fuerza"
    })
    @DisplayName("Detección infalible de emergencias: activa alerta inmediata 123")
    void processQuery_emergency_activeAlert(String mensaje) {
        AssistantQuestionRequest req = new AssistantQuestionRequest(mensaje, null);

        AssistantAnswerResponse res = assistantService.processQuery(req);

        assertThat(res.isEmergency()).isTrue();
        assertThat(res.category()).isEqualTo("EMERGENCIA");
        assertThat(res.answer()).contains("123");
        assertThat(res.answer()).contains("urgencias");
        assertThat(res.suggestions()).anyMatch(s -> s.spaRoute().equals("tel:123"));
        assertThat(res.legalNotice()).isNotBlank();
    }

    @Test
    @DisplayName("Consulta sobre triaje: explica los 5 niveles y ofrece enlace de triaje")
    void processQuery_triage_success() {
        AssistantQuestionRequest req = new AssistantQuestionRequest("¿Cómo funciona el triaje y qué es el nivel II?", null);

        AssistantAnswerResponse res = assistantService.processQuery(req);

        assertThat(res.isEmergency()).isFalse();
        assertThat(res.category()).isEqualTo("TRIAJE");
        assertThat(res.answer()).contains("Nivel I");
        assertThat(res.answer()).contains("Nivel V");
        assertThat(res.suggestions()).anyMatch(s -> s.spaRoute().equals("#/patient/triage"));
    }

    @Test
    @DisplayName("Consulta sobre citas: explica regla de 2 horas y ofrece disponibilidad")
    void processQuery_appointments_success() {
        AssistantQuestionRequest req = new AssistantQuestionRequest("Quiero agendar o cancelar una cita", null);

        AssistantAnswerResponse res = assistantService.processQuery(req);

        assertThat(res.isEmergency()).isFalse();
        assertThat(res.category()).isEqualTo("CITAS");
        assertThat(res.answer()).contains("2 horas de anticipación");
        assertThat(res.suggestions()).anyMatch(s -> s.spaRoute().equals("#/patient/book"));
    }

    @Test
    @DisplayName("Consulta sobre farmacia: explica código de reclamación y saldos")
    void processQuery_pharmacy_success() {
        AssistantQuestionRequest req = new AssistantQuestionRequest("¿Cómo puedo reclamar mi medicamento con la receta?", null);

        AssistantAnswerResponse res = assistantService.processQuery(req);

        assertThat(res.isEmergency()).isFalse();
        assertThat(res.category()).isEqualTo("FARMACIA");
        assertThat(res.answer()).contains("REC-");
        assertThat(res.suggestions()).anyMatch(s -> s.spaRoute().equals("#/patient/prescriptions"));
    }

    @Test
    @DisplayName("Consulta sobre QR de emergencia: explica token de 15 minutos y PIN")
    void processQuery_summaryQr_success() {
        AssistantQuestionRequest req = new AssistantQuestionRequest("¿Cómo funciona el código QR de emergencia?", null);

        AssistantAnswerResponse res = assistantService.processQuery(req);

        assertThat(res.isEmergency()).isFalse();
        assertThat(res.category()).isEqualTo("RESUMEN_QR");
        assertThat(res.answer()).contains("15 minutos");
        assertThat(res.suggestions()).anyMatch(s -> s.spaRoute().equals("#/patient/emergency-qr"));
    }

    @Test
    @DisplayName("Consulta sobre historia clínica: explica inmutabilidad y enmiendas")
    void processQuery_history_success() {
        AssistantQuestionRequest req = new AssistantQuestionRequest("¿Por qué mi historia clínica es inmutable y cómo pedir una corrección?", null);

        AssistantAnswerResponse res = assistantService.processQuery(req);

        assertThat(res.isEmergency()).isFalse();
        assertThat(res.category()).isEqualTo("HISTORIA_CLINICA");
        assertThat(res.answer()).contains("enmiendas");
        assertThat(res.suggestions()).anyMatch(s -> s.spaRoute().equals("#/patient/history"));
    }

    @Test
    @DisplayName("Consulta general: responde con orientación cordial y menú de opciones")
    void processQuery_general_success() {
        AssistantQuestionRequest req = new AssistantQuestionRequest("Hola, buenos días", null);

        AssistantAnswerResponse res = assistantService.processQuery(req);

        assertThat(res.isEmergency()).isFalse();
        assertThat(res.category()).isEqualTo("GENERAL");
        assertThat(res.suggestions()).isNotEmpty();
    }

    @Test
    @DisplayName("Validación: mensaje vacío o nulo lanza DatosInvalidosException")
    void processQuery_messageEmpty_throwsException() {
        assertThatThrownBy(() -> assistantService.processQuery(new AssistantQuestionRequest("   ", null)))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("El mensaje de consulta no puede estar vacío.");
    }

    @Test
    @DisplayName("Modo Invitado (GUEST): consulta sobre citas indica iniciar sesión y ofrece login/registro")
    void processQuery_guest_appointmentsRequiresSession() {
        AssistantQuestionRequest req = new AssistantQuestionRequest("¿Cómo agendar una cita?", "GUEST");

        AssistantAnswerResponse res = assistantService.processQuery(req);

        assertThat(res.category()).isEqualTo("CITAS_GUEST");
        assertThat(res.answer()).contains("iniciar sesión");
        assertThat(res.suggestions()).anyMatch(s -> s.spaRoute().equals("#/login"));
        assertThat(res.suggestions()).anyMatch(s -> s.spaRoute().equals("#/register"));
    }

    @Test
    @DisplayName("Modo Invitado (GUEST): consulta general explica plataforma y ofrece login/registro")
    void processQuery_guest_general() {
        AssistantQuestionRequest req = new AssistantQuestionRequest("¿Qué es MediTriaje?", "GUEST");

        AssistantAnswerResponse res = assistantService.processQuery(req);

        assertThat(res.category()).isEqualTo("GENERAL_GUEST");
        assertThat(res.answer()).contains("MediTriaje");
        assertThat(res.suggestions()).anyMatch(s -> s.spaRoute().equals("#/login"));
        assertThat(res.suggestions()).anyMatch(s -> s.spaRoute().equals("#/register"));
    }

    @Test
    @DisplayName("Modo Administrador: consulta sobre reportes ofrece enlace a reportes operativos")
    void processQuery_admin_reportes() {
        AssistantQuestionRequest req = new AssistantQuestionRequest("¿Cómo consultar los reportes operativos?", "ROLE_ADMINISTRADOR");

        AssistantAnswerResponse res = assistantService.processQuery(req);

        assertThat(res.category()).isEqualTo("REPORTES_ADMIN");
        assertThat(res.answer()).contains("Reportes Operativos");
        assertThat(res.suggestions()).anyMatch(s -> s.spaRoute().equals("#/admin/reports"));
    }

    @Test
    @DisplayName("Modo Administrador: consulta sobre profesionales explica ReTHUS y ofrece gestión de profesionales")
    void processQuery_admin_professionales() {
        AssistantQuestionRequest req = new AssistantQuestionRequest("¿Cómo dar de alta a un médico?", "ROLE_ADMINISTRADOR");

        AssistantAnswerResponse res = assistantService.processQuery(req);

        assertThat(res.category()).isEqualTo("PROFESIONALES_ADMIN");
        assertThat(res.answer()).contains("ReTHUS");
        assertThat(res.suggestions()).anyMatch(s -> s.spaRoute().equals("#/admin/professionals"));
    }

    @Test
    @DisplayName("Modo Administrador: consulta sobre citas orienta al rol de gestión de slots sin enlaces de paciente")
    void processQuery_admin_appointments() {
        AssistantQuestionRequest req = new AssistantQuestionRequest("Quiero ver las citas", "ROLE_ADMINISTRADOR");

        AssistantAnswerResponse res = assistantService.processQuery(req);

        assertThat(res.category()).isEqualTo("ADMIN_ORIENTACION");
        assertThat(res.answer()).contains("Como Administrador");
        assertThat(res.suggestions()).anyMatch(s -> s.spaRoute().equals("#/admin/slots"));
        assertThat(res.suggestions()).noneMatch(s -> s.spaRoute().startsWith("#/patient/"));
    }

    @Test
    @DisplayName("Modo Profesional: consulta sobre agenda ofrece enlace a agenda médica")
    void processQuery_professional_agenda() {
        AssistantQuestionRequest req = new AssistantQuestionRequest("¿Cómo ver mi agenda de citas?", "ROLE_PROFESIONAL");

        AssistantAnswerResponse res = assistantService.processQuery(req);

        assertThat(res.category()).isEqualTo("AGENDA_PROFESIONAL");
        assertThat(res.answer()).contains("Agenda Médica");
        assertThat(res.suggestions()).anyMatch(s -> s.spaRoute().equals("#/professional/agenda"));
    }
}
