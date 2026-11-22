package com.meditriaje.service;

import com.meditriaje.dto.appointment.AppointmentResponse;
import com.meditriaje.model.AppointmentReminder;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.AppointmentReminderRepository;
import com.meditriaje.repository.UserRepository;
import com.meditriaje.service.email.EmailService;
import com.meditriaje.service.email.EmailTemplateService;
import com.meditriaje.service.email.EmailUtil;
import com.meditriaje.service.email.EmailSendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio encargado del despacho de notificaciones y recordatorios de citas
 * con plantillas HTML institucionales y tolerancia a fallos (ADR-015).
 */
@Service
public class AppointmentNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationService.class);
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(
            "EEEE d 'de' MMMM 'de' yyyy, hh:mm a",
            Locale.forLanguageTag("es-CO")
    );

    private static final String CONFIRMATION_TEMPLATE = "templates/email/confirmacion-cita.html";
    private static final String CANCELLATION_TEMPLATE = "templates/email/cancelacion-cita.html";
    private static final String FOLLOW_UP_SUMMARY_TEMPLATE = "templates/email/resumen-atencion-seguimiento.html";

    private final EmailService emailService;
    private final EmailTemplateService templateService;
    private final AppointmentReminderRepository appointmentReminderRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final String appUrl;

    public AppointmentNotificationService(
            EmailService emailService,
            EmailTemplateService templateService,
            AppointmentReminderRepository appointmentReminderRepository,
            PatientRepository patientRepository,
            UserRepository userRepository,
            @Value("${meditriaje.app-url:http://localhost:8080}") String appUrl
    ) {
        this.emailService = Objects.requireNonNull(emailService, "emailService no puede ser nulo");
        this.templateService = Objects.requireNonNull(templateService, "templateService no puede ser nulo");
        this.appointmentReminderRepository = Objects.requireNonNull(appointmentReminderRepository, "recordatorioCitaRepository no puede ser nulo");
        this.patientRepository = Objects.requireNonNull(patientRepository, "pacienteRepository no puede ser nulo");
        this.userRepository = Objects.requireNonNull(userRepository, "usuarioRepository no puede ser nulo");
        this.appUrl = appUrl != null && !appUrl.isBlank() ? appUrl : "http://localhost:8080";
    }

    /**
     * Envía un correo con la confirmación de la cita reservada y registra la auditoría en RECORDATORIO_CITA.
     */
    public void sendBookingConfirmation(Long citaId, Long pacienteId, String emailDestinatario, AppointmentResponse cita) {
        if (cita == null || emailDestinatario == null || emailDestinatario.isBlank()) {
            return;
        }

        String fechaHoraTexto = formatDateTime(cita.startDateTime());
        String modalidadTexto = "PRESENCIAL".equalsIgnoreCase(cita.modality()) ? "Presencial" : "Telemedicina";
        String sedeNombre = cita.siteName() != null ? cita.siteName() : "Sede Asistencial Principal";
        String sedeDireccion = cita.siteAddress() != null ? cita.siteAddress() : "Consulta Externa";

        Map<String, String> variables = Map.of(
                "pacienteNombre", cita.patientName() != null ? cita.patientName() : "Paciente",
                "especialidadNombre", cita.specialtyName() != null ? cita.specialtyName() : "Medicina General",
                "profesionalNombre", cita.professionalName() != null ? cita.professionalName() : "Profesional Asistencial",
                "fechaHoraBogota", fechaHoraTexto,
                "modalidad", modalidadTexto,
                "sedeNombre", sedeNombre,
                "sedeDireccion", sedeDireccion,
                "codigoCita", cita.publicId(),
                "appUrl", appUrl
        );

        String cuerpoHtml = templateService.render(CONFIRMATION_TEMPLATE, variables);
        String asunto = "MediTriaje — Confirmación de Cita Médica (" + cita.specialtyName() + ")";

        dispatchAndRecord(citaId, pacienteId, emailDestinatario, "CONFIRMACION_RESERVA", asunto, cuerpoHtml);
    }

    /**
     * Envía un correo notificando la cancelación de la cita médica y registra el evento en RECORDATORIO_CITA.
     */
    public void sendCancellationNotification(Long citaId, Long pacienteId, String emailDestinatario, AppointmentResponse cita, String motivo) {
        if (cita == null || emailDestinatario == null || emailDestinatario.isBlank()) {
            return;
        }

        String fechaHoraTexto = formatDateTime(cita.startDateTime());
        String motivoTexto = (motivo != null && !motivo.isBlank())
                ? motivo
                : "Cancelación solicitada por el usuario o personal asistencial";

        Map<String, String> variables = Map.of(
                "pacienteNombre", cita.patientName() != null ? cita.patientName() : "Paciente",
                "especialidadNombre", cita.specialtyName() != null ? cita.specialtyName() : "Medicina General",
                "profesionalNombre", cita.professionalName() != null ? cita.professionalName() : "Profesional Asistencial",
                "fechaHoraBogota", fechaHoraTexto,
                "motivoCancelacion", motivoTexto,
                "codigoCita", cita.publicId(),
                "appUrl", appUrl
        );

        String cuerpoHtml = templateService.render(CANCELLATION_TEMPLATE, variables);
        String asunto = "MediTriaje — Cancelación de Cita Médica (" + cita.specialtyName() + ")";

        dispatchAndRecord(citaId, pacienteId, emailDestinatario, "CANCELACION", asunto, cuerpoHtml);
    }

    /**
     * Envía un correo con el resumen de atención médica e indicaciones de seguimiento post-atención.
     */
    public void sendFollowUpAttentionSummary(
            String emailDestinatario,
            String pacienteNombre,
            String profesionalNombre,
            String especialidadNombre,
            String fechaAtencion,
            String tipoSeguimiento,
            String indicaciones,
            String fechaSugeridaControl
    ) {
        if (emailDestinatario == null || emailDestinatario.isBlank()) {
            return;
        }

        Map<String, String> variables = Map.of(
                "pacienteNombre", pacienteNombre != null ? pacienteNombre : "Paciente",
                "profesionalNombre", profesionalNombre != null ? profesionalNombre : "Profesional Asistencial",
                "especialidadNombre", especialidadNombre != null ? especialidadNombre : "Consulta Médica",
                "fechaAtencion", fechaAtencion != null ? fechaAtencion : "",
                "tipoSeguimiento", tipoSeguimiento != null ? tipoSeguimiento : "Control Ambulatorio",
                "indicaciones", indicaciones != null ? indicaciones : "Continuar con las recomendaciones dadas en consulta.",
                "fechaSugeridaControl", fechaSugeridaControl != null ? fechaSugeridaControl : "Según evolución clínica",
                "appUrl", appUrl
        );

        String cuerpoHtml = templateService.render(FOLLOW_UP_SUMMARY_TEMPLATE, variables);
        String asunto = "MediTriaje — Plan de Cuidado y Seguimiento Post-Atención";

        try {
            EmailSendResult resultado = emailService.sendHtmlEmail(emailDestinatario, asunto, cuerpoHtml);
            if (resultado != null && !resultado.success()) {
                log.warn("Fallo no bloqueante al enviar correo de resumen de atencion a [{}]: {}",
                        EmailUtil.maskEmail(emailDestinatario), resultado.message());
            }
        } catch (Exception ex) {
            log.warn("Fallo no bloqueante al enviar correo de resumen de atencion a [{}]: {}",
                    EmailUtil.maskEmail(emailDestinatario), ex.getMessage());
        }
    }

    /**
     * Resuelve la dirección de correo electrónico del paciente a partir de su ID de dominio.
     */
    public String resolvePatientEmail(Long pacienteId) {
        if (pacienteId == null) {
            return null;
        }
        return patientRepository.findById(pacienteId)
                .filter(paciente -> paciente.userId() != null)
                .flatMap(paciente -> userRepository.findById(paciente.userId()))
                .map(com.meditriaje.model.User::email)
                .orElse(null);
    }

    private void dispatchAndRecord(Long citaId, Long pacienteId, String destinatario, String tipo, String asunto, String cuerpoHtml) {
        String estadoEnvio = "ENVIADO";
        String errorMensaje = null;

        try {
            EmailSendResult resultado = emailService.sendHtmlEmail(destinatario, asunto, cuerpoHtml);
            if (resultado != null && !resultado.success()) {
                estadoEnvio = "FALLIDO";
                errorMensaje = resultado.message();
            }
        } catch (Exception ex) {
            log.warn("Fallo no bloqueante al despachar correo [{}] hacia [{}]: {}",
                    tipo, EmailUtil.maskEmail(destinatario), ex.getMessage());
            estadoEnvio = "FALLIDO";
            errorMensaje = EmailUtil.sanitizeErrorMessage(ex.getMessage(), null);
        }

        if (citaId != null && pacienteId != null) {
            try {
                AppointmentReminder recordatorio = new AppointmentReminder(
                        null,
                        UUID.randomUUID().toString(),
                        citaId,
                        pacienteId,
                        tipo,
                        "EMAIL",
                        destinatario,
                        estadoEnvio,
                        errorMensaje,
                        Instant.now()
                );
                appointmentReminderRepository.save(recordatorio);
            } catch (Exception ex) {
                log.error("No se pudo registrar la trazabilidad del recordatorio en BD para citaId={}: {}", citaId, ex.getMessage());
            }
        }
    }

    private String formatDateTime(Instant instant) {
        if (instant == null) {
            return "";
        }
        ZonedDateTime bogotaTime = instant.atZone(BOGOTA_ZONE);
        String formateado = DATE_TIME_FORMATTER.format(bogotaTime);
        if (!formateado.isEmpty()) {
            formateado = Character.toUpperCase(formateado.charAt(0)) + formateado.substring(1);
        }
        return formateado + " (UTC-5)";
    }
}
