package com.meditriaje.service;

import com.meditriaje.dto.emergency.EmergencyQueueItemResponse;
import com.meditriaje.dto.hospital.BedCensusResponse;
import com.meditriaje.dto.operational.*;
import com.meditriaje.model.*;
import com.meditriaje.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationalAnalyticsServiceTest {

    @Mock
    private OperationalAlertRepository alertRepository;

    @Mock
    private TrackingQrRepository trackingQrRepository;

    @Mock
    private HospitalService hospitalService;

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private CareEpisodeRepository episodeRepository;

    @Mock
    private TriageAssessmentRepository triageRepository;

    @Mock
    private ProvisionalIdentityRepository identityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    private Clock clock;
    private OperationalAnalyticsService service;

    private final Instant now = Instant.parse("2026-03-30T10:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(now, ZoneOffset.UTC);
        service = new OperationalAnalyticsService(
                alertRepository,
                trackingQrRepository,
                hospitalService,
                siteRepository,
                episodeRepository,
                triageRepository,
                identityRepository,
                userRepository,
                auditService,
                clock
        );
    }

    @Test
    @DisplayName("O01/O02: obtenerDashboardHospitalario calcula ocupación, esperas y genera alerta si saturación >= 85%")
    void getDashboardHospital_calculaAndGeneratesAlert() {
        Site sede = new Site(1L, 10L, "sede-1", "Sede Central", "Dir", "Valledupar", "ACTIVO");
        when(siteRepository.getSingleSite()).thenReturn(sede);

        BedCensusResponse censo = new BedCensusResponse(
                "sede-1", "Sede Central", 100, 88, 10, 2, 0, 88.0, List.of()
        );
        when(hospitalService.getBedCensus()).thenReturn(censo);

        EmergencyQueueItemResponse itemCola = new EmergencyQueueItemResponse(
                "ep-1", null, "Paciente NN", "NN-001", "sede-1", "Sede Central",
                "URGENCIA", "EN_VALORACION", "II", "Dolor torácico", 40L, now.minusSeconds(2400), "Dr. Médico"
        );
        when(episodeRepository.listEmergencyQueueBySite(1L, null)).thenReturn(List.of(itemCola));
        when(alertRepository.listBySite(1L, "ACTIVA")).thenReturn(List.of());

        HospitalDashboardResponse resp = service.getHospitalDashboard("usr-admin", "127.0.0.1");

        assertNotNull(resp);
        assertEquals(88.0, resp.occupancyRatePercentage());
        assertEquals(88, resp.occupiedBeds());
        assertEquals(1, resp.activeEmergencyEpisodes());
        assertEquals(1L, resp.episodesByTriageLevel().get("II"));

        // F-03: GET es estrictamente idempotente y no genera alertas como efecto colateral
        verify(alertRepository, never()).save(any(OperationalAlert.class));

        // Proceso desacoplado de evaluación de alertas operativas
        service.evaluateAutomaticAlerts(sede, resp.occupancyRatePercentage(), resp.episodesByTriageLevel(), resp.averageWaitMinutes());
        verify(alertRepository, atLeastOnce()).save(any(OperationalAlert.class));
    }

    @Test
    @DisplayName("O02: reconocerAlerta actualiza estado a RECONOCIDA y audita evento")
    void acknowledgeAlert_success() {
        OperationalAlert alerta = new OperationalAlert(
                5L, "alerta-1", 1L, "SATURACION_CAMAS", "CRITICA", "Ocupación al 90%", "ACTIVA", null, null, null, now
        );
        when(alertRepository.findByPublicId("alerta-1")).thenReturn(Optional.of(alerta));

        User usuario = new User(10L, "usr-admin", "admin@hospital.com", "HASH", "ACTIVO", 0, null, now, false);
        when(userRepository.findByPublicId("usr-admin")).thenReturn(Optional.of(usuario));

        AcknowledgeAlertRequest req = new AcknowledgeAlertRequest("Se activó pabellón de expansión");
        service.acknowledgeAlert("alerta-1", req, "usr-admin", "127.0.0.1");

        verify(alertRepository).acknowledgeAlert(eq(5L), eq(10L), eq(now), eq("Se activó pabellón de expansión"));
        verify(auditService).audit(any(AuditEvent.class));
    }

    @Test
    @DisplayName("O03: generarQrSeguimiento genera token QROP seguro sin exponer PHI")
    void generateQrFollowUp_createsTokenExitosamente() {
        CareEpisode ep = new CareEpisode(
                50L, "ep-1", null, "URGENCIAS", 1L, "EN_VALORACION", now, null, "Urgencia", now, null
        );
        when(episodeRepository.findByPublicId("ep-1")).thenReturn(Optional.of(ep));

        User usuario = new User(20L, "usr-enf", "enf@hospital.com", "HASH", "ACTIVO", 0, null, now, false);
        when(userRepository.findByPublicId("usr-enf")).thenReturn(Optional.of(usuario));

        when(trackingQrRepository.findActiveByEpisodeId(50L)).thenReturn(Optional.empty());

        InpatientQrTracking qrGuardado = new InpatientQrTracking(
                1L, "qr-pub", 50L, "QROP-1234567890", "URGENCIAS / SALA DE OBSERVACION", "ACTIVA", now, now.plusSeconds(86400)
        );
        when(trackingQrRepository.save(any(InpatientQrTracking.class))).thenReturn(qrGuardado);
        when(identityRepository.findByEpisodeId(50L)).thenReturn(Optional.empty());
        when(triageRepository.findLatestByEpisodeId(50L)).thenReturn(Optional.empty());

        TrackingQrResponse resp = service.generateTrackingQr("ep-1", "usr-enf", "127.0.0.1");

        assertNotNull(resp);
        assertEquals("QROP-1234567890", resp.qrToken());
        assertEquals("URGENCIAS / SALA DE OBSERVACION", resp.currentLocation());
        verify(auditService).audit(any(AuditEvent.class));
    }

    @Test
    @DisplayName("O03: consultarPorTokenQr recupera ubicación sin exponer historial clínico")
    void getByTokenQr_returnsLocationSegura() {
        InpatientQrTracking qr = new InpatientQrTracking(
                1L, "qr-pub", 50L, "QROP-TEST", "PABELLON SAN ROQUE / CAMA 102", "ACTIVO", now, now.plusSeconds(86400)
        );
        when(trackingQrRepository.findByToken("QROP-TEST")).thenReturn(Optional.of(qr));

        CareEpisode ep = new CareEpisode(
                50L, "ep-1", null, "HOSPITALIZACION", 1L, "EN_PABELLON", now, null, "Hospitalizado", now, null
        );
        when(episodeRepository.findById(50L)).thenReturn(Optional.of(ep));

        ProvisionalIdentity idProv = new ProvisionalIdentity(
                1L, "id-prov-uuid", 50L, "NN-20260330-0001", "Físico", 30, "M", "ALERTA", "PENDIENTE", null, null, null, now
        );
        when(identityRepository.findByEpisodeId(50L)).thenReturn(Optional.of(idProv));

        TrackingQrResponse resp = service.getByQrToken("QROP-TEST", "127.0.0.1");

        assertNotNull(resp);
        assertEquals("NN-20260330-0001", resp.provisionalIdentityCode());
        assertEquals("PABELLON SAN ROQUE / CAMA 102", resp.currentLocation());
        assertEquals("EN_PABELLON", resp.episodeStatus());
        verify(auditService).audit(any(AuditEvent.class));
    }
}
