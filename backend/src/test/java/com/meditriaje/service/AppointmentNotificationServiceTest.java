package com.meditriaje.service;

import com.meditriaje.dto.appointment.AppointmentResponse;
import com.meditriaje.model.Patient;
import com.meditriaje.model.AppointmentReminder;
import com.meditriaje.model.User;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.AppointmentReminderRepository;
import com.meditriaje.repository.UserRepository;
import com.meditriaje.service.email.EmailService;
import com.meditriaje.service.email.EmailTemplateService;
import com.meditriaje.service.email.EmailSendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentNotificationServiceTest {

    @Mock
    private EmailService emailService;

    @Mock
    private EmailTemplateService templateService;

    @Mock
    private AppointmentReminderRepository appointmentReminderRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private UserRepository userRepository;

    private AppointmentNotificationService notificationService;

    private AppointmentResponse dummyCita;

    @BeforeEach
    void setUp() {
        notificationService = new AppointmentNotificationService(
                emailService,
                templateService,
                appointmentReminderRepository,
                patientRepository,
                userRepository,
                "http://localhost:8080"
        );

        dummyCita = new AppointmentResponse(
                "cita-uuid-123",
                "slot-uuid-1",
                "paciente-uuid-1",
                "Carlos Perez",
                "prof-uuid-1",
                "Dr. Roberto Gomez",
                "esp-uuid-1",
                "Medicina General",
                "sede-uuid-1",
                "Sede Principal",
                "Calle 100 #15-20",
                Instant.parse("2026-10-15T13:30:00Z"),
                Instant.parse("2026-10-15T14:00:00Z"),
                "PRESENCIAL",
                "PROGRAMADA",
                null,
                null,
                Instant.now()
        );
    }

    @Test
    @DisplayName("enviarConfirmacionReserva exitoso despacha correo y registra ENVIADO en BD")
    void sendBookingConfirmation_success() {
        when(templateService.render(anyString(), anyMap())).thenReturn("<html>Confirmacion</html>");
        when(emailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(EmailSendResult.succeeded());

        notificationService.sendBookingConfirmation(10L, 20L, "carlos@example.com", dummyCita);

        verify(emailService).sendHtmlEmail(
                eq("carlos@example.com"),
                eq("MediTriaje — Confirmación de Cita Médica (Medicina General)"),
                eq("<html>Confirmacion</html>")
        );

        ArgumentCaptor<AppointmentReminder> captor = ArgumentCaptor.forClass(AppointmentReminder.class);
        verify(appointmentReminderRepository).save(captor.capture());

        AppointmentReminder guardado = captor.getValue();
        assertThat(guardado.appointmentId()).isEqualTo(10L);
        assertThat(guardado.patientId()).isEqualTo(20L);
        assertThat(guardado.type()).isEqualTo("CONFIRMACION_RESERVA");
        assertThat(guardado.recipient()).isEqualTo("carlos@example.com");
        assertThat(guardado.deliveryStatus()).isEqualTo("ENVIADO");
        assertThat(guardado.errorMessage()).isNull();
    }

    @Test
    @DisplayName("enviarConfirmacionReserva ante fallo visible de transporte registra FALLIDO y mensaje de error")
    void sendBookingConfirmation_failureTransport_registraFailed() {
        when(templateService.render(anyString(), anyMap())).thenReturn("<html>Confirmacion</html>");
        when(emailService.sendHtmlEmail(anyString(), anyString(), anyString()))
                .thenReturn(EmailSendResult.failed("BREVO_400", "Sender domain not verified in Brevo"));

        notificationService.sendBookingConfirmation(10L, 20L, "carlos@example.com", dummyCita);

        ArgumentCaptor<AppointmentReminder> captor = ArgumentCaptor.forClass(AppointmentReminder.class);
        verify(appointmentReminderRepository).save(captor.capture());

        AppointmentReminder guardado = captor.getValue();
        assertThat(guardado.deliveryStatus()).isEqualTo("FALLIDO");
        assertThat(guardado.errorMessage()).contains("Sender domain not verified in Brevo");
    }

    @Test
    @DisplayName("enviarConfirmacionReserva ante excepción de transporte no lanza excepción y registra FALLIDO")
    void sendBookingConfirmation_failureSmtp_tolerante() {
        when(templateService.render(anyString(), anyMap())).thenReturn("<html>Confirmacion</html>");
        doThrow(new RuntimeException("Connection refused to mail.server.com:25"))
                .when(emailService).sendHtmlEmail(anyString(), anyString(), anyString());

        assertThatCode(() -> notificationService.sendBookingConfirmation(10L, 20L, "carlos@example.com", dummyCita))
                .doesNotThrowAnyException();

        ArgumentCaptor<AppointmentReminder> captor = ArgumentCaptor.forClass(AppointmentReminder.class);
        verify(appointmentReminderRepository).save(captor.capture());

        AppointmentReminder guardado = captor.getValue();
        assertThat(guardado.deliveryStatus()).isEqualTo("FALLIDO");
        assertThat(guardado.errorMessage()).contains("Connection refused");
    }

    @Test
    @DisplayName("enviarNotificacionCancelacion exitoso despacha correo y registra CANCELACION")
    void sendCancellationNotification_success() {
        when(templateService.render(anyString(), anyMap())).thenReturn("<html>Cancelacion</html>");
        when(emailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(EmailSendResult.succeeded());

        notificationService.sendCancellationNotification(10L, 20L, "carlos@example.com", dummyCita, "Calamidad doméstica");

        verify(emailService).sendHtmlEmail(
                eq("carlos@example.com"),
                eq("MediTriaje — Cancelación de Cita Médica (Medicina General)"),
                eq("<html>Cancelacion</html>")
        );

        ArgumentCaptor<AppointmentReminder> captor = ArgumentCaptor.forClass(AppointmentReminder.class);
        verify(appointmentReminderRepository).save(captor.capture());

        AppointmentReminder guardado = captor.getValue();
        assertThat(guardado.type()).isEqualTo("CANCELACION");
        assertThat(guardado.deliveryStatus()).isEqualTo("ENVIADO");
    }

    @Test
    @DisplayName("enviarNotificacionCancelacion ante fallo SMTP no bloquea flujo")
    void sendCancellationNotification_failureSmtp_tolerante() {
        when(templateService.render(anyString(), anyMap())).thenReturn("<html>Cancelacion</html>");
        doThrow(new RuntimeException("SMTP timeout"))
                .when(emailService).sendHtmlEmail(anyString(), anyString(), anyString());

        assertThatCode(() -> notificationService.sendCancellationNotification(10L, 20L, "carlos@example.com", dummyCita, null))
                .doesNotThrowAnyException();

        ArgumentCaptor<AppointmentReminder> captor = ArgumentCaptor.forClass(AppointmentReminder.class);
        verify(appointmentReminderRepository).save(captor.capture());
        assertThat(captor.getValue().deliveryStatus()).isEqualTo("FALLIDO");
    }

    @Test
    @DisplayName("enviarResumenAtencionSeguimiento despacha correo al paciente")
    void sendFollowUpAttentionSummary_success() {
        when(templateService.render(anyString(), anyMap())).thenReturn("<html>Resumen</html>");

        notificationService.sendFollowUpAttentionSummary(
                "carlos@example.com",
                "Carlos Perez",
                "Dr. Roberto Gomez",
                "Medicina General",
                "15/10/2026",
                "CONTROL_MEDICO",
                "Tomar abundantes liquidos",
                "22/10/2026"
        );

        verify(emailService).sendHtmlEmail(
                eq("carlos@example.com"),
                eq("MediTriaje — Plan de Cuidado y Seguimiento Post-Atención"),
                eq("<html>Resumen</html>")
        );
    }

    @Test
    @DisplayName("resolverEmailPaciente obtiene el correo del usuario vinculado al paciente")
    void resolvePatientEmail_success() {
        Patient paciente = new Patient(20L, 50L, "pac-uuid", "CC", "12345678", "Carlos", "Perez",
                LocalDate.of(1990, 1, 1), "3001234567", Instant.now(), Instant.now());
        User usuario = new User(50L, "user-uuid", "paciente@example.com", "hash", "ACTIVO", 0, null, Instant.now());

        when(patientRepository.findById(20L)).thenReturn(Optional.of(paciente));
        when(userRepository.findById(50L)).thenReturn(Optional.of(usuario));

        String email = notificationService.resolvePatientEmail(20L);

        assertThat(email).isEqualTo("paciente@example.com");
    }

    @Test
    @DisplayName("resolverEmailPaciente retorna null si paciente no existe")
    void resolvePatientEmail_notFound() {
        when(patientRepository.findById(999L)).thenReturn(Optional.empty());

        String email = notificationService.resolvePatientEmail(999L);

        assertThat(email).isNull();
    }
}
