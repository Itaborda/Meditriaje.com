package com.meditriaje.service.email;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private EmailTemplateService templateService;
    private DefaultEmailService emailService;

    @Mock
    private JavaMailSender javaMailSender;

    @BeforeEach
    void setUp() {
        templateService = new EmailTemplateService(new DefaultResourceLoader());
        emailService = new DefaultEmailService(
                templateService,
                javaMailSender,
                "no-reply@meditriaje.com",
                "MediTriaje 2.0",
                "http://localhost:5500"
        );
    }

    @Test
    @DisplayName("Debe renderizar la plantilla HTML y registrar el correo en el buffer con resultado exitoso")
    void shouldSendRecoveryEmailWithHtmlTemplate() {
        when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        String email = "paciente.test@ejemplo.com";
        String nombre = "Carlos Mendoza";
        String codigo = "654321";
        int minutosExpiracion = 15;

        EmailSendResult resultado = emailService.sendRecoveryCode(email, nombre, codigo, minutosExpiracion);

        assertThat(resultado.success()).isTrue();
        assertThat(resultado.code()).isEqualTo("OK");

        Optional<SentEmail> enviadoOpt = emailService.getLastSentEmail();
        assertThat(enviadoOpt).isPresent();

        SentEmail enviado = enviadoOpt.get();
        assertThat(enviado.recipient()).isEqualTo(email);
        assertThat(enviado.subject()).contains("Código de recuperación");
        assertThat(enviado.htmlBody())
                .contains("Hola, Carlos Mendoza:")
                .contains("654321")
                .contains("15 minutos")
                .contains("MediTriaje 2.0")
                .contains("Aviso de Seguridad");

        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Debe despachar correo de bienvenida con credenciales iniciales para profesionales")
    void shouldSendInitialCredentialsHtml() {
        when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        String email = "medico.experto@hospital.com.co";
        String nombre = "Dra. Sofía Restrepo";
        String rol = "Profesional Asistencial (Medicina General)";
        String passwordTemporal = "Temp$Pass1234!";

        EmailSendResult resultado = emailService.sendInitialCredentials(email, nombre, rol, passwordTemporal);

        assertThat(resultado.success()).isTrue();
        assertThat(resultado.code()).isEqualTo("OK");

        Optional<SentEmail> enviadoOpt = emailService.getLastSentEmail();
        assertThat(enviadoOpt).isPresent();

        SentEmail enviado = enviadoOpt.get();
        assertThat(enviado.recipient()).isEqualTo(email);
        assertThat(enviado.subject()).contains("credenciales de acceso institucional");
        assertThat(enviado.htmlBody())
                .contains("Dra. Sofía Restrepo")
                .contains("Profesional Asistencial (Medicina General)")
                .contains("medico.experto@hospital.com.co")
                .contains("Temp$Pass1234!")
                .contains("http://localhost:5500/#/login")
                .contains("Aviso obligatorio de seguridad");

        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Resiliencia: si el servidor SMTP falla, retorna ResultadoEnvio fallido visible sin lanzar excepción")
    void resilienceOnSmtpFailure() {
        when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("Error conectando con Brevo SMTP"))
                .when(javaMailSender).send(any(MimeMessage.class));

        EmailSendResult resultado = emailService.sendInitialCredentials("medico@test.com", "Dr. Pérez", "Médico", "Pass#12345");

        assertThat(resultado.success()).isFalse();
        assertThat(resultado.code()).isEqualTo("SMTP_ERROR");
        assertThat(resultado.message()).contains("Error conectando con Brevo SMTP");

        Optional<SentEmail> enviadoOpt = emailService.getLastSentEmail();
        assertThat(enviadoOpt).isPresent();
        assertThat(enviadoOpt.get().recipient()).isEqualTo("medico@test.com");
    }

    @Test
    @DisplayName("Debe usar 'Usuario' por defecto si el nombre es nulo o vacío")
    void shouldUseDefaultName() {
        when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        EmailSendResult resultado = emailService.sendRecoveryCode("user@ejemplo.com", "   ", "123456", 15);
        assertThat(resultado.success()).isTrue();

        Optional<SentEmail> enviadoOpt = emailService.getLastSentEmail();
        assertThat(enviadoOpt).isPresent();
        assertThat(enviadoOpt.get().htmlBody()).contains("Hola, Usuario:");
    }

    @Test
    @DisplayName("enviarCorreoHtml almacena el correo renderizado en el buffer")
    void shouldSendGenericHtmlEmail() {
        when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        String email = "paciente@example.com";
        String asunto = "Prueba de notificación";
        String cuerpo = "<html><body>Hola</body></html>";

        EmailSendResult resultado = emailService.sendHtmlEmail(email, asunto, cuerpo);
        assertThat(resultado.success()).isTrue();

        Optional<SentEmail> enviadoOpt = emailService.getLastSentEmail();
        assertThat(enviadoOpt).isPresent();
        assertThat(enviadoOpt.get().recipient()).isEqualTo(email);
        assertThat(enviadoOpt.get().subject()).isEqualTo(asunto);
        assertThat(enviadoOpt.get().htmlBody()).isEqualTo(cuerpo);
    }

    @Test
    @DisplayName("Las plantillas HTML de confirmación, cancelación y resumen cargan correctamente")
    void templatesHtml_carganCorrectly() {
        String htmlConfirmacion = templateService.render("templates/email/confirmacion-cita.html", java.util.Map.of("pacienteNombre", "Ana"));
        assertThat(htmlConfirmacion).contains("Ana").contains("Confirmación de Cita Médica");

        String htmlCancelacion = templateService.render("templates/email/cancelacion-cita.html", java.util.Map.of("pacienteNombre", "Ana"));
        assertThat(htmlCancelacion).contains("Ana").contains("Cancelación de Cita Médica");

        String htmlResumen = templateService.render("templates/email/resumen-atencion-seguimiento.html", java.util.Map.of("pacienteNombre", "Ana"));
        assertThat(htmlResumen).contains("Ana").contains("Resumen de Atención");

        String htmlBienvenida = templateService.render("templates/email/bienvenida-credenciales.html", java.util.Map.of(
                "nombre", "Dr. Mario",
                "rol", "Pediatra",
                "email", "mario@hospital.com",
                "passwordTemporal", "Pass#12345",
                "enlaceLogin", "http://localhost:5500/#/login"
        ));
        assertThat(htmlBienvenida).contains("Dr. Mario").contains("Pediatra").contains("Pass#12345");
    }

    @Test
    @DisplayName("Garantía de privacidad: Ninguna plantilla de correo contiene variables ni terminología clínica sensible")
    void templatesHtml_notContienenVariablesNorTerminologiaClinical() throws Exception {
        List<String> plantillas = List.of(
                "templates/email/bienvenida-credenciales.html",
                "templates/email/cancelacion-cita.html",
                "templates/email/confirmacion-cita.html",
                "templates/email/recuperacion-password.html",
                "templates/email/resumen-atencion-seguimiento.html"
        );

        List<String> terminosSensibles = List.of(
                "diagnostico", "diagnóstico",
                "cie10", "cie-10", "cie_10",
                "medicamento",
                "signos vitales",
                "presion arterial", "presión arterial",
                "evolucion", "evolución"
        );

        DefaultResourceLoader loader = new DefaultResourceLoader();
        for (String plantilla : plantillas) {
            String contenido = new String(
                    loader.getResource("classpath:" + plantilla).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            ).toLowerCase(Locale.ROOT);

            for (String termino : terminosSensibles) {
                assertThat(contenido)
                        .as("La plantilla '%s' no debe contener el término clínico o variable sensible '%s'", plantilla, termino)
                        .doesNotContain(termino);
            }
        }
    }
}
