package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
import com.meditriaje.dto.hospital.*;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.HospitalService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HospitalController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class HospitalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HospitalService hospitalService;

    @MockBean
    private JwtService jwtService;

    private static final String TOKEN_ENFERMERIA = "token.enfermeria.valido";
    private static final String TOKEN_PROFESIONAL = "token.profesional.valido";
    private static final String TOKEN_PACIENTE = "token.paciente.valido";
    private static final String TOKEN_ADMIN = "token.admin.valido";

    @BeforeEach
    void setUp() {
        when(jwtService.isValid(TOKEN_ENFERMERIA)).thenReturn(true);
        when(jwtService.extractPublicId(TOKEN_ENFERMERIA)).thenReturn("usr-enf-uuid");
        when(jwtService.extractRoles(TOKEN_ENFERMERIA)).thenReturn(List.of("ROLE_ENFERMERIA"));

        when(jwtService.isValid(TOKEN_PROFESIONAL)).thenReturn(true);
        when(jwtService.extractPublicId(TOKEN_PROFESIONAL)).thenReturn("usr-prof-uuid");
        when(jwtService.extractRoles(TOKEN_PROFESIONAL)).thenReturn(List.of("ROLE_PROFESIONAL"));

        when(jwtService.isValid(TOKEN_PACIENTE)).thenReturn(true);
        when(jwtService.extractPublicId(TOKEN_PACIENTE)).thenReturn("usr-pac-uuid");
        when(jwtService.extractRoles(TOKEN_PACIENTE)).thenReturn(List.of("ROLE_PACIENTE"));

        when(jwtService.isValid(TOKEN_ADMIN)).thenReturn(true);
        when(jwtService.extractPublicId(TOKEN_ADMIN)).thenReturn("usr-admin-uuid");
        when(jwtService.extractRoles(TOKEN_ADMIN)).thenReturn(List.of("ROLE_ADMINISTRADOR"));
    }

    private Cookie cookie(String token) {
        return new Cookie("access_token", token);
    }

    @Test
    @DisplayName("SEC01: Petición anónima a asignación de cama retorna 401 Unauthorized")
    void assignBed_withoutSession_returns401() throws Exception {
        AssignBedRequest req = new AssignBedRequest("cama-pub", "Obs");

        mockMvc.perform(post("/api/v1/hospital/episodes/ep-1/beds/assign")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SEC02: Paciente intentando asignar cama retorna 403 Forbidden")
    void assignBed_patient_returns403() throws Exception {
        AssignBedRequest req = new AssignBedRequest("cama-pub", "Obs");

        mockMvc.perform(post("/api/v1/hospital/episodes/ep-1/beds/assign")
                        .cookie(cookie(TOKEN_PACIENTE))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H01/H02: Enfermería asignando cama disponible retorna 201 Created")
    void assignBed_nursing_returns201() throws Exception {
        AssignBedRequest req = new AssignBedRequest("cama-pub", "Asignación inicial");
        BedDetailResponse resp = new BedDetailResponse(
                "cama-pub", "hab-pub", "H1", "area-pub", "Urgencias",
                "sede-pub", "Hospital", "CAMA-01", "OCUPADA", "ep-1", "Carlos", "oc-1"
        );

        when(hospitalService.assignBed(eq("ep-1"), any(), anyString(), anyString())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/hospital/episodes/ep-1/beds/assign")
                        .cookie(cookie(TOKEN_ENFERMERIA))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicId").value("cama-pub"))
                .andExpect(jsonPath("$.estado").value("OCUPADA"));
    }

    @Test
    @DisplayName("H03: Profesional trasladando paciente retorna 200 OK con movimiento")
    void transferPatient_professional_returns200() throws Exception {
        TransferPatientRequest req = new TransferPatientRequest("cama-dest-pub", "Pasa a UCI");
        MovementResponse mov = new MovementResponse(
                "mov-pub", "ep-1", "Urgencias", "UCI", "CAMA-01", "CAMA-02",
                Instant.now(), "Pasa a UCI", "medico@hospital.com"
        );

        when(hospitalService.transferPatient(eq("ep-1"), any(), anyString(), anyString())).thenReturn(mov);

        mockMvc.perform(post("/api/v1/hospital/episodes/ep-1/beds/transfer")
                        .cookie(cookie(TOKEN_PROFESIONAL))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("mov-pub"))
                .andExpect(jsonPath("$.areaDestinoNombre").value("UCI"));
    }

    @Test
    @DisplayName("H05: Enfermería intentando egreso médico definitivo retorna 403 Forbidden")
    void registerDischarge_nursing_returns403() throws Exception {
        RegisterDischargeRequest req = new RegisterDischargeRequest("ALTA_DOMICILIO", "Diag", "Epicrisis", "Plan");

        mockMvc.perform(post("/api/v1/hospital/episodes/ep-1/discharge")
                        .cookie(cookie(TOKEN_ENFERMERIA))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H05: Profesional médico registrando alta hospitalaria retorna 204 No Content")
    void registerDischarge_professional_returns204() throws Exception {
        RegisterDischargeRequest req = new RegisterDischargeRequest("ALTA_DOMICILIO", "Diag", "Epicrisis", "Plan");

        mockMvc.perform(post("/api/v1/hospital/episodes/ep-1/discharge")
                        .cookie(cookie(TOKEN_PROFESIONAL))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("H06: Consulta de censo de camas por administrador retorna 200 OK con métricas")
    void getCensusBeds_admin_returns200() throws Exception {
        BedCensusResponse censo = new BedCensusResponse("sede-pub", "Hospital Central", 20, 15, 3, 1, 1, 75.0, List.of());
        when(hospitalService.getBedCensus()).thenReturn(censo);

        mockMvc.perform(get("/api/v1/hospital/census")
                        .cookie(cookie(TOKEN_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCamas").value(20))
                .andExpect(jsonPath("$.ocupadas").value(15))
                .andExpect(jsonPath("$.tasaOcupacionPorcentaje").value(75.0));
    }

    @Test
    @DisplayName("H01: Consultar áreas y habitaciones hospitalarias retorna 200 OK")
    void listAreas_nursing_returns200() throws Exception {
        HospitalAreaResponse area = new HospitalAreaResponse(
                "area-1", "URG-OBS", "Observación Urgencias", "OBSERVACION", "1", "ACTIVA",
                List.of(new HospitalRoomResponse("hab-1", "area-1", "OBS-101", "COMPARTIDA", "ACTIVA"))
        );
        when(hospitalService.listAreasWithRooms()).thenReturn(List.of(area));

        mockMvc.perform(get("/api/v1/hospital/areas")
                        .cookie(cookie(TOKEN_ENFERMERIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("URG-OBS"))
                .andExpect(jsonPath("$[0].habitaciones[0].codigo").value("OBS-101"));
    }

    @Test
    @DisplayName("H01: Administrador creando área hospitalaria retorna 201 Created")
    void createArea_admin_returns201() throws Exception {
        CreateHospitalAreaRequest req = new CreateHospitalAreaRequest("UCI-ADU", "UCI Adultos", "UCI_ADULTOS", "3");
        HospitalAreaResponse resp = new HospitalAreaResponse("area-uci", "UCI-ADU", "UCI Adultos", "UCI_ADULTOS", "3", "ACTIVA", List.of());
        when(hospitalService.createArea(any(), eq("usr-admin-uuid"), anyString())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/hospital/areas")
                        .cookie(cookie(TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("UCI-ADU"));
    }

    @Test
    @DisplayName("SEC02: Enfermería intentando crear área hospitalaria recibe 403 Forbidden")
    void createArea_nursing_returns403() throws Exception {
        CreateHospitalAreaRequest req = new CreateHospitalAreaRequest("UCI-ADU", "UCI Adultos", "UCI_ADULTOS", "3");

        mockMvc.perform(post("/api/v1/hospital/areas")
                        .cookie(cookie(TOKEN_ENFERMERIA))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H01: Administrador creando habitación en área retorna 201 Created")
    void createRoom_admin_returns201() throws Exception {
        CreateHospitalRoomRequest req = new CreateHospitalRoomRequest("UCI-301", "AISLAMIENTO");
        HospitalRoomResponse resp = new HospitalRoomResponse("hab-301", "area-uci", "UCI-301", "AISLAMIENTO", "ACTIVA");
        when(hospitalService.createRoom(eq("area-uci"), any(), eq("usr-admin-uuid"), anyString())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/hospital/areas/area-uci/rooms")
                        .cookie(cookie(TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("UCI-301"));
    }

    @Test
    @DisplayName("H01: Administrador creando camas en habitación retorna 201 Created")
    void createBeds_admin_returns201() throws Exception {
        CreateHospitalBedRequest req = new CreateHospitalBedRequest("UCI-301", 2);
        List<BedDetailResponse> resp = List.of(
                new BedDetailResponse("cama-1", "hab-301", "UCI-301", "area-uci", "UCI Adultos", "sede-1", "Hospital", "UCI-301-01", "DISPONIBLE", null, null, null),
                new BedDetailResponse("cama-2", "hab-301", "UCI-301", "area-uci", "UCI Adultos", "sede-1", "Hospital", "UCI-301-02", "DISPONIBLE", null, null, null)
        );
        when(hospitalService.createBeds(eq("hab-301"), any(), eq("usr-admin-uuid"), anyString())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/hospital/rooms/hab-301/beds")
                        .cookie(cookie(TOKEN_ADMIN))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].codigo").value("UCI-301-01"))
                .andExpect(jsonPath("$[1].codigo").value("UCI-301-02"));
    }
}
