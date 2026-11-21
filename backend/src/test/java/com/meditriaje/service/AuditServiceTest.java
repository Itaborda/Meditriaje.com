package com.meditriaje.service;

import com.meditriaje.dto.audit.AuditRecordResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.AuditEvent;
import com.meditriaje.model.AuditResult;
import com.meditriaje.repository.AuditRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para {@link AuditoriaService} y modelos de auditoría.
 * Verifica el registro de eventos, consultas supervisadas (ADR-019),
 * ausencia de datos clínicos y validación de campos obligatorios.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditRepository auditRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    void recordEvent_withUser_savesEventCorrectly() {
        Long usuarioId = 42L;
        String publicId = UUID.randomUUID().toString();
        String ip = "192.168.1.100";

        auditService.recordEvent(
                usuarioId,
                AuditableAction.LOGIN_EXITOSO,
                "USUARIO",
                publicId,
                AuditResult.EXITO,
                ip
        );

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRepository).record(captor.capture());

        AuditEvent captured = captor.getValue();
        assertThat(captured.userId()).isEqualTo(usuarioId);
        assertThat(captured.action()).isEqualTo(AuditableAction.LOGIN_EXITOSO);
        assertThat(captured.resourceType()).isEqualTo("USUARIO");
        assertThat(captured.resourcePublicId()).isEqualTo(publicId);
        assertThat(captured.result()).isEqualTo(AuditResult.EXITO);
        assertThat(captured.sourceIp()).isEqualTo(ip);
    }

    @Test
    void recordEvent_withoutUser_allowsUserIdNull() {
        String ip = "10.0.0.1";

        auditService.recordEvent(
                AuditableAction.LOGIN_FALLIDO,
                "AUTH",
                null,
                AuditResult.FALLO,
                ip
        );

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRepository).record(captor.capture());

        AuditEvent captured = captor.getValue();
        assertThat(captured.userId()).isNull();
        assertThat(captured.action()).isEqualTo(AuditableAction.LOGIN_FALLIDO);
        assertThat(captured.result()).isEqualTo(AuditResult.FALLO);
    }

    @Test
    void auditEvent_validFieldsRequired() {
        assertThatThrownBy(() -> new AuditEvent(null, null, "USUARIO", null, AuditResult.EXITO, "127.0.0.1"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("accion");

        assertThatThrownBy(() -> new AuditEvent(null, AuditableAction.LOGIN_EXITOSO, "   ", null, AuditResult.EXITO, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo de recurso");

        assertThatThrownBy(() -> new AuditEvent(null, AuditableAction.LOGIN_EXITOSO, "USUARIO", null, null, "127.0.0.1"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("resultado");

        assertThatThrownBy(() -> new AuditEvent(null, AuditableAction.LOGIN_EXITOSO, "USUARIO", null, AuditResult.EXITO, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IP");
    }

    @Test
    void auditEvent_notContainsFieldsOfContentClinical() {
        // ADR-011: La bitácora nunca debe contener datos clínicos, diagnósticos, evolución, etc.
        List<String> camposProhibidos = List.of(
                "diagnostico", "cie10", "evolucion", "motivo", "signovital",
                "presion", "temperatura", "medicamento", "receta", "dosis", "historia"
        );

        Field[] fields = AuditEvent.class.getDeclaredFields();
        for (Field field : fields) {
            String fieldNameLower = field.getName().toLowerCase();
            for (String prohibido : camposProhibidos) {
                assertThat(fieldNameLower)
                        .as("El modelo de auditoría no debe contener el campo clínico o sensible '%s'", prohibido)
                        .doesNotContain(prohibido);
            }
        }
    }

    @Test
    @DisplayName("consultarBitacora - lanza DatosInvalidosException con fechas invertidas")
    void queryAuditLog_withDatesInvertidas_throwsException() {
        LocalDate desde = LocalDate.of(2026, 10, 10);
        LocalDate hasta = LocalDate.of(2026, 10, 1);

        assertThatThrownBy(() -> auditService.queryAuditLog(desde, hasta, null, null, 0, 20))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("no puede ser posterior");
    }

    @Test
    @DisplayName("consultarBitacora - delega a repositorio y mapea respuesta paginada")
    void queryAuditLog_withParamsValid_returnsResponsePaginada() {
        LocalDate desde = LocalDate.of(2026, 10, 1);
        LocalDate hasta = LocalDate.of(2026, 10, 4);

        AuditRecordResponse evento = new AuditRecordResponse(
                55L,
                "admin@meditriaje.com",
                "CAMBIO_ADMINISTRATIVO",
                "ESPECIALIDAD",
                "esp-1",
                "EXITO",
                "127.0.0.1",
                Instant.now()
        );

        when(auditRepository.listEvents(any(), any(), eq("CAMBIO_ADMINISTRATIVO"), eq("EXITO"), eq(0), eq(15)))
                .thenReturn(List.of(evento));
        when(auditRepository.countEvents(any(), any(), eq("CAMBIO_ADMINISTRATIVO"), eq("EXITO")))
                .thenReturn(1L);

        PaginatedResponse<AuditRecordResponse> resultado =
                auditService.queryAuditLog(desde, hasta, "CAMBIO_ADMINISTRATIVO", "EXITO", 0, 15);

        assertThat(resultado).isNotNull();
        assertThat(resultado.totalElements()).isEqualTo(1L);
        assertThat(resultado.content()).hasSize(1);
        assertThat(resultado.content().get(0).action()).isEqualTo("CAMBIO_ADMINISTRATIVO");
    }

    @Test
    @DisplayName("RegistroAuditoriaResponse - prohíbe exposición de campos clínicos confidenciales")
    void auditRecordResponse_notContainsFieldsOfContentClinical() {
        // ADR-007, ADR-011, ADR-019: La respuesta de auditoría técnica administrativa nunca expone contenido clínico
        List<String> camposProhibidos = List.of(
                "diagnostico", "cie10", "evolucion", "motivo", "signovital",
                "presion", "temperatura", "medicamento", "receta", "dosis", "historia"
        );

        RecordComponent[] components = AuditRecordResponse.class.getRecordComponents();
        for (RecordComponent component : components) {
            String nameLower = component.getName().toLowerCase();
            for (String prohibido : camposProhibidos) {
                assertThat(nameLower)
                        .as("El DTO de auditoría no debe contener el campo clínico '%s'", prohibido)
                        .doesNotContain(prohibido);
            }
        }
    }
}
