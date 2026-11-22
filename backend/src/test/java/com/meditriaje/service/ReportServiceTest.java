package com.meditriaje.service;

import com.meditriaje.dto.report.DistributionItemDto;
import com.meditriaje.dto.report.AppointmentMetricsDto;
import com.meditriaje.dto.report.TriageMetricsDto;
import com.meditriaje.dto.report.OperationalSummaryResponse;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.User;
import com.meditriaje.repository.ReportRepository;
import com.meditriaje.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-04T12:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    private ReportService reporteService;

    private final User adminUser = new User(
            1L, "usr-admin-uuid", "admin@meditriaje.com", "hash", "ACTIVO", 0, null, AHORA, false
    );


    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(AHORA, ZONE);
        reporteService = new ReportService(reportRepository, userRepository, auditService, fixedClock);
    }

    @Test
    @DisplayName("obtenerResumenOperativo: consolida métricas, calcula tasas y audita")
    void getSummaryOperativo_success() {
        LocalDate desde = LocalDate.of(2026, 10, 1);
        LocalDate hasta = LocalDate.of(2026, 10, 4);

        when(userRepository.findByPublicId("usr-admin-uuid")).thenReturn(Optional.of(adminUser));

        // Citas: 10 atendidas, 2 canceladas, 1 no asistió, 7 programadas = total 20
        when(reportRepository.countAppointmentsByStatus(any(), any())).thenReturn(Map.of(
                "ATENDIDA", 10L,
                "CANCELADA", 2L,
                "NO_ASISTIO", 1L,
                "PROGRAMADA", 7L
        ));
        when(reportRepository.getAppointmentsBySpecialty(any(), any())).thenReturn(List.of(
                new DistributionItemDto("esp-1", "Medicina General", 15L, 75.0)
        ));
        when(reportRepository.getAppointmentsBySite(any(), any())).thenReturn(List.of(
                new DistributionItemDto("sed-1", "Sede Norte", 20L, 100.0)
        ));

        // Triaje: 2 Nivel I, 3 Nivel II, 5 Nivel III = total 10; 2 emergencias
        when(reportRepository.countTriagesByLevel(any(), any())).thenReturn(Map.of(
                "I", 2L,
                "II", 3L,
                "III", 5L
        ));
        when(reportRepository.countEmergencyTriages(any(), any())).thenReturn(2L);
        when(reportRepository.getTriagesByRoute(any(), any())).thenReturn(List.of(
                new DistributionItemDto("URGENCIAS", "URGENCIAS", 2L, 20.0)
        ));

        // Farmacia
        when(reportRepository.countIssuedPrescriptions(any(), any())).thenReturn(10L);
        when(reportRepository.countPrescriptionsByDispensationStatus(any(), any())).thenReturn(Map.of(
                "PENDIENTE", 3L,
                "DISPENSADA_PARCIAL", 2L,
                "DISPENSADA_TOTAL", 5L
        ));
        when(reportRepository.countDispensedUnits(any(), any())).thenReturn(45L);

        // Break-Glass
        when(reportRepository.countBreakGlassActivations(any(), any())).thenReturn(4L);
        when(reportRepository.countActiveBreakGlass(any())).thenReturn(1L);
        when(reportRepository.getBreakGlassBySpecialty(any(), any())).thenReturn(List.of(
                new DistributionItemDto("esp-1", "Urgencias", 4L, 100.0)
        ));

        OperationalSummaryResponse response = reporteService.getOperationalSummary(
                desde, hasta, "usr-admin-uuid", "192.168.1.100"
        );

        assertThat(response).isNotNull();
        // Citas
        assertThat(response.appointments().totalAppointments()).isEqualTo(20L);
        assertThat(response.appointments().attended()).isEqualTo(10L);
        assertThat(response.appointments().complianceRate()).isEqualTo(50.0); // 10 / 20 * 100
        assertThat(response.appointments().cancellationRate()).isEqualTo(15.0); // (2+1) / 20 * 100 = 15.0%

        // Triaje
        assertThat(response.triage().totalTriages()).isEqualTo(10L);
        assertThat(response.triage().emergencies()).isEqualTo(2L);
        assertThat(response.triage().emergencyRate()).isEqualTo(20.0); // 2 / 10 * 100

        // Farmacia
        assertThat(response.pharmacy().totalPrescriptions()).isEqualTo(10L);
        assertThat(response.pharmacy().fullyDispensed()).isEqualTo(5L);
        assertThat(response.pharmacy().totalDispensedUnits()).isEqualTo(45L);

        // Break-Glass
        assertThat(response.breakGlass().totalActivations()).isEqualTo(4L);
        assertThat(response.breakGlass().activeCurrent()).isEqualTo(1L);

        // Auditoría
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).audit(captor.capture());
        AuditEvent evento = captor.getValue();
        assertThat(evento.action()).isEqualTo(AuditableAction.CONSULTA_REPORTE_ADMINISTRATIVO);
        assertThat(evento.userId()).isEqualTo(1L);
        assertThat(evento.resourceType()).isEqualTo("REPORTE_OPERATIVO");
        assertThat(evento.resourcePublicId()).isEqualTo("RESUMEN_OPERATIVO");
    }

    @Test
    @DisplayName("obtenerReporteCitas: calcula métricas específicas y maneja ceros sin error")
    void getReportAppointments_withoutData() {
        when(reportRepository.countAppointmentsByStatus(any(), any())).thenReturn(Map.of());
        when(reportRepository.getAppointmentsBySpecialty(any(), any())).thenReturn(List.of());
        when(reportRepository.getAppointmentsBySite(any(), any())).thenReturn(List.of());

        AppointmentMetricsDto dto = reporteService.getAppointmentsReport(null, null, null, "127.0.0.1");

        assertThat(dto.totalAppointments()).isZero();
        assertThat(dto.complianceRate()).isZero();
        assertThat(dto.cancellationRate()).isZero();
    }

    @Test
    @DisplayName("obtenerReporteTriaje: calcula métricas específicas de triaje")
    void getReportTriage_success() {
        when(reportRepository.countTriagesByLevel(any(), any())).thenReturn(Map.of(
                "IV", 5L,
                "V", 5L
        ));
        when(reportRepository.countEmergencyTriages(any(), any())).thenReturn(0L);
        when(reportRepository.getTriagesByRoute(any(), any())).thenReturn(List.of());

        TriageMetricsDto dto = reporteService.getTriageReport(null, null, null, "127.0.0.1");

        assertThat(dto.totalTriages()).isEqualTo(10L);
        assertThat(dto.emergencies()).isZero();
        assertThat(dto.emergencyRate()).isZero();
    }

    @Test
    @DisplayName("validarRangoFechas: rechaza si fechaDesde es posterior a fechaHasta")
    void validateDateRange_invalid_throwsException() {
        LocalDate desde = LocalDate.of(2026, 10, 10);
        LocalDate hasta = LocalDate.of(2026, 10, 5);

        assertThatThrownBy(() -> reporteService.getOperationalSummary(desde, hasta, "admin", "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("La fecha inicial no puede ser posterior a la fecha final.");
    }
}
