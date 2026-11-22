package com.meditriaje.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.controller.AppointmentController;
import com.meditriaje.controller.ClinicalAllergyController;
import com.meditriaje.controller.ClinicalPatientHistoryController;
import com.meditriaje.controller.PatientController;
import com.meditriaje.controller.PatientAllergyController;
import com.meditriaje.controller.PrescriptionController;
import com.meditriaje.controller.TriageController;
import com.meditriaje.dto.allergy.InactivateAllergyRequest;
import com.meditriaje.dto.appointment.CancelAppointmentRequest;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.service.AllergyService;
import com.meditriaje.service.AppointmentService;
import com.meditriaje.service.ClinicalAttentionService;
import com.meditriaje.service.DispensationService;
import com.meditriaje.service.EmergencyQrService;
import com.meditriaje.service.FollowUpService;
import com.meditriaje.service.PatientService;
import com.meditriaje.service.PrescriptionService;
import com.meditriaje.service.TriageService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T6 (Brechas MVP #1): Pruebas exhaustivas de control de acceso cruzado por ID.
 * Demuestra que un paciente autenticado (B) no puede acceder ni alterar
 * recursos pertenecientes a otro paciente (A): triaje, citas, historia clínica,
 * recetas, alergias y accesos QR temporales.
 */
@WebMvcTest(controllers = {
        TriageController.class,
        AppointmentController.class,
        PrescriptionController.class,
        ClinicalPatientHistoryController.class,
        ClinicalAllergyController.class,
        PatientAllergyController.class,
        PatientController.class
})
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class CrossPatientIdAccessSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private TriageService triageService;

    @MockBean
    private AppointmentService appointmentService;

    @MockBean
    private PrescriptionService prescriptionService;

    @MockBean
    private ClinicalAttentionService clinicalAttentionService;

    @MockBean
    private AllergyService allergyService;

    @MockBean
    private PatientService pacienteService;

    @MockBean
    private FollowUpService followUpService;

    @MockBean
    private EmergencyQrService emergencyQrService;

    @MockBean
    private DispensationService dispensationService;

    private static final String TOKEN_PACIENTE_B = "jwt.paciente.b";
    private static final String PACIENTE_B_UUID = "usr-paciente-b-uuid";

    private static final String ID_TRIAJE_A = "triaje-paciente-a-123";
    private static final String ID_CITA_A = "cita-paciente-a-456";
    private static final String ID_RECETA_A = "receta-paciente-a-789";
    private static final String ID_PACIENTE_A = "paciente-a-uuid";
    private static final String ID_ALERGIA_A = "alergia-paciente-a-111";
    private static final String ID_QR_A = "qr-paciente-a-222";

    @BeforeEach
    void setUp() {
        when(jwtService.isValid(TOKEN_PACIENTE_B)).thenReturn(true);
        when(jwtService.extractPublicId(TOKEN_PACIENTE_B)).thenReturn(PACIENTE_B_UUID);
        when(jwtService.extractRoles(TOKEN_PACIENTE_B)).thenReturn(List.of("ROLE_PACIENTE"));
    }

    private Cookie cookieAuth() {
        return new Cookie("access_token", TOKEN_PACIENTE_B);
    }

    @Test
    @DisplayName("Triaje: Paciente B intentando leer triaje de Paciente A recibe 403 Forbidden")
    void triage_patientBQueriesTriageOfTo_returns403() throws Exception {
        when(triageService.getByPublicId(eq(ID_TRIAJE_A), eq(PACIENTE_B_UUID), any()))
                .thenThrow(new UnauthorizedAccessException("No tiene autorizacion para acceder al triaje de otro paciente."));

        mockMvc.perform(get("/api/v1/triage/{publicId}", ID_TRIAJE_A)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(cookieAuth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_NO_AUTORIZADO"));
    }

    @Test
    @DisplayName("Citas: Paciente B intentando cancelar cita de Paciente A recibe 403 Forbidden")
    void appointments_patientBCancelaAppointmentOfTo_returns403() throws Exception {
        CancelAppointmentRequest req = new CancelAppointmentRequest("Cancelacion arbitraria ajena");
        when(appointmentService.cancelAppointment(eq(ID_CITA_A), any(), eq(PACIENTE_B_UUID), any(), anyString()))
                .thenThrow(new UnauthorizedAccessException("No tiene autorizacion para cancelar una cita ajena."));

        mockMvc.perform(patch("/api/v1/appointments/{publicId}/cancel", ID_CITA_A)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(cookieAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_NO_AUTORIZADO"));
    }

    @Test
    @DisplayName("Citas (No-Show): Paciente B intentando marcar no-asistio en cita recibe 403 Forbidden")
    void appointments_patientBAttemptsMarkNotShow_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/appointments/{publicId}/no-show", ID_CITA_A)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(cookieAuth()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Recetas: Paciente B intentando leer receta de Paciente A recibe 403 Forbidden")
    void prescriptions_patientBQueriesPrescriptionOfTo_returns403() throws Exception {
        when(prescriptionService.getByPublicId(eq(ID_RECETA_A), eq(PACIENTE_B_UUID), any()))
                .thenThrow(new UnauthorizedAccessException("No tiene autorizacion para acceder a la receta de otro paciente."));

        mockMvc.perform(get("/api/v1/prescriptions/{publicId}", ID_RECETA_A)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(cookieAuth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_NO_AUTORIZADO"));
    }

    @Test
    @DisplayName("Historia Clínica: Paciente B intentando consultar historial clínico de Paciente A recibe 403 Forbidden")
    void history_patientBQueriesHistoryOfTo_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/clinical/patients/{patientPublicId}/history", ID_PACIENTE_A)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(cookieAuth()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Alergias Clínicas: Paciente B intentando consultar alergias de Paciente A vía endpoint asistencial recibe 403 Forbidden")
    void allergies_patientBQueriesAllergiesOfTo_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/clinical/patients/{patientPublicId}/allergies", ID_PACIENTE_A)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(cookieAuth()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Alergias Paciente: Paciente B intentando inactivar alergia de Paciente A recibe 403 Forbidden")
    void allergies_patientBInactiveAllergyOfTo_returns403() throws Exception {
        InactivateAllergyRequest req = new InactivateAllergyRequest("Intento de inactivacion no autorizada");
        doThrow(new UnauthorizedAccessException("No tiene autorizacion para inactivar una alergia de otro paciente."))
                .when(allergyService).inactivateMyAllergy(eq(ID_ALERGIA_A), any(), eq(PACIENTE_B_UUID), anyString());

        mockMvc.perform(patch("/api/v1/patients/me/allergies/{publicId}/deactivate", ID_ALERGIA_A)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(cookieAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_NO_AUTORIZADO"));
    }

    @Test
    @DisplayName("QR de Emergencia: Paciente B intentando revocar acceso QR de Paciente A recibe 403 Forbidden")
    void qr_patientBRevocaQrOfTo_returns403() throws Exception {
        doThrow(new UnauthorizedAccessException("No tiene autorizacion para revocar un acceso QR ajeno."))
                .when(emergencyQrService).revokeQrAccess(eq(ID_QR_A), eq(PACIENTE_B_UUID), anyString());

        mockMvc.perform(patch("/api/v1/patients/me/emergency-qr/{publicId}/revoke", ID_QR_A)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(cookieAuth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_NO_AUTORIZADO"));
    }
}
