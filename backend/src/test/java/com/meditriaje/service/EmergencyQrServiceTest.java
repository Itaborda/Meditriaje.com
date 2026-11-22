package com.meditriaje.service;

import com.meditriaje.dto.emergency.QrAccessResponse;
import com.meditriaje.dto.emergency.GenerateQrRequest;
import com.meditriaje.dto.emergency.GenerateQrResponse;
import com.meditriaje.dto.emergency.VerifyQrResponse;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.TemporaryQrAccess;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.Patient;
import com.meditriaje.model.User;
import com.meditriaje.repository.TemporaryQrAccessRepository;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.UserRepository;
import com.meditriaje.security.TokenHashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmergencyQrServiceTest {

    @Mock
    private TemporaryQrAccessRepository temporaryQrAccessRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private Clock fixedClock;
    private Instant fixedInstant;

    private EmergencyQrService emergencyQrService;

    private User usuarioPaciente;
    private Patient paciente;

    @BeforeEach
    void setUp() {
        fixedInstant = Instant.parse("2026-10-04T12:00:00Z");
        fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        emergencyQrService = new EmergencyQrService(
                temporaryQrAccessRepository,
                patientRepository,
                userRepository,
                auditService,
                passwordEncoder,
                fixedClock
        );

        usuarioPaciente = new User(
                10L,
                "user-pac-uuid",
                "paciente@test.com",
                "hash",
                "ACTIVO",
                0,
                null,
                fixedInstant,
                false,
                false,
                null,
                null
        );

        paciente = new Patient(
                20L,
                10L,
                "pac-uuid-1",
                "CC",
                "123456789",
                "Carlos",
                "Perez",
                LocalDate.of(1990, 5, 15),
                "3001234567",
                fixedInstant,
                fixedInstant
        );
    }

    @Test
    @DisplayName("generarAccesoQr: genera token de 256 bits, hash SHA-256, expira en 15 min y audita")
    void generateAccessQr_successWithoutPin() {
        when(userRepository.findByPublicId("user-pac-uuid")).thenReturn(Optional.of(usuarioPaciente));
        when(patientRepository.findByUserId(10L)).thenReturn(Optional.of(paciente));
        when(temporaryQrAccessRepository.create(any(TemporaryQrAccess.class))).thenReturn(100L);

        GenerateQrRequest request = new GenerateQrRequest(null, true, true, true, true);
        GenerateQrResponse response = emergencyQrService.generateQrAccess(request, "user-pac-uuid", "192.168.1.1");

        assertThat(response).isNotNull();
        assertThat(response.token()).isNotBlank();
        assertThat(response.token().length()).isGreaterThan(30);
        assertThat(response.qrUrl()).isEqualTo("#/emergency-summary/" + response.token());
        assertThat(response.expiresAt()).isEqualTo(fixedInstant.plus(15, ChronoUnit.MINUTES));
        assertThat(response.maxAccesses()).isEqualTo(3);
        assertThat(response.requiresPin()).isFalse();
        assertThat(response.includeAllergies()).isTrue();
        assertThat(response.includeMedications()).isTrue();
        assertThat(response.includeAttentions()).isTrue();
        assertThat(response.includeContact()).isTrue();

        ArgumentCaptor<TemporaryQrAccess> captor = ArgumentCaptor.forClass(TemporaryQrAccess.class);
        verify(temporaryQrAccessRepository).create(captor.capture());
        TemporaryQrAccess guardado = captor.getValue();
        assertThat(guardado.patientId()).isEqualTo(20L);
        assertThat(guardado.tokenHash()).isEqualTo(TokenHashUtil.hash(response.token()));
        assertThat(guardado.pinHash()).isNull();
        assertThat(guardado.maxAccesses()).isEqualTo(3);
        assertThat(guardado.accessCount()).isZero();
        assertThat(guardado.revoked()).isFalse();

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).audit(auditCaptor.capture());
        AuditEvent evento = auditCaptor.getValue();
        assertThat(evento.action()).isEqualTo(AuditableAction.GENERACION_QR_EMERGENCIA);
        assertThat(evento.resourceType()).isEqualTo("ACCESO_TEMPORAL_QR");
        assertThat(evento.resourcePublicId()).isEqualTo(response.publicId());
        assertThat(evento.sourceIp()).isEqualTo("192.168.1.1");
    }

    @Test
    @DisplayName("generarAccesoQr: con PIN numérico de 4 dígitos, lo hashea con PasswordEncoder")
    void generateAccessQr_successWithPin() {
        when(userRepository.findByPublicId("user-pac-uuid")).thenReturn(Optional.of(usuarioPaciente));
        when(patientRepository.findByUserId(10L)).thenReturn(Optional.of(paciente));
        when(passwordEncoder.encode("4321")).thenReturn("argon2id$hashedpin");
        when(temporaryQrAccessRepository.create(any(TemporaryQrAccess.class))).thenReturn(101L);

        GenerateQrRequest request = new GenerateQrRequest("4321", true, false, true, false);
        GenerateQrResponse response = emergencyQrService.generateQrAccess(request, "user-pac-uuid", "127.0.0.1");

        assertThat(response.requiresPin()).isTrue();
        assertThat(response.includeMedications()).isFalse();
        assertThat(response.includeContact()).isFalse();

        ArgumentCaptor<TemporaryQrAccess> captor = ArgumentCaptor.forClass(TemporaryQrAccess.class);
        verify(temporaryQrAccessRepository).create(captor.capture());
        assertThat(captor.getValue().pinHash()).isEqualTo("argon2id$hashedpin");
        assertThat(captor.getValue().includeMedications()).isFalse();
    }

    @Test
    @DisplayName("generarAccesoQr: lanza AccesoNoAutorizadoException si usuario no es paciente")
    void generateAccessQr_userNotPatient_throwsException() {
        when(userRepository.findByPublicId("user-pac-uuid")).thenReturn(Optional.of(usuarioPaciente));
        when(patientRepository.findByUserId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emergencyQrService.generateQrAccess(new GenerateQrRequest(null, true, true, true, true), "user-pac-uuid", "127.0.0.1"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Solo pacientes registrados");
    }

    @Test
    @DisplayName("listarMisAccesosQr: resuelve estado dinámicamente según instante actual")
    void listMyAccessesQr_resolvesStatuses() {
        when(userRepository.findByPublicId("user-pac-uuid")).thenReturn(Optional.of(usuarioPaciente));
        when(patientRepository.findByUserId(10L)).thenReturn(Optional.of(paciente));

        TemporaryQrAccess activo = new TemporaryQrAccess(
                1L, "qr-1", 20L, "hash1", null, true, true, true, true, 3, 1, false,
                fixedInstant.plus(10, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );

        TemporaryQrAccess expirado = new TemporaryQrAccess(
                2L, "qr-2", 20L, "hash2", null, true, true, true, true, 3, 0, false,
                fixedInstant.minus(1, ChronoUnit.MINUTES), fixedInstant.minus(16, ChronoUnit.MINUTES), fixedInstant
        );

        TemporaryQrAccess agotado = new TemporaryQrAccess(
                3L, "qr-3", 20L, "hash3", null, true, true, true, true, 3, 3, false,
                fixedInstant.plus(10, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );

        TemporaryQrAccess revocado = new TemporaryQrAccess(
                4L, "qr-4", 20L, "hash4", null, true, true, true, true, 3, 0, true,
                fixedInstant.plus(10, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );

        when(temporaryQrAccessRepository.listByPatientId(20L))
                .thenReturn(List.of(activo, expirado, agotado, revocado));

        List<QrAccessResponse> lista = emergencyQrService.listMyQrAccesses("user-pac-uuid");

        assertThat(lista).hasSize(4);
        assertThat(lista.get(0).status()).isEqualTo("ACTIVO");
        assertThat(lista.get(1).status()).isEqualTo("EXPIRADO");
        assertThat(lista.get(2).status()).isEqualTo("AGOTADO");
        assertThat(lista.get(3).status()).isEqualTo("REVOCADO");
    }

    @Test
    @DisplayName("revocarAccesoQr: revoca exitosamente y audita si es el paciente titular")
    void revokeAccessQr_success() {
        when(userRepository.findByPublicId("user-pac-uuid")).thenReturn(Optional.of(usuarioPaciente));
        when(patientRepository.findByUserId(10L)).thenReturn(Optional.of(paciente));

        TemporaryQrAccess acceso = new TemporaryQrAccess(
                1L, "qr-uuid", 20L, "hash1", null, true, true, true, true, 3, 0, false,
                fixedInstant.plus(10, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );
        when(temporaryQrAccessRepository.findByPublicId("qr-uuid")).thenReturn(Optional.of(acceso));

        emergencyQrService.revokeQrAccess("qr-uuid", "user-pac-uuid", "192.168.1.1");

        verify(temporaryQrAccessRepository).revoke("qr-uuid", 20L);

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).audit(auditCaptor.capture());
        AuditEvent evento = auditCaptor.getValue();
        assertThat(evento.action()).isEqualTo(AuditableAction.REVOCACION_QR_EMERGENCIA);
        assertThat(evento.resourcePublicId()).isEqualTo("qr-uuid");
    }

    @Test
    @DisplayName("revocarAccesoQr: rechaza si el QR pertenece a otro paciente")
    void revokeAccessQr_otherPatient_throwsException() {
        when(userRepository.findByPublicId("user-pac-uuid")).thenReturn(Optional.of(usuarioPaciente));
        when(patientRepository.findByUserId(10L)).thenReturn(Optional.of(paciente));

        TemporaryQrAccess accesoAjeno = new TemporaryQrAccess(
                1L, "qr-uuid", 999L, "hash1", null, true, true, true, true, 3, 0, false,
                fixedInstant.plus(10, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );
        when(temporaryQrAccessRepository.findByPublicId("qr-uuid")).thenReturn(Optional.of(accesoAjeno));

        assertThatThrownBy(() -> emergencyQrService.revokeQrAccess("qr-uuid", "user-pac-uuid", "192.168.1.1"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("No tiene autorizacion para revocar un acceso QR ajeno");
    }

    @Test
    @DisplayName("verificarTokenPublico: verifica vigencia, estado y requerimiento de PIN")
    void verifyTokenPublic_variousScenarios() {
        String tokenPlano = "secure-random-token-xyz-1234567890";
        String tokenHash = TokenHashUtil.hash(tokenPlano);

        // Caso 1: Token activo con PIN
        TemporaryQrAccess activoConPin = new TemporaryQrAccess(
                1L, "qr-1", 20L, tokenHash, "argon2id$pin", true, true, true, true, 3, 0, false,
                fixedInstant.plus(10, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );
        when(temporaryQrAccessRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(activoConPin));

        VerifyQrResponse resp1 = emergencyQrService.verifyPublicToken(tokenPlano);
        assertThat(resp1.valid()).isTrue();
        assertThat(resp1.requiresPin()).isTrue();
        assertThat(resp1.status()).isEqualTo("ACTIVO");
        assertThat(resp1.expiresAt()).isEqualTo(fixedInstant.plus(10, ChronoUnit.MINUTES));

        // Caso 2: Token no existe
        when(temporaryQrAccessRepository.findByTokenHash(TokenHashUtil.hash("inexistente")))
                .thenReturn(Optional.empty());
        VerifyQrResponse resp2 = emergencyQrService.verifyPublicToken("inexistente");
        assertThat(resp2.valid()).isFalse();
        assertThat(resp2.status()).isEqualTo("EXPIRADO");

        // Caso 3: Token nulo o vacío
        VerifyQrResponse resp3 = emergencyQrService.verifyPublicToken("");
        assertThat(resp3.valid()).isFalse();
    }
}
