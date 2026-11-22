package com.meditriaje.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Implementación principal de EmailService desacoplada mediante EmailTransport (Brevo API, SMTP o Log).
 * Retorna ResultadoEnvio explícito y mantiene buffer en memoria para pruebas y observabilidad (D1, T3).
 */
@Service
public class DefaultEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(DefaultEmailService.class);
    private static final String RECOVERY_TEMPLATE = "templates/email/recuperacion-password.html";
    private static final String WELCOME_TEMPLATE = "templates/email/bienvenida-credenciales.html";

    private final EmailTemplateService templateService;
    private final EmailTransport emailTransport;
    private final String frontendUrl;

    private final ConcurrentLinkedDeque<SentEmail> emailBuffer = new ConcurrentLinkedDeque<>();

    @Autowired
    public DefaultEmailService(
            EmailTemplateService templateService,
            EmailTransport emailTransport,
            @Value("${app.frontend-url:http://localhost:5500}") String frontendUrl
    ) {
        this.templateService = Objects.requireNonNull(templateService, "templateService no puede ser nulo");
        this.emailTransport = (emailTransport != null) ? emailTransport : new LogEmailTransport();
        this.frontendUrl = (frontendUrl != null && !frontendUrl.isBlank()) ? frontendUrl : "http://localhost:5500";
    }

    /**
     * Constructor de conveniencia compatible con pruebas que inyectan transporte directamente.
     */
    public DefaultEmailService(EmailTemplateService templateService, EmailTransport emailTransport) {
        this(templateService, emailTransport, "http://localhost:5500");
    }

    /**
     * Constructor de compatibilidad para pruebas que usan JavaMailSender directamente vía SMTP.
     */
    public DefaultEmailService(
            EmailTemplateService templateService,
            JavaMailSender javaMailSender,
            String mailFrom,
            String mailFromName,
            String frontendUrl
    ) {
        this(templateService, new SmtpEmailTransport(javaMailSender, mailFrom, mailFromName), frontendUrl);
    }

    /**
     * Constructor por defecto para pruebas unitarias sin transporte externo.
     */
    public DefaultEmailService(EmailTemplateService templateService) {
        this(templateService, new LogEmailTransport(), "http://localhost:5500");
    }

    @Override
    public EmailSendResult sendHtmlEmail(String destinatarioEmail, String asunto, String cuerpoHtml) {
        Objects.requireNonNull(destinatarioEmail, "destinatarioEmail no puede ser nulo");
        Objects.requireNonNull(asunto, "asunto no puede ser nulo");
        Objects.requireNonNull(cuerpoHtml, "cuerpoHtml no puede ser nulo");

        SentEmail correo = new SentEmail(destinatarioEmail, asunto, cuerpoHtml, Instant.now());
        emailBuffer.addFirst(correo);

        // Mantener tamaño máximo del buffer en memoria (50 últimos correos)
        while (emailBuffer.size() > 50) {
            emailBuffer.removeLast();
        }

        // Delegar envío al transporte configurado
        EmailSendResult resultado = emailTransport.send(destinatarioEmail, asunto, cuerpoHtml);
        if (!resultado.success()) {
            log.warn("Fallo al enviar correo vía transporte [{}] hacia [{}]: [code={}, msg={}]",
                    emailTransport.getTransportName(),
                    EmailUtil.maskEmail(destinatarioEmail),
                    resultado.code(),
                    resultado.message());
        }
        return resultado;
    }

    @Override
    public EmailSendResult sendRecoveryCode(String destinatarioEmail, String destinatarioNombre, String codigo, int minutosExpiracion) {
        Objects.requireNonNull(destinatarioEmail, "destinatarioEmail no puede ser nulo");
        Objects.requireNonNull(codigo, "codigo no puede ser nulo");

        String nombre = (destinatarioNombre != null && !destinatarioNombre.isBlank()) ? destinatarioNombre : "Usuario";

        Map<String, String> variables = Map.of(
                "nombre", nombre,
                "codigo", codigo,
                "minutosExpiracion", String.valueOf(minutosExpiracion)
        );

        String cuerpoHtml = templateService.render(RECOVERY_TEMPLATE, variables);
        String asunto = "MediTriaje 2.0 — Código de recuperación de contraseña";

        return sendHtmlEmail(destinatarioEmail, asunto, cuerpoHtml);
    }

    @Override
    public EmailSendResult sendInitialCredentials(String destinatarioEmail, String destinatarioNombre, String rol, String passwordTemporal) {
        Objects.requireNonNull(destinatarioEmail, "destinatarioEmail no puede ser nulo");
        Objects.requireNonNull(passwordTemporal, "passwordTemporal no puede ser nula");

        String nombre = (destinatarioNombre != null && !destinatarioNombre.isBlank()) ? destinatarioNombre : "Profesional";
        String rolFormateado = (rol != null && !rol.isBlank()) ? rol : "Profesional Asistencial";
        String enlaceLogin = frontendUrl + "/#/login";

        Map<String, String> variables = Map.of(
                "nombre", nombre,
                "rol", rolFormateado,
                "email", destinatarioEmail,
                "passwordTemporal", passwordTemporal,
                "enlaceLogin", enlaceLogin
        );

        String cuerpoHtml = templateService.render(WELCOME_TEMPLATE, variables);
        String asunto = "MediTriaje 2.0 — Tus credenciales de acceso institucional";

        return sendHtmlEmail(destinatarioEmail, asunto, cuerpoHtml);
    }

    /**
     * Retorna el último correo despachado por el servicio (útil para pruebas).
     */
    public Optional<SentEmail> getLastSentEmail() {
        return Optional.ofNullable(emailBuffer.peekFirst());
    }

    /**
     * Limpia el buffer en memoria de correos despachados.
     */
    public void clearBuffer() {
        emailBuffer.clear();
    }
}
