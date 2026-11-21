package com.meditriaje.service;

import com.meditriaje.dto.hospital.BedCensusResponse;
import com.meditriaje.dto.emergency.EmergencyQueueItemResponse;
import com.meditriaje.dto.operational.*;
import com.meditriaje.exception.OperationConflictException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.*;
import com.meditriaje.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Servicio de Analítica Hospitalaria, Alertas Operativas y Seguimiento QR Seguro (Fase O, ADR-027, ADR-028).
 */
@Service
public class OperationalAnalyticsService {

    private final OperationalAlertRepository alertRepository;
    private final TrackingQrRepository trackingQrRepository;
    private final HospitalService hospitalService;
    private final SiteRepository siteRepository;
    private final CareEpisodeRepository episodeRepository;
    private final TriageAssessmentRepository triageRepository;
    private final ProvisionalIdentityRepository identityRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final Clock clock;

    public OperationalAnalyticsService(
            OperationalAlertRepository alertRepository,
            TrackingQrRepository trackingQrRepository,
            HospitalService hospitalService,
            SiteRepository siteRepository,
            CareEpisodeRepository episodeRepository,
            TriageAssessmentRepository triageRepository,
            ProvisionalIdentityRepository identityRepository,
            UserRepository userRepository,
            AuditService auditService,
            Clock clock
    ) {
        this.alertRepository = Objects.requireNonNull(alertRepository);
        this.trackingQrRepository = Objects.requireNonNull(trackingQrRepository);
        this.hospitalService = Objects.requireNonNull(hospitalService);
        this.siteRepository = Objects.requireNonNull(siteRepository);
        this.episodeRepository = Objects.requireNonNull(episodeRepository);
        this.triageRepository = Objects.requireNonNull(triageRepository);
        this.identityRepository = Objects.requireNonNull(identityRepository);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.auditService = Objects.requireNonNull(auditService);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * O01: Construye el panel analítico hospitalario en tiempo real (hospital único).
     */
    @Transactional
    public HospitalDashboardResponse getHospitalDashboard(
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Site sede = siteRepository.getSingleSite();

        // Métricas de camas
        BedCensusResponse censo = hospitalService.getBedCensus();
        int totalCamas = censo.totalBeds();
        int ocupadas = censo.occupied();
        int disponibles = censo.available();
        int limpieza = censo.inCleaning();
        int mantenimiento = censo.inMaintenance();
        double tasaOcupacion = censo.occupancyRatePercentage();

        // Episodios activos de urgencias obtenidos directamente de la cola con triaje y tiempos
        List<EmergencyQueueItemResponse> cola = episodeRepository.listEmergencyQueueBySite(sede.id(), null);

        Map<String, Long> episodiosPorTriaje = new HashMap<>();
        Map<String, List<Long>> minutosEsperaPorTriaje = new HashMap<>();

        for (String n : List.of("I", "II", "III", "IV", "V", "PENDIENTE_VALORACION")) {
            episodiosPorTriaje.put(n, 0L);
            minutosEsperaPorTriaje.put(n, new ArrayList<>());
        }

        for (EmergencyQueueItemResponse item : cola) {
            String nivel = item.triageLevel() != null ? item.triageLevel() : "PENDIENTE_VALORACION";
            episodiosPorTriaje.put(nivel, episodiosPorTriaje.getOrDefault(nivel, 0L) + 1);
            minutosEsperaPorTriaje.computeIfAbsent(nivel, k -> new ArrayList<>()).add(item.waitMinutes());
        }

        Map<String, Double> tiemposPromedioEspera = new HashMap<>();
        minutosEsperaPorTriaje.forEach((k, v) -> {
            double avg = v.isEmpty() ? 0.0 : v.stream().mapToLong(Long::longValue).average().orElse(0.0);
            tiemposPromedioEspera.put(k, Math.round(avg * 10.0) / 10.0);
        });

        // F-03: GET es estrictamente idempotente de consulta; no muta estado en base de datos.
        List<OperationalAlertResponse> alertasActivas = alertRepository.listBySite(sede.id(), "ACTIVA");

        return new HospitalDashboardResponse(
                sede.publicId(),
                sede.name(),
                totalCamas,
                disponibles,
                ocupadas,
                limpieza,
                mantenimiento,
                tasaOcupacion,
                cola.size(),
                episodiosPorTriaje,
                tiemposPromedioEspera,
                alertasActivas
        );
    }

    /**
     * Proceso desacoplado para evaluación y disparo idempotente de alertas operativas (F-03).
     */
    @Transactional
    public void evaluateAutomaticAlerts(
            Site sede,
            double tasaOcupacion,
            Map<String, Long> episodiosPorTriaje,
            Map<String, Double> tiemposPromedio
    ) {
        // Alerta de camas si ocupación >= 85%
        if (tasaOcupacion >= 85.0) {
            String msg = "Saturación hospitalaria crítica: Ocupación de camas al " + tasaOcupacion + "% en " + sede.name();
            createAlertIfMissing(sede.id(), "SATURACION_CAMAS", "CRITICA", msg);
        }

        // Alerta de demora Triaje II > 30 min (Res 5596/2015)
        Double esperaT2 = tiemposPromedio.get("II");
        if (esperaT2 != null && esperaT2 > 30.0 && episodiosPorTriaje.getOrDefault("II", 0L) > 0) {
            String msg = "Demora en Triaje II: Tiempo promedio de espera (" + esperaT2 + " min) supera el estándar normativo de 30 min.";
            createAlertIfMissing(sede.id(), "DEMORA_TRIAJE_II", "ALTA", msg);
        }
    }

    private void createAlertIfMissing(Long sedeId, String tipo, String severidad, String mensaje) {
        List<OperationalAlertResponse> existentes = alertRepository.listBySite(sedeId, "ACTIVA");
        boolean yaExiste = existentes.stream().anyMatch(a -> tipo.equals(a.alertType()));
        if (!yaExiste) {
            try {
                alertRepository.save(new OperationalAlert(
                        null,
                        UUID.randomUUID().toString(),
                        sedeId,
                        tipo,
                        severidad,
                        mensaje,
                        "ACTIVA",
                        null,
                        null,
                        null,
                        Instant.now(clock)
                ));
            } catch (DataIntegrityViolationException | OperationConflictException ignored) {
                // Deduplicado de forma segura por el índice único UQ_ALERTA_OPERATIVA_ACTIVA (V025)
            }
        }
    }

    /**
     * O02: Lista las alertas operativas del hospital.
     */
    public List<OperationalAlertResponse> listAlerts(String estado) {
        // Hospital único: sin filtro de sede se listan todas las alertas.
        return alertRepository.listBySite(null, estado);
    }

    /**
     * O02: Reconoce y silencia auditadamente una alerta operativa.
     */
    @Transactional
    public void acknowledgeAlert(
            String alertaPublicId,
            AcknowledgeAlertRequest req,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        OperationalAlert alerta = alertRepository.findByPublicId(alertaPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Alerta no encontrada: " + alertaPublicId));

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        Instant ahora = Instant.now(clock);
        alertRepository.acknowledgeAlert(alerta.id(), usuario.id(), ahora, req.reason());

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.ALERTA_OPERATIVA_RECONOCIDA,
                "ALERTA_OPERATIVA",
                alertaPublicId,
                AuditResult.EXITO,
                ipOrigen
        ));
    }

    /**
     * O03: Genera o recupera un token QR seguro de seguimiento intrahospitalario para manilla/cabecera.
     */
    @Transactional
    public TrackingQrResponse generateTrackingQr(
            String episodioPublicId,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        CareEpisode ep = episodeRepository.findByPublicId(episodioPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Episodio no encontrado: " + episodioPublicId));

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        Optional<InpatientQrTracking> existenteOpt = trackingQrRepository.findActiveByEpisodeId(ep.id());
        boolean esVencido = existenteOpt.map(q -> q.expiresAt() != null && q.expiresAt().isBefore(Instant.now(clock))).orElse(false);

        InpatientQrTracking qr;
        if (existenteOpt.isPresent() && !esVencido) {
            qr = existenteOpt.get();
        } else {
            String token = "QROP-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
            Instant expira = Instant.now(clock).plus(Duration.ofDays(7));
            qr = trackingQrRepository.save(new InpatientQrTracking(
                    null,
                    UUID.randomUUID().toString(),
                    ep.id(),
                    token,
                    "URGENCIAS / SALA DE OBSERVACION",
                    "ACTIVO",
                    Instant.now(clock),
                    expira
            ));
        }

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.QR_SEGUIMIENTO_GENERADO,
                "SEGUIMIENTO_INTRAHOSPITALARIO_QR",
                qr.publicId(), // S-03: Auditar publicId de recurso, jamás el token secreto
                AuditResult.EXITO,
                ipOrigen
        ));

        String codIdentidad = identityRepository.findByEpisodeId(ep.id())
                .map(ProvisionalIdentity::provisionalCode).orElse("REGISTRADO");

        Optional<TriageAssessment> ultTriaje = triageRepository.findLatestByEpisodeId(ep.id());

        return new TrackingQrResponse(
                qr.publicId(),
                ep.publicId(),
                codIdentidad,
                qr.qrTokenCode(),
                "/operational/tracking/" + qr.qrTokenCode(),
                qr.currentLocationText(),
                ep.status(),
                ultTriaje.map(TriageAssessment::level).orElse("PENDIENTE"),
                qr.status(),
                qr.createdAt(),
                qr.expiresAt()
        );
    }

    /**
     * O03: Consulta la ubicación y estado básico mediante escaneo de QR (sin PHI médica ni nivel clínico, S-03).
     */
    public TrackingQrResponse getByQrToken(String token, String ipOrigen) {
        if (token == null || token.isBlank()) {
            throw new ResourceNotFoundException("Token QR de seguimiento no válido o expirado.");
        }

        InpatientQrTracking qr = trackingQrRepository.findByToken(token.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Token QR de seguimiento no válido o expirado."));

        // S-03: Validar que el QR se encuentre ACTIVO y no haya expirado
        if (!"ACTIVO".equals(qr.status()) || (qr.expiresAt() != null && qr.expiresAt().isBefore(Instant.now(clock)))) {
            throw new ResourceNotFoundException("Token QR de seguimiento no válido o expirado.");
        }

        CareEpisode ep = episodeRepository.findById(qr.episodeId())
                .orElseThrow(() -> new ResourceNotFoundException("Episodio asociado no encontrado."));

        // S-03: Rechazar acceso si el episodio ya egresó o fue cancelado
        if ("EGRESADO".equals(ep.status()) || "CANCELADO".equals(ep.status())) {
            throw new ResourceNotFoundException("Token QR de seguimiento no válido o expirado.");
        }

        String codIdentidad = identityRepository.findByEpisodeId(ep.id())
                .map(ProvisionalIdentity::provisionalCode).orElse("PACIENTE_IDENTIFICADO");

        auditService.audit(new AuditEvent(
                null,
                AuditableAction.QR_SEGUIMIENTO_ESCANEADO,
                "SEGUIMIENTO_INTRAHOSPITALARIO_QR",
                qr.publicId(), // S-03: Auditar publicId de recurso, jamás el token secreto
                AuditResult.EXITO,
                ipOrigen
        ));

        // S-03: En la respuesta pública no divulgar dato clínico sensible (nivelTriaje -> NO_DIVULGABLE)
        return new TrackingQrResponse(
                qr.publicId(),
                ep.publicId(),
                codIdentidad,
                qr.qrTokenCode(),
                "/operational/tracking/" + qr.qrTokenCode(),
                qr.currentLocationText(),
                ep.status(),
                "NO_DIVULGABLE",
                qr.status(),
                qr.createdAt(),
                qr.expiresAt()
        );
    }
}
