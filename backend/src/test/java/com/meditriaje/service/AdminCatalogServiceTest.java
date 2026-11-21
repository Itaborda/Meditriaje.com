package com.meditriaje.service;

import com.meditriaje.dto.admin.UpdateSpecialtyRequest;
import com.meditriaje.dto.admin.CreateSpecialtyRequest;
import com.meditriaje.dto.admin.SpecialtyResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.Specialty;
import com.meditriaje.model.AuditResult;
import com.meditriaje.model.User;
import com.meditriaje.repository.SpecialtyRepository;
import com.meditriaje.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCatalogServiceTest {

    @Mock
    private SpecialtyRepository specialtyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    private AdminCatalogService catalogService;

    private static final String ADMIN_PUBLIC_ID = "admin-uuid-1";
    private static final Long ADMIN_ID = 10L;
    private static final String IP_ORIGEN = "192.168.1.50";

    @BeforeEach
    void setUp() {
        catalogService = new AdminCatalogService(
                specialtyRepository,
                userRepository,
                auditService
        );
    }

    private void mockAdminUser() {
        User adminUsuario = new User(
                ADMIN_ID,
                ADMIN_PUBLIC_ID,
                "admin@meditriaje.com",
                "hash",
                "ACTIVO",
                0,
                null,
                Instant.now()
        );
        when(userRepository.findByPublicId(ADMIN_PUBLIC_ID)).thenReturn(Optional.of(adminUsuario));
    }

    // =========================================================================
    // ESPECIALIDAD TESTS
    // =========================================================================

    @Test
    void createSpecialty_success_createsAndAudits() {
        mockAdminUser();
        CreateSpecialtyRequest req = new CreateSpecialtyRequest("Medicina General", 20);
        when(specialtyRepository.existsByName("Medicina General")).thenReturn(false);
        when(specialtyRepository.create(anyString(), eq("Medicina General"), eq(20)))
                .thenReturn(new Specialty(1L, "esp-uuid-1", "Medicina General", 20, "ACTIVO"));

        SpecialtyResponse resp = catalogService.createSpecialty(req, ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp).isNotNull();
        assertThat(resp.publicId()).isEqualTo("esp-uuid-1");
        assertThat(resp.name()).isEqualTo("Medicina General");
        assertThat(resp.slotDurationMinutes()).isEqualTo(20);
        assertThat(resp.status()).isEqualTo("ACTIVO");

        verify(auditService).recordEvent(
                eq(ADMIN_ID),
                eq(AuditableAction.CAMBIO_ADMINISTRATIVO),
                eq("ESPECIALIDAD"),
                eq("esp-uuid-1"),
                eq(AuditResult.EXITO),
                eq(IP_ORIGEN)
        );
    }

    @Test
    void createSpecialty_duplicateName_throwsInvalidData() {
        CreateSpecialtyRequest req = new CreateSpecialtyRequest("Medicina General", 20);
        when(specialtyRepository.existsByName("Medicina General")).thenReturn(true);

        assertThatThrownBy(() -> catalogService.createSpecialty(req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Ya existe una especialidad");

        verify(specialtyRepository, never()).create(anyString(), anyString(), anyInt());
        verify(auditService, never()).recordEvent(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void updateSpecialty_success_updatesAndAudits() {
        mockAdminUser();
        UpdateSpecialtyRequest req = new UpdateSpecialtyRequest("Pediatría Integral", 30);
        Specialty actual = new Specialty(2L, "esp-uuid-2", "Pediatría", 20, "ACTIVO");
        when(specialtyRepository.findByPublicId("esp-uuid-2")).thenReturn(Optional.of(actual));
        when(specialtyRepository.existsByNameAndNotPublicId("Pediatría Integral", "esp-uuid-2")).thenReturn(false);

        SpecialtyResponse resp = catalogService.updateSpecialty("esp-uuid-2", req, ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.name()).isEqualTo("Pediatría Integral");
        assertThat(resp.slotDurationMinutes()).isEqualTo(30);

        verify(specialtyRepository).update("esp-uuid-2", "Pediatría Integral", 30);
        verify(auditService).recordEvent(
                eq(ADMIN_ID),
                eq(AuditableAction.CAMBIO_ADMINISTRATIVO),
                eq("ESPECIALIDAD"),
                eq("esp-uuid-2"),
                eq(AuditResult.EXITO),
                eq(IP_ORIGEN)
        );
    }

    @Test
    void updateSpecialty_notFound_throwsResourceNotFound() {
        UpdateSpecialtyRequest req = new UpdateSpecialtyRequest("Pediatría", 30);
        when(specialtyRepository.findByPublicId("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogService.updateSpecialty("no-existe", req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateSpecialty_duplicateName_throwsInvalidData() {
        UpdateSpecialtyRequest req = new UpdateSpecialtyRequest("Cardiología", 30);
        Specialty actual = new Specialty(2L, "esp-uuid-2", "Pediatría", 20, "ACTIVO");
        when(specialtyRepository.findByPublicId("esp-uuid-2")).thenReturn(Optional.of(actual));
        when(specialtyRepository.existsByNameAndNotPublicId("Cardiología", "esp-uuid-2")).thenReturn(true);

        assertThatThrownBy(() -> catalogService.updateSpecialty("esp-uuid-2", req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Ya existe otra especialidad");
    }

    @Test
    void deactivateSpecialty_success_changesStatusToInactive() {
        mockAdminUser();
        Specialty actual = new Specialty(1L, "esp-uuid-1", "Medicina", 20, "ACTIVO");
        when(specialtyRepository.findByPublicId("esp-uuid-1")).thenReturn(Optional.of(actual));

        SpecialtyResponse resp = catalogService.deactivateSpecialty("esp-uuid-1", ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.status()).isEqualTo("INACTIVO");
        verify(specialtyRepository).changeStatus("esp-uuid-1", "INACTIVO");
        verify(auditService).recordEvent(eq(ADMIN_ID), eq(AuditableAction.CAMBIO_ADMINISTRATIVO), eq("ESPECIALIDAD"), eq("esp-uuid-1"), eq(AuditResult.EXITO), eq(IP_ORIGEN));
    }

    @Test
    void activateSpecialty_success_changesStatusToActive() {
        mockAdminUser();
        Specialty actual = new Specialty(1L, "esp-uuid-1", "Medicina", 20, "INACTIVO");
        when(specialtyRepository.findByPublicId("esp-uuid-1")).thenReturn(Optional.of(actual));

        SpecialtyResponse resp = catalogService.activateSpecialty("esp-uuid-1", ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.status()).isEqualTo("ACTIVO");
        verify(specialtyRepository).changeStatus("esp-uuid-1", "ACTIVO");
    }

    @Test
    void listSpecialties_withFilterValid_returnsPaginated() {
        Specialty esp = new Specialty(1L, "esp-1", "Cardiología", 25, "ACTIVO");
        when(specialtyRepository.list(0, 10, "ACTIVO")).thenReturn(List.of(esp));
        when(specialtyRepository.count("ACTIVO")).thenReturn(1L);

        PaginatedResponse<SpecialtyResponse> pag = catalogService.listSpecialties(0, 10, "ACTIVO");

        assertThat(pag.content()).hasSize(1);
        assertThat(pag.totalElements()).isEqualTo(1L);
        assertThat(pag.totalPages()).isEqualTo(1);
        assertThat(pag.hasNext()).isFalse();
        assertThat(pag.hasPrevious()).isFalse();
    }

    @Test
    void listSpecialties_statusInvalid_throwsInvalidData() {
        assertThatThrownBy(() -> catalogService.listSpecialties(0, 10, "PENDIENTE"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Estado invalido");
    }
}
