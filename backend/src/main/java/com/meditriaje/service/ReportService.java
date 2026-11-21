package com.meditriaje.service;

import com.meditriaje.dto.report.DistributionItemDto;
import com.meditriaje.dto.report.BreakGlassMetricsDto;
import com.meditriaje.dto.report.AppointmentMetricsDto;
import com.meditriaje.dto.report.PharmacyMetricsDto;
import com.meditriaje.dto.report.TriageMetricsDto;
import com.meditriaje.dto.report.OperationalSummaryResponse;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.AuditResult;
import com.meditriaje.model.User;
import com.meditriaje.repository.ReportRepository;
import com.meditriaje.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Servicio de métricas e indicadores operativos hospitalarios (F2.6, RF-30, ADR-018).
 * Asegura estricta segregación de datos clínicos (ADR-007): el personal administrativo
 * accede únicamente a resúmenes matemáticos agregados sin datos de salud individuales.
 */
@Service
public class ReportService {

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final Clock clock;

    public ReportService(
            ReportRepository reportRepository,
            UserRepository userRepository,
            AuditService auditService,
            Clock clock
    ) {
        this.reportRepository = Objects.requireNonNull(reportRepository, "reporteRepository no puede ser nulo");
        this.userRepository = Objects.requireNonNull(userRepository, "usuarioRepository no puede ser nulo");
        this.auditService = Objects.requireNonNull(auditService, "auditoriaService no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    /**
     * Genera el resumen operativo global consolidado (citas, triaje, farmacia y break-glass).
     */
    @Transactional(readOnly = true)
    public OperationalSummaryResponse getOperationalSummary(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            String adminPublicId,
            String ipOrigen
    ) {
        validateDateRange(fechaDesde, fechaHasta);

        Instant desde = fechaDesde != null ? fechaDesde.atStartOfDay(BOGOTA_ZONE).toInstant() : null;
        Instant hasta = fechaHasta != null ? fechaHasta.atTime(LocalTime.MAX).atZone(BOGOTA_ZONE).toInstant() : null;
        Instant ahora = Instant.now(clock);

        AppointmentMetricsDto metricasCitas = computeAppointmentMetrics(desde, hasta);
        TriageMetricsDto metricasTriaje = computeTriageMetrics(desde, hasta);
        PharmacyMetricsDto metricasFarmacia = computePharmacyMetrics(desde, hasta);
        BreakGlassMetricsDto metricasBreakGlass = computeBreakGlassMetrics(desde, hasta, ahora);

        auditReportQuery(adminPublicId, "RESUMEN_OPERATIVO", ipOrigen);

        return new OperationalSummaryResponse(
                desde,
                hasta,
                metricasCitas,
                metricasTriaje,
                metricasFarmacia,
                metricasBreakGlass,
                ahora
        );
    }

    /**
     * Genera el reporte específico de agendamiento y estado de citas.
     */
    @Transactional(readOnly = true)
    public AppointmentMetricsDto getAppointmentsReport(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            String adminPublicId,
            String ipOrigen
    ) {
        validateDateRange(fechaDesde, fechaHasta);

        Instant desde = fechaDesde != null ? fechaDesde.atStartOfDay(BOGOTA_ZONE).toInstant() : null;
        Instant hasta = fechaHasta != null ? fechaHasta.atTime(LocalTime.MAX).atZone(BOGOTA_ZONE).toInstant() : null;

        AppointmentMetricsDto metricas = computeAppointmentMetrics(desde, hasta);
        auditReportQuery(adminPublicId, "CITAS", ipOrigen);
        return metricas;
    }

    /**
     * Genera el reporte específico de triaje clínico y cortes de emergencia.
     */
    @Transactional(readOnly = true)
    public TriageMetricsDto getTriageReport(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            String adminPublicId,
            String ipOrigen
    ) {
        validateDateRange(fechaDesde, fechaHasta);

        Instant desde = fechaDesde != null ? fechaDesde.atStartOfDay(BOGOTA_ZONE).toInstant() : null;
        Instant hasta = fechaHasta != null ? fechaHasta.atTime(LocalTime.MAX).atZone(BOGOTA_ZONE).toInstant() : null;

        TriageMetricsDto metricas = computeTriageMetrics(desde, hasta);
        auditReportQuery(adminPublicId, "TRIAJE", ipOrigen);
        return metricas;
    }

    // -------------------------------------------------------------------------
    // Métodos auxiliares de cálculo
    // -------------------------------------------------------------------------

    private AppointmentMetricsDto computeAppointmentMetrics(Instant desde, Instant hasta) {
        Map<String, Long> estados = reportRepository.countAppointmentsByStatus(desde, hasta);

        long programadas = estados.getOrDefault("PROGRAMADA", 0L);
        long confirmadas = estados.getOrDefault("CONFIRMADA", 0L);
        long atendidas = estados.getOrDefault("ATENDIDA", 0L);
        long canceladas = estados.getOrDefault("CANCELADA", 0L);
        long noAsistio = estados.getOrDefault("NO_ASISTIO", 0L);
        long reprogramadas = estados.getOrDefault("REPROGRAMADA", 0L);

        long total = programadas + confirmadas + atendidas + canceladas + noAsistio + reprogramadas;
        double tasaCumplimiento = total > 0 ? round((atendidas * 100.0) / total) : 0.0;
        double tasaCancelacion = total > 0 ? round(((canceladas + noAsistio) * 100.0) / total) : 0.0;

        List<DistributionItemDto> porEspecialidad = reportRepository.getAppointmentsBySpecialty(desde, hasta);
        List<DistributionItemDto> porSede = reportRepository.getAppointmentsBySite(desde, hasta);

        return new AppointmentMetricsDto(
                total,
                programadas,
                confirmadas,
                atendidas,
                canceladas,
                noAsistio,
                reprogramadas,
                tasaCumplimiento,
                tasaCancelacion,
                porEspecialidad,
                porSede
        );
    }

    private TriageMetricsDto computeTriageMetrics(Instant desde, Instant hasta) {
        Map<String, Long> niveles = reportRepository.countTriagesByLevel(desde, hasta);

        long n1 = niveles.getOrDefault("I", 0L);
        long n2 = niveles.getOrDefault("II", 0L);
        long n3 = niveles.getOrDefault("III", 0L);
        long n4 = niveles.getOrDefault("IV", 0L);
        long n5 = niveles.getOrDefault("V", 0L);

        long total = n1 + n2 + n3 + n4 + n5;
        long emergencias = reportRepository.countEmergencyTriages(desde, hasta);
        double tasaEmergencia = total > 0 ? round((emergencias * 100.0) / total) : 0.0;

        List<DistributionItemDto> porRuta = reportRepository.getTriagesByRoute(desde, hasta);

        return new TriageMetricsDto(
                total,
                n1,
                n2,
                n3,
                n4,
                n5,
                emergencias,
                tasaEmergencia,
                porRuta
        );
    }

    private PharmacyMetricsDto computePharmacyMetrics(Instant desde, Instant hasta) {
        long totalRecetas = reportRepository.countIssuedPrescriptions(desde, hasta);
        Map<String, Long> estadosDisp = reportRepository.countPrescriptionsByDispensationStatus(desde, hasta);

        long pendientes = estadosDisp.getOrDefault("PENDIENTE", 0L);
        long parciales = estadosDisp.getOrDefault("DISPENSADA_PARCIAL", 0L);
        long totales = estadosDisp.getOrDefault("DISPENSADA_TOTAL", 0L);
        long unidades = reportRepository.countDispensedUnits(desde, hasta);

        return new PharmacyMetricsDto(
                totalRecetas,
                pendientes,
                parciales,
                totales,
                unidades
        );
    }

    private BreakGlassMetricsDto computeBreakGlassMetrics(Instant desde, Instant hasta, Instant ahora) {
        long total = reportRepository.countBreakGlassActivations(desde, hasta);
        long activas = reportRepository.countActiveBreakGlass(ahora);
        List<DistributionItemDto> porEsp = reportRepository.getBreakGlassBySpecialty(desde, hasta);

        return new BreakGlassMetricsDto(total, activas, porEsp);
    }

    private void validateDateRange(LocalDate fechaDesde, LocalDate fechaHasta) {
        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            throw new InvalidDataException("La fecha inicial no puede ser posterior a la fecha final.");
        }
    }

    private void auditReportQuery(String adminPublicId, String subtipo, String ipOrigen) {
        Long usuarioId = null;
        if (adminPublicId != null) {
            usuarioId = userRepository.findByPublicId(adminPublicId)
                    .map(User::id)
                    .orElse(null);
        }

        auditService.audit(new AuditEvent(
                usuarioId,
                AuditableAction.CONSULTA_REPORTE_ADMINISTRATIVO,
                "REPORTE_OPERATIVO",
                subtipo,
                AuditResult.EXITO,
                ipOrigen
        ));
    }

    private double round(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
