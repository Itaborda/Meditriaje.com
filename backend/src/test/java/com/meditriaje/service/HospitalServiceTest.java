package com.meditriaje.service;

import com.meditriaje.dto.hospital.*;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.OperationConflictException;
import com.meditriaje.model.*;
import com.meditriaje.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HospitalServiceTest {

    @Mock private HospitalAreaRepository areaRepository;
    @Mock private HospitalRoomRepository roomRepository;
    @Mock private HospitalBedRepository bedRepository;
    @Mock private BedOccupancyRepository occupancyRepository;
    @Mock private PatientMovementRepository movementRepository;
    @Mock private HospitalProcedureRepository procedureRepository;
    @Mock private HospitalDischargeRepository dischargeRepository;
    @Mock private CareEpisodeRepository episodeRepository;
    @Mock private SiteRepository siteRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProfessionalRepository professionalRepository;
    @Mock private AuditService auditService;

    private HospitalService hospitalService;

    private static final Instant AHORA = Instant.parse("2026-10-10T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(AHORA, ZoneId.of("UTC"));

    private User enfermeroMock;
    private User medicoUsuarioMock;
    private Professional medicoMock;
    private CareEpisode episodioMock;
    private HospitalBed camaDispMock;
    private HospitalRoom habitacionMock;
    private HospitalArea areaMock;
    private Site sedeMock;

    @BeforeEach
    void setUp() {
        hospitalService = new HospitalService(
                areaRepository,
                roomRepository,
                bedRepository,
                occupancyRepository,
                movementRepository,
                procedureRepository,
                dischargeRepository,
                episodeRepository,
                siteRepository,
                userRepository,
                professionalRepository,
                auditService,
                CLOCK
        );

        enfermeroMock = new User(10L, "enf-pub-id", "enfermera@hospital.com", "hash", "ACTIVO", 0, null, AHORA, false, false, null, null);
        medicoUsuarioMock = new User(20L, "med-pub-id", "medico@hospital.com", "hash", "ACTIVO", 0, null, AHORA, false, false, null, null);
        medicoMock = new Professional(5L, 20L, "med-prof-pub-id", 1L, "CC", "1234", "RM-999", "Roberto", "Gomez", "300", AHORA, AHORA);

        sedeMock = new Site(1L, 1L, "sede-pub", "Hospital Central", "Calle 16", "Valledupar", "ACTIVO");
        areaMock = new HospitalArea(1L, "area-pub", 1L, "URG", "Pabellón de Urgencias", "URGENCIAS", "1", "ACTIVA", AHORA);
        habitacionMock = new HospitalRoom(2L, "hab-pub", 1L, "OBS-01", "COMPARTIDA", "ACTIVA", AHORA);
        camaDispMock = new HospitalBed(3L, "cama-pub", 2L, "CAMA-01", "DISPONIBLE", AHORA, AHORA);

        episodioMock = new CareEpisode(100L, "ep-pub", 50L, "URGENCIA", 1L, "EN_TRIAJE", AHORA, null, "Dolor agudo", AHORA, AHORA);
    }

    @Test
    @DisplayName("H01/H02: Asignación de cama disponible a episodio exitosa")
    void assignBed_available_success() {
        AssignBedRequest req = new AssignBedRequest("cama-pub", "Paciente requiere observación inmediata");

        when(episodeRepository.findByPublicId("ep-pub")).thenReturn(Optional.of(episodioMock));
        when(userRepository.findByPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(bedRepository.findByPublicId("cama-pub")).thenReturn(Optional.of(camaDispMock));
        when(bedRepository.reserveBedIfAvailable(3L)).thenReturn(true);
        when(roomRepository.findById(2L)).thenReturn(Optional.of(habitacionMock));
        when(areaRepository.findById(1L)).thenReturn(Optional.of(areaMock));
        when(occupancyRepository.findActiveByEpisodeId(100L)).thenReturn(Optional.empty());

        BedOccupancy ocCreada = new BedOccupancy(1L, "oc-pub", 100L, 3L, AHORA, null, "ACTIVA", 10L, "Obs", AHORA);
        when(occupancyRepository.save(any())).thenReturn(ocCreada);

        BedDetailResponse detalle = new BedDetailResponse("cama-pub", "hab-pub", "OBS-01", "area-pub", "Urgencias", "sede-pub", "Hospital", "CAMA-01", "OCUPADA", "ep-pub", "Juan Gomez", "oc-pub");
        when(bedRepository.findDetailByPublicId("cama-pub")).thenReturn(Optional.of(detalle));

        BedDetailResponse resp = hospitalService.assignBed("ep-pub", req, "enf-pub-id", "127.0.0.1");

        assertThat(resp).isNotNull();
        assertThat(resp.status()).isEqualTo("OCUPADA");
        verify(bedRepository).reserveBedIfAvailable(3L);
        verify(movementRepository).save(any());
        verify(episodeRepository).updateStatus(100L, "OBSERVACION");
        verify(auditService).audit(any());
    }

    @Test
    @DisplayName("H02: Conflicto si la cama seleccionada no está DISPONIBLE")
    void assignBed_notAvailable_throwsConflict() {
        AssignBedRequest req = new AssignBedRequest("cama-pub", "Asignación");
        HospitalBed camaOcupada = new HospitalBed(3L, "cama-pub", 2L, "CAMA-01", "OCUPADA", AHORA, AHORA);

        when(episodeRepository.findByPublicId("ep-pub")).thenReturn(Optional.of(episodioMock));
        when(userRepository.findByPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(bedRepository.findByPublicId("cama-pub")).thenReturn(Optional.of(camaOcupada));

        assertThatThrownBy(() -> hospitalService.assignBed("ep-pub", req, "enf-pub-id", "127.0.0.1"))
                .isInstanceOf(OperationConflictException.class)
                .hasMessageContaining("no está disponible");
    }

    @Test
    @DisplayName("H03: Traslado intrahospitalario longitudinal finaliza cama anterior y asigna destino")
    void transferPatient_success() {
        TransferPatientRequest req = new TransferPatientRequest("cama-dest-pub", "Traslado a UCI");
        BedOccupancy ocupActual = new BedOccupancy(1L, "oc-1", 100L, 3L, AHORA, null, "ACTIVA", 10L, "Obs", AHORA);
        HospitalBed camaDest = new HospitalBed(4L, "cama-dest-pub", 2L, "CAMA-02", "DISPONIBLE", AHORA, AHORA);

        when(episodeRepository.findByPublicId("ep-pub")).thenReturn(Optional.of(episodioMock));
        when(userRepository.findByPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(occupancyRepository.findActiveByEpisodeId(100L)).thenReturn(Optional.of(ocupActual));
        when(bedRepository.findByPublicId("cama-dest-pub")).thenReturn(Optional.of(camaDest));
        when(bedRepository.reserveBedIfAvailable(4L)).thenReturn(true);
        when(bedRepository.findById(3L)).thenReturn(Optional.of(camaDispMock));
        when(roomRepository.findById(2L)).thenReturn(Optional.of(habitacionMock));
        when(areaRepository.findById(1L)).thenReturn(Optional.of(areaMock));

        MovementResponse resp = hospitalService.transferPatient("ep-pub", req, "enf-pub-id", "127.0.0.1");

        assertThat(resp).isNotNull();
        verify(occupancyRepository).finishOccupancy(1L, AHORA);
        verify(bedRepository).updateStatus(3L, "LIMPIEZA");
        verify(bedRepository).reserveBedIfAvailable(4L);
        verify(movementRepository).save(any());
    }

    @Test
    @DisplayName("H04: Registro de procedimiento clínico actualiza episodio a QUIROFANO")
    void registerProcedure_success() {
        RegisterProcedureRequest req = new RegisterProcedureRequest(
                "APENDICECTOMIA", "Intervención urgente por apendicitis aguda", "med-prof-pub-id", "hab-pub", "Sin complicaciones iniciales"
        );

        when(episodeRepository.findByPublicId("ep-pub")).thenReturn(Optional.of(episodioMock));
        when(userRepository.findByPublicId("med-pub-id")).thenReturn(Optional.of(medicoUsuarioMock));
        when(professionalRepository.findByPublicId("med-prof-pub-id")).thenReturn(Optional.of(medicoMock));
        when(roomRepository.findByPublicId("hab-pub")).thenReturn(Optional.of(habitacionMock));

        ProcedureResponse resp = hospitalService.registerProcedure("ep-pub", req, "med-pub-id", "127.0.0.1");

        assertThat(resp).isNotNull();
        assertThat(resp.procedureType()).isEqualTo("APENDICECTOMIA");
        verify(procedureRepository).save(any());
        verify(episodeRepository).updateStatus(100L, "QUIROFANO");
    }

    @Test
    @DisplayName("H05: Egreso hospitalario libera cama, cierra episodio y audita evento")
    void registerDischargeHospital_physician_releasesBedAndEgresa() {
        RegisterDischargeRequest req = new RegisterDischargeRequest(
                "ALTA_DOMICILIO", "Resolución favorable de cuadro agudo", "Paciente afebril con signos estables", "Reposo 5 días y amoxicilina"
        );

        BedOccupancy ocActiva = new BedOccupancy(1L, "oc-1", 100L, 3L, AHORA, null, "ACTIVA", 10L, "Obs", AHORA);

        when(episodeRepository.findByPublicId("ep-pub")).thenReturn(Optional.of(episodioMock));
        when(userRepository.findByPublicId("med-pub-id")).thenReturn(Optional.of(medicoUsuarioMock));
        when(professionalRepository.findByUserId(20L)).thenReturn(Optional.of(medicoMock));
        when(occupancyRepository.findActiveByEpisodeId(100L)).thenReturn(Optional.of(ocActiva));

        hospitalService.registerHospitalDischarge(
                "ep-pub", req, "med-pub-id", List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL")), "127.0.0.1"
        );

        verify(occupancyRepository).finishOccupancy(1L, AHORA);
        verify(bedRepository).updateStatus(3L, "LIMPIEZA");
        verify(dischargeRepository).save(any());
        verify(episodeRepository).closeEpisode(100L, AHORA);
    }

    @Test
    @DisplayName("H05: Usuario sin rol médico (ej. enfermero) es rechazado con 403 al intentar dar egreso definitivo")
    void registerDischargeHospital_notPhysician_throwsUnauthorizedAccess() {
        RegisterDischargeRequest req = new RegisterDischargeRequest("ALTA_DOMICILIO", "Diag", "Epicrisis", "Plan");

        assertThatThrownBy(() -> hospitalService.registerHospitalDischarge(
                "ep-pub", req, "enf-pub-id", List.of(new SimpleGrantedAuthority("ROLE_ENFERMERIA")), "127.0.0.1"
        ))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Solo profesionales médicos pueden emitir órdenes de egreso");
    }

    @Test
    @DisplayName("H06: Centro de Control calcula censo de camas e indicadores de ocupación")
    void getCensusBeds_calculaOccupancyCorrectly() {
        when(siteRepository.getSingleSite()).thenReturn(sedeMock);

        BedDetailResponse c1 = new BedDetailResponse("c-1", "h-1", "H1", "a-1", "Urgencias", "sede-pub", "Hospital", "C1", "OCUPADA", "ep-1", "Paciente 1", "oc-1");
        BedDetailResponse c2 = new BedDetailResponse("c-2", "h-1", "H1", "a-1", "Urgencias", "sede-pub", "Hospital", "C2", "DISPONIBLE", null, null, null);
        BedDetailResponse c3 = new BedDetailResponse("c-3", "h-1", "H1", "a-1", "Urgencias", "sede-pub", "Hospital", "C3", "LIMPIEZA", null, null, null);
        BedDetailResponse c4 = new BedDetailResponse("c-4", "h-1", "H1", "a-1", "Urgencias", "sede-pub", "Hospital", "C4", "MANTENIMIENTO", null, null, null);

        when(bedRepository.listDetailsBySiteId(1L)).thenReturn(List.of(c1, c2, c3, c4));
        when(areaRepository.listBySiteId(1L)).thenReturn(List.of(areaMock));

        BedCensusResponse censo = hospitalService.getBedCensus();

        assertThat(censo).isNotNull();
        assertThat(censo.totalBeds()).isEqualTo(4);
        assertThat(censo.occupied()).isEqualTo(1);
        assertThat(censo.available()).isEqualTo(1);
        assertThat(censo.inCleaning()).isEqualTo(1);
        assertThat(censo.inMaintenance()).isEqualTo(1);
        assertThat(censo.occupancyRatePercentage()).isEqualTo(25.0);
    }

    @Test
    @DisplayName("H01: Listar áreas hospitalarias retorna cada área con sus habitaciones configuradas")
    void listAreasWithRooms_returnsAreasWithNestedRooms() {
        when(siteRepository.getSingleSiteId()).thenReturn(1L);
        when(areaRepository.listBySiteId(1L)).thenReturn(List.of(areaMock));
        when(roomRepository.listByAreaId(1L)).thenReturn(List.of(habitacionMock));

        List<HospitalAreaResponse> resp = hospitalService.listAreasWithRooms();

        assertThat(resp).hasSize(1);
        assertThat(resp.get(0).code()).isEqualTo("URG");
        assertThat(resp.get(0).rooms()).hasSize(1);
        assertThat(resp.get(0).rooms().get(0).code()).isEqualTo("OBS-01");
    }

    @Test
    @DisplayName("H01: Crear área hospitalaria válida persiste área y audita cambio administrativo")
    void createArea_validRequest_savesAndReturnsArea() {
        when(userRepository.findByPublicId("admin-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(siteRepository.getSingleSiteId()).thenReturn(1L);
        when(areaRepository.save(any())).thenAnswer(inv -> {
            HospitalArea a = inv.getArgument(0);
            return new HospitalArea(9L, a.publicId(), a.siteId(), a.code(), a.name(), a.type(), a.floor(), a.status(), a.createdAt());
        });

        HospitalAreaResponse resp = hospitalService.createArea(
                new CreateHospitalAreaRequest("hosp-3", "Hospitalización Piso 3", "hospitalizacion_general", "3"),
                "admin-pub-id",
                "127.0.0.1"
        );

        assertThat(resp.code()).isEqualTo("HOSP-3");
        assertThat(resp.type()).isEqualTo("HOSPITALIZACION_GENERAL");
        assertThat(resp.floor()).isEqualTo("3");
        verify(auditService).audit(any());
    }

    @Test
    @DisplayName("H01: Crear área hospitalaria con tipo inválido lanza InvalidDataException")
    void createArea_invalidType_throwsInvalidDataException() {
        assertThatThrownBy(() -> hospitalService.createArea(
                new CreateHospitalAreaRequest("X-01", "Inválida", "CAFETERIA", "1"),
                "admin-pub-id",
                "127.0.0.1"
        )).isInstanceOf(com.meditriaje.exception.InvalidDataException.class);
    }

    @Test
    @DisplayName("H01: Crear habitación en área existente persiste habitación y audita cambio")
    void createRoom_validRequest_savesAndReturnsRoom() {
        when(userRepository.findByPublicId("admin-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(areaRepository.findByPublicId("area-pub")).thenReturn(Optional.of(areaMock));
        when(roomRepository.save(any())).thenAnswer(inv -> {
            HospitalRoom r = inv.getArgument(0);
            return new HospitalRoom(12L, r.publicId(), r.areaId(), r.code(), r.type(), r.status(), r.createdAt());
        });

        HospitalRoomResponse resp = hospitalService.createRoom(
                "area-pub",
                new CreateHospitalRoomRequest("hab-205", "individual"),
                "admin-pub-id",
                "127.0.0.1"
        );

        assertThat(resp.areaPublicId()).isEqualTo("area-pub");
        assertThat(resp.code()).isEqualTo("HAB-205");
        assertThat(resp.type()).isEqualTo("INDIVIDUAL");
        verify(auditService).audit(any());
    }

    @Test
    @DisplayName("H01: Crear camas en lote genera prefijos numerados y retorna detalle")
    void createBeds_batchCount_generatesNumberedCodesAndReturnsDetails() {
        when(userRepository.findByPublicId("admin-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(roomRepository.findByPublicId("hab-pub")).thenReturn(Optional.of(habitacionMock));
        when(areaRepository.findById(1L)).thenReturn(Optional.of(areaMock));
        when(bedRepository.save(any())).thenAnswer(inv -> {
            HospitalBed b = inv.getArgument(0);
            return new HospitalBed(20L, b.publicId(), b.roomId(), b.code(), b.status(), b.createdAt(), b.updatedAt());
        });
        when(bedRepository.findDetailByPublicId(anyString())).thenReturn(Optional.empty());

        List<BedDetailResponse> batch = hospitalService.createBeds(
                "hab-pub",
                new CreateHospitalBedRequest("hosp-201", 2),
                "admin-pub-id",
                "127.0.0.1"
        );

        assertThat(batch).hasSize(2);
        assertThat(batch.get(0).code()).isEqualTo("HOSP-201-01");
        assertThat(batch.get(1).code()).isEqualTo("HOSP-201-02");
        verify(auditService, times(2)).audit(any());
    }
}
