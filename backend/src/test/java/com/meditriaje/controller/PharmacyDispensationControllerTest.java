package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.dto.pharmacy.DeliveryDetailRequest;
import com.meditriaje.dto.pharmacy.DispensationDetailResponse;
import com.meditriaje.dto.pharmacy.DispensationResponse;
import com.meditriaje.dto.pharmacy.PrescriptionDispensationResponse;
import com.meditriaje.dto.pharmacy.RegisterDispensationRequest;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.model.PrescriptionDispensationStatus;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.DispensationService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PharmacyDispensationController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class PharmacyDispensationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DispensationService dispensationService;

    @MockBean
    private JwtService jwtService;

    private final String tokenFarmaceutico = "token.farmaceutico.valido";
    private final String tokenPaciente = "token.paciente.valido";
    private final String tokenAdmin = "token.admin.valido";

    private void mockAuth(String token, String publicId, String role) {
        when(jwtService.isValid(token)).thenReturn(true);
        when(jwtService.extractPublicId(token)).thenReturn(publicId);
        when(jwtService.extractRoles(token)).thenReturn(List.of(role));
    }

    @Test
    @DisplayName("POST /api/v1/pharmacy/dispensations - Farmacéutico con CSRF: 201 Created")
    void registerDispensation_pharmacist_returns201() throws Exception {
        mockAuth(tokenFarmaceutico, "farm-uuid-1", "ROLE_FARMACEUTICO");

        RegisterDispensationRequest request = new RegisterDispensationRequest(
                "receta-uuid-1",
                "Entrega en ventanilla",
                List.of(new DeliveryDetailRequest("med-uuid-1", 10, "LOTE-INV-1", LocalDate.of(2028, 1, 1)))
        );

        DispensationResponse mockResp = new DispensationResponse(
                "disp-uuid-abc",
                "receta-uuid-1",
                "sede-uuid-1",
                "Sede Norte",
                "farm-uuid-1",
                "farmacia@hospital.com",
                "Entrega en ventanilla",
                Instant.now(),
                List.of(new DispensationDetailResponse("med-uuid-1", "MED-1", "Acetaminofen", 10, "LOTE-INV-1", LocalDate.of(2028, 1, 1), Instant.now()))
        );

        when(dispensationService.registerDispensation(any(), eq("farm-uuid-1"), any(), anyString()))
                .thenReturn(mockResp);

        mockMvc.perform(post("/api/v1/pharmacy/dispensations")
                        .cookie(new Cookie("access_token", tokenFarmaceutico))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/pharmacy/dispensations/disp-uuid-abc"))
                .andExpect(jsonPath("$.publicId").value("disp-uuid-abc"))
                .andExpect(jsonPath("$.detalles[0].cantidadEntregada").value(10))
                .andExpect(jsonPath("$.detalles[0].lote").value("LOTE-INV-1"));
    }

    @Test
    @DisplayName("POST /api/v1/pharmacy/dispensations - Paciente recibe 403 Forbidden")
    void registerDispensation_patient_returns403() throws Exception {
        mockAuth(tokenPaciente, "pac-uuid-1", "ROLE_PACIENTE");

        RegisterDispensationRequest request = new RegisterDispensationRequest(
                "receta-uuid-1", null,
                List.of(new DeliveryDetailRequest("med-uuid-1", 5, null, null))
        );

        mockMvc.perform(post("/api/v1/pharmacy/dispensations")
                        .cookie(new Cookie("access_token", tokenPaciente))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    @DisplayName("POST /api/v1/pharmacy/dispensations - Administrador recibe 403 Forbidden")
    void registerDispensation_admin_returns403() throws Exception {
        mockAuth(tokenAdmin, "admin-uuid-1", "ROLE_ADMINISTRADOR");

        RegisterDispensationRequest request = new RegisterDispensationRequest(
                "receta-uuid-1", null,
                List.of(new DeliveryDetailRequest("med-uuid-1", 5, null, null))
        );

        mockMvc.perform(post("/api/v1/pharmacy/dispensations")
                        .cookie(new Cookie("access_token", tokenAdmin))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    @DisplayName("POST /api/v1/pharmacy/dispensations - Sin autenticación recibe 401 Unauthorized")
    void registerDispensation_withoutAuth_returns401() throws Exception {
        RegisterDispensationRequest request = new RegisterDispensationRequest(
                "receta-uuid-1", null,
                List.of(new DeliveryDetailRequest("med-uuid-1", 5, null, null))
        );

        mockMvc.perform(post("/api/v1/pharmacy/dispensations")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    @DisplayName("POST /api/v1/pharmacy/dispensations - Request inválido (sin detalles) retorna 400 Bad Request")
    void registerDispensation_invalidRequest_returns400() throws Exception {
        mockAuth(tokenFarmaceutico, "farm-uuid-1", "ROLE_FARMACEUTICO");

        RegisterDispensationRequest request = new RegisterDispensationRequest(
                "receta-uuid-1", null, List.of()
        );

        mockMvc.perform(post("/api/v1/pharmacy/dispensations")
                        .cookie(new Cookie("access_token", tokenFarmaceutico))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    @DisplayName("GET /api/v1/pharmacy/dispensations/{publicId} - Farmacéutico: 200 OK")
    void getDispensation_pharmacist_returns200() throws Exception {
        mockAuth(tokenFarmaceutico, "farm-uuid-1", "ROLE_FARMACEUTICO");

        DispensationResponse mockResp = new DispensationResponse(
                "disp-uuid-abc", "receta-1", "sede-1", "Sede Central", "farm-1", "farm@hospital.com",
                null, Instant.now(), List.of()
        );
        when(dispensationService.getDispensationByPublicId("disp-uuid-abc")).thenReturn(mockResp);

        mockMvc.perform(get("/api/v1/pharmacy/dispensations/disp-uuid-abc")
                        .cookie(new Cookie("access_token", tokenFarmaceutico)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("disp-uuid-abc"));
    }

    @Test
    @DisplayName("GET /api/v1/pharmacy/prescriptions - Farmacéutico: 200 OK paginado")
    void searchPrescriptions_pharmacist_returns200() throws Exception {
        mockAuth(tokenFarmaceutico, "farm-uuid-1", "ROLE_FARMACEUTICO");

        PrescriptionDispensationResponse item = new PrescriptionDispensationResponse(
                "rec-uuid-1", "REC-A1B2C3D4", "atn-1", "pac-1", "CC 10203040", "Carlos Gomez",
                "prof-1", "Dra. Perez", "Medicina General", 30, Instant.now(), Instant.now().plusSeconds(86400),
                false, PrescriptionDispensationStatus.PENDIENTE, List.of(), List.of()
        );
        PaginatedResponse<PrescriptionDispensationResponse> pag = PaginatedResponse.of(List.of(item), 0, 10, 1);

        when(dispensationService.searchPrescriptionsForPharmacy(anyString(), anyInt(), anyInt(), any()))
                .thenReturn(pag);

        mockMvc.perform(get("/api/v1/pharmacy/prescriptions")
                        .cookie(new Cookie("access_token", tokenFarmaceutico))
                        .param("query", "10203040"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].recetaPublicId").value("rec-uuid-1"))
                .andExpect(jsonPath("$.content[0].codigoReclamacion").value("REC-A1B2C3D4"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/pharmacy/prescriptions - Paciente recibe 403 Forbidden")
    void searchPrescriptions_patient_returns403() throws Exception {
        mockAuth(tokenPaciente, "pac-uuid-1", "ROLE_PACIENTE");

        mockMvc.perform(get("/api/v1/pharmacy/prescriptions")
                        .cookie(new Cookie("access_token", tokenPaciente)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    @DisplayName("GET /api/v1/pharmacy/prescriptions/{publicId} - Farmacéutico: 200 OK con saldos")
    void getPrescription_pharmacist_returns200() throws Exception {
        mockAuth(tokenFarmaceutico, "farm-uuid-1", "ROLE_FARMACEUTICO");

        PrescriptionDispensationResponse mockResp = new PrescriptionDispensationResponse(
                "rec-uuid-1", "REC-A1B2C3D4", "atn-1", "pac-1", "CC 10203040", "Carlos Gomez",
                "prof-1", "Dra. Perez", "Medicina General", 30, Instant.now(), Instant.now().plusSeconds(86400),
                false, PrescriptionDispensationStatus.PENDIENTE, List.of(), List.of()
        );

        when(dispensationService.getPrescriptionForPharmacy(eq("rec-uuid-1"), any()))
                .thenReturn(mockResp);

        mockMvc.perform(get("/api/v1/pharmacy/prescriptions/rec-uuid-1")
                        .cookie(new Cookie("access_token", tokenFarmaceutico)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recetaPublicId").value("rec-uuid-1"))
                .andExpect(jsonPath("$.codigoReclamacion").value("REC-A1B2C3D4"));
    }

    @Test
    @DisplayName("GET /api/v1/pharmacy/prescriptions/{publicId} - Administrador recibe 403 Forbidden")
    void getPrescription_admin_returns403() throws Exception {
        mockAuth(tokenAdmin, "admin-uuid-1", "ROLE_ADMINISTRADOR");

        mockMvc.perform(get("/api/v1/pharmacy/prescriptions/rec-uuid-1")
                        .cookie(new Cookie("access_token", tokenAdmin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }
}
