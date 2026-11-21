package com.meditriaje.service;

import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.dto.pharmacy.DeliveryDetailRequest;
import com.meditriaje.dto.pharmacy.DispensationDetailResponse;
import com.meditriaje.dto.pharmacy.DispensationResponse;
import com.meditriaje.dto.pharmacy.PrescriptionDispensationResponse;
import com.meditriaje.dto.pharmacy.RegisterDispensationRequest;
import com.meditriaje.dto.pharmacy.MedicationBalanceDto;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.Dispensation;
import com.meditriaje.model.PrescriptionDispensationStatus;
import com.meditriaje.model.Patient;
import com.meditriaje.model.Prescription;
import com.meditriaje.model.AuditResult;
import com.meditriaje.model.Site;
import com.meditriaje.model.User;
import com.meditriaje.repository.DispensationRepository;
import com.meditriaje.repository.DispensationRepository.PrescribedItemInfo;
import com.meditriaje.repository.PatientRepository;
import com.meditriaje.repository.PrescriptionRepository;
import com.meditriaje.repository.SiteRepository;
import com.meditriaje.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispensationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private PrescriptionRepository prescriptionRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private DispensationRepository dispensationRepository;

    @Mock
    private AuditService auditService;

    private final Instant now = Instant.parse("2026-10-04T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneId.of("America/Bogota"));

    private DispensationService service;

    private final List<GrantedAuthority> authoritiesFarmaceutico = List.of(new SimpleGrantedAuthority("ROLE_FARMACEUTICO"));
    private final List<GrantedAuthority> authoritiesPaciente = List.of(new SimpleGrantedAuthority("ROLE_PACIENTE"));
    private final List<GrantedAuthority> authoritiesAdmin = List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));

    @BeforeEach
    void setUp() {
        service = new DispensationService(
                userRepository,
                siteRepository,
                prescriptionRepository,
                patientRepository,
                dispensationRepository,
                auditService,
                clock
        );
    }

    @Test
    @DisplayName("registrarDispensacion - Éxito: dispensa medicamentos, guarda lote INVIMA y audita")
    void registerDispensation_success() {
        User farmaceutico = new User(10L, "usr-farm-1", "farmacia@ejemplo.com", "hash", "ACTIVO", 0, null, null, false, false, null, null);
        Site sede = new Site(20L, 1L, "sede-uuid-1", "Sede Central", "Calle 100", "Bogotá", "ACTIVO");
        Prescription receta = new Prescription(30L, "rec-uuid-1", 100L, 200L, 300L, 30, now.minus(5, ChronoUnit.DAYS));

        when(userRepository.findByPublicId("usr-farm-1")).thenReturn(Optional.of(farmaceutico));
        when(siteRepository.getSingleSite()).thenReturn(sede);
        when(prescriptionRepository.findEntityByPublicId("rec-uuid-1")).thenReturn(Optional.of(receta));

        List<PrescribedItemInfo> items = List.of(
                new PrescribedItemInfo(501L, "med-uuid-1", "Acetaminofen", 20),
                new PrescribedItemInfo(502L, "med-uuid-2", "Ibuprofeno", 10)
        );
        when(dispensationRepository.findPrescriptionItems(30L)).thenReturn(items);
        when(dispensationRepository.getDispensedTotalsByPrescriptionId(30L)).thenReturn(Map.of(501L, 5)); // previo 5 de 20
        when(dispensationRepository.createDispensation(any(Dispensation.class))).thenReturn(888L);

        DispensationResponse mockResp = new DispensationResponse(
                "disp-uuid-abc",
                "rec-uuid-1",
                "sede-uuid-1",
                "Sede Central",
                "usr-farm-1",
                "farmacia@ejemplo.com",
                "Primera entrega",
                now,
                List.of(new DispensationDetailResponse("med-uuid-1", "MED-ACE-500", "Acetaminofen", 10, "LOTE-INV-1", LocalDate.of(2027, 12, 31), now))
        );
        when(dispensationRepository.findByPublicId(anyString())).thenReturn(Optional.of(mockResp));

        RegisterDispensationRequest request = new RegisterDispensationRequest(
                "rec-uuid-1",
                "Primera entrega",
                List.of(new DeliveryDetailRequest("med-uuid-1", 10, "LOTE-INV-1", LocalDate.of(2027, 12, 31)))
        );

        DispensationResponse response = service.registerDispensation(request, "usr-farm-1", authoritiesFarmaceutico, "192.168.1.10");

        assertThat(response).isNotNull();
        assertThat(response.publicId()).isEqualTo("disp-uuid-abc");
        assertThat(response.details()).hasSize(1);
        assertThat(response.details().get(0).deliveredQuantity()).isEqualTo(10);

        verify(dispensationRepository).saveDetails(eq(888L), any());
        verify(auditService).recordEvent(
                eq(10L),
                eq(AuditableAction.DISPENSACION_RECETA),
                eq("RECETA"),
                eq("rec-uuid-1"),
                eq(AuditResult.EXITO),
                eq("192.168.1.10")
        );
    }

    @Test
    @DisplayName("registrarDispensacion - Rechaza solicitud si la receta ha expirado")
    void registerDispensation_rejectsExpiredPrescription() {
        User farmaceutico = new User(10L, "usr-farm-1", "farmacia@ejemplo.com", "hash", "ACTIVO", 0, null, null, false, false, null, null);
        Site sede = new Site(20L, 1L, "sede-uuid-1", "Sede Central", "Calle 100", "Bogotá", "ACTIVO");
        // Emitida hace 35 días con vigencia de 30 días -> Expirada
        Prescription receta = new Prescription(30L, "rec-uuid-1", 100L, 200L, 300L, 30, now.minus(35, ChronoUnit.DAYS));

        when(userRepository.findByPublicId("usr-farm-1")).thenReturn(Optional.of(farmaceutico));
        when(siteRepository.getSingleSite()).thenReturn(sede);
        when(prescriptionRepository.findEntityByPublicId("rec-uuid-1")).thenReturn(Optional.of(receta));

        RegisterDispensationRequest request = new RegisterDispensationRequest(
                "rec-uuid-1",
                null,
                List.of(new DeliveryDetailRequest("med-uuid-1", 5, "LOTE-1", null))
        );

        assertThatThrownBy(() -> service.registerDispensation(request, "usr-farm-1", authoritiesFarmaceutico, "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("ha expirado");

        verify(dispensationRepository, never()).createDispensation(any());
        verify(auditService, never()).recordEvent(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("registrarDispensacion - Rechaza acceso si el usuario no tiene rol ROLE_FARMACEUTICO")
    void registerDispensation_rejectsNonPharmacistRole() {
        RegisterDispensationRequest request = new RegisterDispensationRequest(
                "rec-uuid-1",
                null,
                List.of(new DeliveryDetailRequest("med-uuid-1", 5, "LOTE-1", null))
        );

        assertThatThrownBy(() -> service.registerDispensation(request, "usr-admin-1", authoritiesAdmin, "127.0.0.1"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("ROLE_FARMACEUTICO");

        assertThatThrownBy(() -> service.registerDispensation(request, "usr-pac-1", authoritiesPaciente, "127.0.0.1"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("ROLE_FARMACEUTICO");
    }

    @Test
    @DisplayName("registrarDispensacion - Rechaza sobre-dispensación que supere el saldo pendiente")
    void registerDispensation_rejectsOverDispensation() {
        User farmaceutico = new User(10L, "usr-farm-1", "farmacia@ejemplo.com", "hash", "ACTIVO", 0, null, null, false, false, null, null);
        Site sede = new Site(20L, 1L, "sede-uuid-1", "Sede Central", "Calle 100", "Bogotá", "ACTIVO");
        Prescription receta = new Prescription(30L, "rec-uuid-1", 100L, 200L, 300L, 30, now.minus(2, ChronoUnit.DAYS));

        when(userRepository.findByPublicId("usr-farm-1")).thenReturn(Optional.of(farmaceutico));
        when(siteRepository.getSingleSite()).thenReturn(sede);
        when(prescriptionRepository.findEntityByPublicId("rec-uuid-1")).thenReturn(Optional.of(receta));

        // Prescrito: 10. Ya entregado: 8. Saldo disponible: 2.
        List<PrescribedItemInfo> items = List.of(
                new PrescribedItemInfo(501L, "med-uuid-1", "Amoxicilina", 10)
        );
        when(dispensationRepository.findPrescriptionItems(30L)).thenReturn(items);
        when(dispensationRepository.getDispensedTotalsByPrescriptionId(30L)).thenReturn(Map.of(501L, 8));

        // Solicita 5 (supera saldo de 2)
        RegisterDispensationRequest request = new RegisterDispensationRequest(
                "rec-uuid-1",
                null,
                List.of(new DeliveryDetailRequest("med-uuid-1", 5, "LOTE-1", null))
        );

        assertThatThrownBy(() -> service.registerDispensation(request, "usr-farm-1", authoritiesFarmaceutico, "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("excede el saldo pendiente (2)");
    }

    @Test
    @DisplayName("registrarDispensacion - Rechaza entrega de medicamento ya dispensado en su totalidad")
    void registerDispensation_rejectsAlreadyFullyDispensedMedication() {
        User farmaceutico = new User(10L, "usr-farm-1", "farmacia@ejemplo.com", "hash", "ACTIVO", 0, null, null, false, false, null, null);
        Site sede = new Site(20L, 1L, "sede-uuid-1", "Sede Central", "Calle 100", "Bogotá", "ACTIVO");
        Prescription receta = new Prescription(30L, "rec-uuid-1", 100L, 200L, 300L, 30, now.minus(2, ChronoUnit.DAYS));

        when(userRepository.findByPublicId("usr-farm-1")).thenReturn(Optional.of(farmaceutico));
        when(siteRepository.getSingleSite()).thenReturn(sede);
        when(prescriptionRepository.findEntityByPublicId("rec-uuid-1")).thenReturn(Optional.of(receta));

        // Prescrito: 10. Ya entregado: 10. Saldo: 0.
        List<PrescribedItemInfo> items = List.of(
                new PrescribedItemInfo(501L, "med-uuid-1", "Omeprazol", 10)
        );
        when(dispensationRepository.findPrescriptionItems(30L)).thenReturn(items);
        when(dispensationRepository.getDispensedTotalsByPrescriptionId(30L)).thenReturn(Map.of(501L, 10));

        RegisterDispensationRequest request = new RegisterDispensationRequest(
                "rec-uuid-1",
                null,
                List.of(new DeliveryDetailRequest("med-uuid-1", 1, "LOTE-1", null))
        );

        assertThatThrownBy(() -> service.registerDispensation(request, "usr-farm-1", authoritiesFarmaceutico, "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("ya ha sido dispensado en su totalidad");
    }

    @Test
    @DisplayName("registrarDispensacion - Rechaza medicamento que no pertenece a la receta")
    void registerDispensation_rejectsUnprescribedMedication() {
        User farmaceutico = new User(10L, "usr-farm-1", "farmacia@ejemplo.com", "hash", "ACTIVO", 0, null, null, false, false, null, null);
        Site sede = new Site(20L, 1L, "sede-uuid-1", "Sede Central", "Calle 100", "Bogotá", "ACTIVO");
        Prescription receta = new Prescription(30L, "rec-uuid-1", 100L, 200L, 300L, 30, now.minus(2, ChronoUnit.DAYS));

        when(userRepository.findByPublicId("usr-farm-1")).thenReturn(Optional.of(farmaceutico));
        when(siteRepository.getSingleSite()).thenReturn(sede);
        when(prescriptionRepository.findEntityByPublicId("rec-uuid-1")).thenReturn(Optional.of(receta));

        List<PrescribedItemInfo> items = List.of(
                new PrescribedItemInfo(501L, "med-uuid-1", "Omeprazol", 10)
        );
        when(dispensationRepository.findPrescriptionItems(30L)).thenReturn(items);

        RegisterDispensationRequest request = new RegisterDispensationRequest(
                "rec-uuid-1",
                null,
                List.of(new DeliveryDetailRequest("med-uuid-999", 5, "LOTE-1", null))
        );

        assertThatThrownBy(() -> service.registerDispensation(request, "usr-farm-1", authoritiesFarmaceutico, "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("no forma parte de la receta médica");
    }

    @Test
    @DisplayName("registrarDispensacion - Rechaza medicamentos duplicados en la misma solicitud")
    void registerDispensation_rejectsDuplicateMedicationsInRequest() {
        User farmaceutico = new User(10L, "usr-farm-1", "farmacia@ejemplo.com", "hash", "ACTIVO", 0, null, null, false, false, null, null);
        Site sede = new Site(20L, 1L, "sede-uuid-1", "Sede Central", "Calle 100", "Bogotá", "ACTIVO");
        Prescription receta = new Prescription(30L, "rec-uuid-1", 100L, 200L, 300L, 30, now.minus(2, ChronoUnit.DAYS));

        when(userRepository.findByPublicId("usr-farm-1")).thenReturn(Optional.of(farmaceutico));
        when(siteRepository.getSingleSite()).thenReturn(sede);
        when(prescriptionRepository.findEntityByPublicId("rec-uuid-1")).thenReturn(Optional.of(receta));

        RegisterDispensationRequest request = new RegisterDispensationRequest(
                "rec-uuid-1",
                null,
                List.of(
                        new DeliveryDetailRequest("med-uuid-1", 5, "LOTE-1", null),
                        new DeliveryDetailRequest("med-uuid-1", 5, "LOTE-2", null)
                )
        );

        assertThatThrownBy(() -> service.registerDispensation(request, "usr-farm-1", authoritiesFarmaceutico, "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("duplicados");
    }

    @Test
    @DisplayName("consultarRecetaParaFarmacia - Éxito: retorna saldos y datos de reclamación")
    void getPrescriptionForPharmacy_success() {
        PrescriptionDispensationResponse mockResp = new PrescriptionDispensationResponse(
                "rec-uuid-1",
                "REC-A1B2C3D4",
                "atn-1",
                "pac-1",
                "CC 10203040",
                "Carlos Gomez",
                "prof-1",
                "Dra. Perez",
                "Medicina General",
                30,
                now.minus(5, ChronoUnit.DAYS),
                now.plus(25, ChronoUnit.DAYS),
                false,
                PrescriptionDispensationStatus.PENDIENTE,
                List.of(),
                List.of()
        );
        when(dispensationRepository.findPrescriptionDispensationByPublicId("rec-uuid-1")).thenReturn(Optional.of(mockResp));

        PrescriptionDispensationResponse res = service.getPrescriptionForPharmacy("rec-uuid-1", authoritiesFarmaceutico);

        assertThat(res).isNotNull();
        assertThat(res.prescriptionPublicId()).isEqualTo("rec-uuid-1");
        assertThat(res.claimCode()).isEqualTo("REC-A1B2C3D4");
    }

    @Test
    @DisplayName("buscarRecetasParaFarmacia - Retorna lista paginada")
    void searchPrescriptionsForPharmacy_returnsPaginated() {
        PrescriptionDispensationResponse item = new PrescriptionDispensationResponse(
                "rec-uuid-1", "REC-1", "atn-1", "pac-1", "CC 10203040", "Carlos Gomez", "prof-1", "Dra. Perez",
                "Medicina General", 30, now, now.plus(30, ChronoUnit.DAYS), false, PrescriptionDispensationStatus.PENDIENTE,
                List.of(), List.of()
        );
        when(dispensationRepository.searchPrescriptionDispensations("10203040", 0, 10)).thenReturn(List.of(item));
        when(dispensationRepository.countPrescriptionDispensations("10203040")).thenReturn(1);

        PaginatedResponse<PrescriptionDispensationResponse> pag = service.searchPrescriptionsForPharmacy("10203040", 0, 10, authoritiesFarmaceutico);

        assertThat(pag.content()).hasSize(1);
        assertThat(pag.totalElements()).isEqualTo(1L);
        assertThat(pag.page()).isEqualTo(0);
    }

    @Test
    @DisplayName("consultarDispensacionPaciente - Éxito: paciente consulta su propia receta")
    void getDispensationPatient_success() {
        User userPac = new User(50L, "usr-pac-1", "carlos@ejemplo.com", "hash", "ACTIVO", 0, null, null, false, false, null, null);
        Patient paciente = new Patient(100L, 50L, "pac-uuid-1", "CC", "10203040", "Carlos", "Gomez", LocalDate.of(1990, 1, 1), "3001234567", now, null);
        Prescription receta = new Prescription(30L, "rec-uuid-1", 200L, 100L, 300L, 30, now);

        when(userRepository.findByPublicId("usr-pac-1")).thenReturn(Optional.of(userPac));
        when(patientRepository.findByUserId(50L)).thenReturn(Optional.of(paciente));
        when(prescriptionRepository.findEntityByPublicId("rec-uuid-1")).thenReturn(Optional.of(receta));

        PrescriptionDispensationResponse mockResp = new PrescriptionDispensationResponse(
                "rec-uuid-1", "REC-12345678", "atn-1", "pac-uuid-1", "CC 10203040", "Carlos Gomez", "prof-1", "Dra. Perez",
                "Medicina General", 30, now, now.plus(30, ChronoUnit.DAYS), false, PrescriptionDispensationStatus.PENDIENTE,
                List.of(), List.of()
        );
        when(dispensationRepository.findPrescriptionDispensationByPublicId("rec-uuid-1")).thenReturn(Optional.of(mockResp));

        PrescriptionDispensationResponse res = service.getPatientDispensation("rec-uuid-1", "usr-pac-1");

        assertThat(res).isNotNull();
        assertThat(res.prescriptionPublicId()).isEqualTo("rec-uuid-1");
    }

    @Test
    @DisplayName("consultarDispensacionPaciente - Rechaza consulta de receta ajena")
    void getDispensationPatient_rejectsOtherPatientPrescription() {
        User userPac = new User(50L, "usr-pac-1", "carlos@ejemplo.com", "hash", "ACTIVO", 0, null, null, false, false, null, null);
        Patient paciente = new Patient(100L, 50L, "pac-uuid-1", "CC", "10203040", "Carlos", "Gomez", LocalDate.of(1990, 1, 1), "3001234567", now, null);
        // Receta pertenece al paciente 999L, no al 100L
        Prescription recetaAjena = new Prescription(30L, "rec-uuid-1", 200L, 999L, 300L, 30, now);

        when(userRepository.findByPublicId("usr-pac-1")).thenReturn(Optional.of(userPac));
        when(patientRepository.findByUserId(50L)).thenReturn(Optional.of(paciente));
        when(prescriptionRepository.findEntityByPublicId("rec-uuid-1")).thenReturn(Optional.of(recetaAjena));

        assertThatThrownBy(() -> service.getPatientDispensation("rec-uuid-1", "usr-pac-1"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("otro paciente");
    }
}
