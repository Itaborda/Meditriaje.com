package com.meditriaje.service;

import com.meditriaje.dto.admin.GenerateSlotsRequest;
import com.meditriaje.dto.admin.GenerateSlotsResponse;
import com.meditriaje.dto.admin.SlotResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.AuditableAction;
import com.meditriaje.model.AvailabilitySlot;
import com.meditriaje.model.Specialty;
import com.meditriaje.model.Professional;
import com.meditriaje.model.AuditResult;
import com.meditriaje.model.Site;
import com.meditriaje.model.User;
import com.meditriaje.repository.AvailabilitySlotRepository;
import com.meditriaje.repository.SpecialtyRepository;
import com.meditriaje.repository.ProfessionalRepository;
import com.meditriaje.repository.SiteRepository;
import com.meditriaje.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlotGeneratorServiceTest {

    @Mock
    private AvailabilitySlotRepository slotRepository;

    @Mock
    private ProfessionalRepository professionalRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private SpecialtyRepository specialtyRepository;

    @Mock
    private AuditService auditService;

    private SlotGeneratorService service;

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private static final String ADMIN_UUID = "admin-uuid";
    private static final Long ADMIN_ID = 999L;
    private static final String PROF_UUID = "prof-uuid";
    private static final String SEDE_UUID = "sede-uuid";
    private static final String ESP_UUID = "esp-uuid";

    private Professional profesional;
    private User usuarioProf;
    private Site sede;
    private Specialty especialidad;
    private User adminUser;

    @BeforeEach
    void setUp() {
        service = new SlotGeneratorService(
                slotRepository,
                professionalRepository,
                userRepository,
                siteRepository,
                specialtyRepository,
                auditService
        );

        profesional = new Professional(10L, 100L, PROF_UUID, 5L, "RM-12345", "Carlos", "Perez", Instant.now(), null);
        usuarioProf = new User(100L, "u-prof", "carlos@test.com", "hash", "ACTIVO", 0, null, Instant.now(), false);
        sede = new Site(20L, 1L, SEDE_UUID, "Sede Norte", "Calle 100", "Bogota", "ACTIVO");
        especialidad = new Specialty(5L, ESP_UUID, "Medicina General", 20, "ACTIVO");
        adminUser = new User(ADMIN_ID, ADMIN_UUID, "admin@test.com", "hash", "ACTIVO", 0, null, Instant.now(), false);
    }

    private void stubValidCatalogs() {
        when(professionalRepository.findByPublicId(PROF_UUID)).thenReturn(Optional.of(profesional));
        when(userRepository.findById(100L)).thenReturn(Optional.of(usuarioProf));
        when(siteRepository.getSingleSite()).thenReturn(sede);
        when(specialtyRepository.findById(5L)).thenReturn(Optional.of(especialidad));
    }

    private void mockAdmin() {
        when(userRepository.findByPublicId(ADMIN_UUID)).thenReturn(Optional.of(adminUser));
    }

    // =========================================================================
    // GENERACION DE SLOTS Y REGLAS DE NEGOCIO
    // =========================================================================

    @Test
    void generateSlots_caseExitoso_generatesSlotsAndAudits() {
        stubValidCatalogs();
        mockAdmin();
        LocalDate hoy = LocalDate.now(BOGOTA_ZONE);
        LocalDate fecha = hoy.plusDays(1);

        GenerateSlotsRequest request = new GenerateSlotsRequest(
                PROF_UUID, fecha, fecha,
                LocalTime.of(8, 0), LocalTime.of(9, 0),
                20, "PRESENCIAL", null
        );

        when(slotRepository.existsOverlap(eq(10L), any(Instant.class), any(Instant.class))).thenReturn(false);

        GenerateSlotsResponse response = service.generateSlots(request, ADMIN_UUID, "127.0.0.1");

        assertThat(response.generatedSlots()).isEqualTo(3);
        assertThat(response.slots()).hasSize(3);

        SlotResponse slot1 = response.slots().get(0);
        ZonedDateTime zdt1Inicio = slot1.startDateTime().atZone(BOGOTA_ZONE);
        ZonedDateTime zdt1Fin = slot1.endDateTime().atZone(BOGOTA_ZONE);
        assertThat(zdt1Inicio.toLocalTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(zdt1Fin.toLocalTime()).isEqualTo(LocalTime.of(8, 20));
        assertThat(slot1.professionalName()).isEqualTo("Carlos Perez");
        assertThat(slot1.siteName()).isEqualTo("Sede Norte");
        assertThat(slot1.specialtyName()).isEqualTo("Medicina General");
        assertThat(slot1.modality()).isEqualTo("PRESENCIAL");
        assertThat(slot1.status()).isEqualTo("LIBRE");

        verify(slotRepository).saveBatch(any());
        verify(auditService).recordEvent(
                eq(ADMIN_ID),
                eq(AuditableAction.CAMBIO_ADMINISTRATIVO),
                eq("DISPONIBILIDAD_SLOT"),
                eq(PROF_UUID),
                eq(AuditResult.EXITO),
                eq("127.0.0.1")
        );
    }

    @Test
    void generateSlots_bordeWindowNotDivisibleExact_notExceedsTimeEnd() {
        stubValidCatalogs();
        mockAdmin();
        LocalDate fecha = LocalDate.now(BOGOTA_ZONE).plusDays(2);

        // Ventana de 08:00 a 08:50 con slots de 20 min -> debe generar 2 slots (08:00-08:20 y 08:20-08:40), omitiendo 08:40-09:00
        GenerateSlotsRequest request = new GenerateSlotsRequest(
                PROF_UUID, fecha, fecha,
                LocalTime.of(8, 0), LocalTime.of(8, 50),
                20, "PRESENCIAL", null
        );

        when(slotRepository.existsOverlap(eq(10L), any(Instant.class), any(Instant.class))).thenReturn(false);

        GenerateSlotsResponse response = service.generateSlots(request, ADMIN_UUID, "127.0.0.1");

        assertThat(response.generatedSlots()).isEqualTo(2);
        SlotResponse slot2 = response.slots().get(1);
        ZonedDateTime zdt2Fin = slot2.endDateTime().atZone(BOGOTA_ZONE);
        assertThat(zdt2Fin.toLocalTime()).isEqualTo(LocalTime.of(8, 40));
    }

    @Test
    void generateSlots_filterDaysWeek_appliesOnlyDaysSeleccionados() {
        stubValidCatalogs();
        mockAdmin();
        LocalDate hoy = LocalDate.now(BOGOTA_ZONE);
        // Rango de 7 días
        LocalDate inicio = hoy.plusDays(1);
        LocalDate fin = inicio.plusDays(6);

        // Filtrar únicamente los días lunes
        GenerateSlotsRequest request = new GenerateSlotsRequest(
                PROF_UUID, inicio, fin,
                LocalTime.of(8, 0), LocalTime.of(8, 30),
                30, "PRESENCIAL", List.of(DayOfWeek.MONDAY)
        );

        when(slotRepository.existsOverlap(eq(10L), any(Instant.class), any(Instant.class))).thenReturn(false);

        GenerateSlotsResponse response = service.generateSlots(request, ADMIN_UUID, "127.0.0.1");

        // En 7 días consecutivos hay exactamente 1 lunes
        assertThat(response.generatedSlots()).isEqualTo(1);
        ZonedDateTime slotDate = response.slots().get(0).startDateTime().atZone(BOGOTA_ZONE);
        assertThat(slotDate.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void generateSlots_durationNula_takesDurationOfSpecialty() {
        stubValidCatalogs();
        mockAdmin();
        LocalDate fecha = LocalDate.now(BOGOTA_ZONE).plusDays(1);

        // Especialidad tiene duracion 20
        GenerateSlotsRequest request = new GenerateSlotsRequest(
                PROF_UUID, fecha, fecha,
                LocalTime.of(8, 0), LocalTime.of(8, 40),
                null, "TELEMEDICINA", null
        );

        when(slotRepository.existsOverlap(eq(10L), any(Instant.class), any(Instant.class))).thenReturn(false);

        GenerateSlotsResponse response = service.generateSlots(request, ADMIN_UUID, "127.0.0.1");

        assertThat(response.generatedSlots()).isEqualTo(2);
        assertThat(response.slots().get(0).modality()).isEqualTo("TELEMEDICINA");
    }

    @Test
    void generateSlots_detectsOverlapWithBD_abortaAndLanzaExcepcion() {
        stubValidCatalogs();
        LocalDate fecha = LocalDate.now(BOGOTA_ZONE).plusDays(1);

        GenerateSlotsRequest request = new GenerateSlotsRequest(
                PROF_UUID, fecha, fecha,
                LocalTime.of(8, 0), LocalTime.of(8, 40),
                20, "PRESENCIAL", null
        );

        // Simular que el segundo slot colisiona
        when(slotRepository.existsOverlap(eq(10L), any(Instant.class), any(Instant.class)))
                .thenReturn(false)
                .thenReturn(true);

        assertThatThrownBy(() -> service.generateSlots(request, ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Existe un solape de horario para el profesional");

        verify(slotRepository, never()).saveBatch(any());
        verify(auditService, never()).recordEvent(any(), any(), any(), any(), any(), any());
    }

    // =========================================================================
    // VALIDACIONES DE ENTRADA Y ESTADOS INACTIVOS
    // =========================================================================

    @Test
    void generateSlots_professionalInexistente_throwsResourceNotFound() {
        when(professionalRepository.findByPublicId("prof-inexistente")).thenReturn(Optional.empty());

        GenerateSlotsRequest request = new GenerateSlotsRequest(
                "prof-inexistente", LocalDate.now(BOGOTA_ZONE).plusDays(1),
                LocalDate.now(BOGOTA_ZONE).plusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 0),
                20, "PRESENCIAL", null
        );

        assertThatThrownBy(() -> service.generateSlots(request, ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Profesional no encontrado");
    }

    @Test
    void generateSlots_professionalWithUserInactive_throwsInvalidData() {
        User usuarioInactivo = new User(100L, "u-prof", "carlos@test.com", "hash", "INACTIVO", 0, null, Instant.now(), false);
        when(professionalRepository.findByPublicId(PROF_UUID)).thenReturn(Optional.of(profesional));
        when(userRepository.findById(100L)).thenReturn(Optional.of(usuarioInactivo));

        GenerateSlotsRequest request = new GenerateSlotsRequest(
                PROF_UUID, LocalDate.now(BOGOTA_ZONE).plusDays(1),
                LocalDate.now(BOGOTA_ZONE).plusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 0),
                20, "PRESENCIAL", null
        );

        assertThatThrownBy(() -> service.generateSlots(request, ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("El profesional no tiene un usuario activo");
    }

    @Test
    void generateSlots_dateStartPreviousToToday_throwsInvalidData() {
        when(professionalRepository.findByPublicId(PROF_UUID)).thenReturn(Optional.of(profesional));
        when(userRepository.findById(100L)).thenReturn(Optional.of(usuarioProf));
        when(siteRepository.getSingleSite()).thenReturn(sede);

        LocalDate ayer = LocalDate.now(BOGOTA_ZONE).minusDays(1);
        GenerateSlotsRequest request = new GenerateSlotsRequest(
                PROF_UUID, ayer, ayer.plusDays(1),
                LocalTime.of(8, 0), LocalTime.of(9, 0),
                20, "PRESENCIAL", null
        );

        assertThatThrownBy(() -> service.generateSlots(request, ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("La fecha de inicio no puede ser anterior");
    }

    @Test
    void generateSlots_dateEndUnderToStart_throwsInvalidData() {
        when(professionalRepository.findByPublicId(PROF_UUID)).thenReturn(Optional.of(profesional));
        when(userRepository.findById(100L)).thenReturn(Optional.of(usuarioProf));
        when(siteRepository.getSingleSite()).thenReturn(sede);

        LocalDate fecha = LocalDate.now(BOGOTA_ZONE).plusDays(5);
        GenerateSlotsRequest request = new GenerateSlotsRequest(
                PROF_UUID, fecha, fecha.minusDays(1),
                LocalTime.of(8, 0), LocalTime.of(9, 0),
                20, "PRESENCIAL", null
        );

        assertThatThrownBy(() -> service.generateSlots(request, ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("La fecha de fin no puede ser anterior");
    }

    @Test
    void generateSlots_timeEndUnderOrEqualToStart_throwsInvalidData() {
        when(professionalRepository.findByPublicId(PROF_UUID)).thenReturn(Optional.of(profesional));
        when(userRepository.findById(100L)).thenReturn(Optional.of(usuarioProf));
        when(siteRepository.getSingleSite()).thenReturn(sede);

        LocalDate fecha = LocalDate.now(BOGOTA_ZONE).plusDays(1);
        GenerateSlotsRequest request = new GenerateSlotsRequest(
                PROF_UUID, fecha, fecha,
                LocalTime.of(10, 0), LocalTime.of(9, 0),
                20, "PRESENCIAL", null
        );

        assertThatThrownBy(() -> service.generateSlots(request, ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("La hora de fin debe ser posterior");
    }

    @Test
    void generateSlots_modalityInvalid_throwsInvalidData() {
        stubValidCatalogs();
        LocalDate fecha = LocalDate.now(BOGOTA_ZONE).plusDays(1);

        GenerateSlotsRequest request = new GenerateSlotsRequest(
                PROF_UUID, fecha, fecha,
                LocalTime.of(8, 0), LocalTime.of(9, 0),
                20, "DOMICILIARIA", null
        );

        assertThatThrownBy(() -> service.generateSlots(request, ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Modalidad invalida");
    }

    @Test
    void generateSlots_withoutSlotsGenerados_throwsInvalidData() {
        stubValidCatalogs();
        LocalDate fecha = LocalDate.now(BOGOTA_ZONE).plusDays(1);

        // Ventana de 10 min pero slot dura 20 min -> genera 0 slots
        GenerateSlotsRequest request = new GenerateSlotsRequest(
                PROF_UUID, fecha, fecha,
                LocalTime.of(8, 0), LocalTime.of(8, 10),
                20, "PRESENCIAL", null
        );

        assertThatThrownBy(() -> service.generateSlots(request, ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("No fue posible generar ningun slot");
    }

    // =========================================================================
    // BLOQUEO, DESBLOQUEO Y ELIMINACION
    // =========================================================================

    @Test
    void blockSlot_slotInFreeStatus_changesToBlocked() {
        SlotResponse slot = new SlotResponse(
                "slot-1", PROF_UUID, "Carlos Perez", SEDE_UUID, "Sede Norte",
                ESP_UUID, "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "LIBRE"
        );
        when(slotRepository.findByPublicId("slot-1")).thenReturn(Optional.of(slot));
        when(userRepository.findByPublicId(ADMIN_UUID)).thenReturn(Optional.of(adminUser));

        SlotResponse resultado = service.blockSlot("slot-1", ADMIN_UUID, "127.0.0.1");

        assertThat(resultado.status()).isEqualTo("BLOQUEADO");
        verify(slotRepository).changeStatus("slot-1", "BLOQUEADO");
        verify(auditService).recordEvent(
                eq(ADMIN_ID),
                eq(AuditableAction.CAMBIO_ADMINISTRATIVO),
                eq("DISPONIBILIDAD_SLOT"),
                eq("slot-1"),
                eq(AuditResult.EXITO),
                eq("127.0.0.1")
        );
    }

    @Test
    void blockSlot_slotNotFree_throwsInvalidData() {
        SlotResponse slot = new SlotResponse(
                "slot-1", PROF_UUID, "Carlos Perez", SEDE_UUID, "Sede Norte",
                ESP_UUID, "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "OCUPADO"
        );
        when(slotRepository.findByPublicId("slot-1")).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.blockSlot("slot-1", ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Solo se pueden bloquear slots en estado LIBRE");
    }

    @Test
    void unblockSlot_slotInBlockedStatus_changesToFree() {
        SlotResponse slot = new SlotResponse(
                "slot-1", PROF_UUID, "Carlos Perez", SEDE_UUID, "Sede Norte",
                ESP_UUID, "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "BLOQUEADO"
        );
        when(slotRepository.findByPublicId("slot-1")).thenReturn(Optional.of(slot));
        when(userRepository.findByPublicId(ADMIN_UUID)).thenReturn(Optional.of(adminUser));

        SlotResponse resultado = service.unblockSlot("slot-1", ADMIN_UUID, "127.0.0.1");

        assertThat(resultado.status()).isEqualTo("LIBRE");
        verify(slotRepository).changeStatus("slot-1", "LIBRE");
        verify(auditService).recordEvent(
                eq(ADMIN_ID),
                eq(AuditableAction.CAMBIO_ADMINISTRATIVO),
                eq("DISPONIBILIDAD_SLOT"),
                eq("slot-1"),
                eq(AuditResult.EXITO),
                eq("127.0.0.1")
        );
    }

    @Test
    void unblockSlot_slotNotBlocked_throwsInvalidData() {
        SlotResponse slot = new SlotResponse(
                "slot-1", PROF_UUID, "Carlos Perez", SEDE_UUID, "Sede Norte",
                ESP_UUID, "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "LIBRE"
        );
        when(slotRepository.findByPublicId("slot-1")).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.unblockSlot("slot-1", ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Solo se pueden desbloquear slots en estado BLOQUEADO");
    }

    @Test
    void deleteSlot_slotInFreeStatus_deletesPhysically() {
        SlotResponse slot = new SlotResponse(
                "slot-1", PROF_UUID, "Carlos Perez", SEDE_UUID, "Sede Norte",
                ESP_UUID, "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "LIBRE"
        );
        when(slotRepository.findByPublicId("slot-1")).thenReturn(Optional.of(slot));
        when(slotRepository.delete("slot-1")).thenReturn(1);
        when(userRepository.findByPublicId(ADMIN_UUID)).thenReturn(Optional.of(adminUser));

        service.deleteSlot("slot-1", ADMIN_UUID, "127.0.0.1");

        verify(slotRepository).delete("slot-1");
        verify(auditService).recordEvent(
                eq(ADMIN_ID),
                eq(AuditableAction.CAMBIO_ADMINISTRATIVO),
                eq("DISPONIBILIDAD_SLOT"),
                eq("slot-1"),
                eq(AuditResult.EXITO),
                eq("127.0.0.1")
        );
    }

    @Test
    void deleteSlot_slotNotFree_throwsInvalidData() {
        SlotResponse slot = new SlotResponse(
                "slot-1", PROF_UUID, "Carlos Perez", SEDE_UUID, "Sede Norte",
                ESP_UUID, "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "BLOQUEADO"
        );
        when(slotRepository.findByPublicId("slot-1")).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.deleteSlot("slot-1", ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Solo se pueden eliminar slots en estado LIBRE");
    }

    @Test
    void list_withFiltersValid_returnsPaginated() {
        when(slotRepository.list(eq(0), eq(10), any(), any(), any(), any(), any(), eq("LIBRE")))
                .thenReturn(List.of());
        when(slotRepository.count(any(), any(), any(), any(), any(), eq("LIBRE")))
                .thenReturn(0);

        PaginatedResponse<SlotResponse> resp = service.list(
                0, 10, null, null, null, null, "LIBRE"
        );

        assertThat(resp.content()).isEmpty();
        assertThat(resp.totalElements()).isZero();
    }

    @Test
    void list_statusInvalid_throwsInvalidData() {
        assertThatThrownBy(() -> service.list(0, 10, null, null, null, null, "INVALIDO"))
                .isInstanceOf(InvalidDataException.class)
                .hasMessageContaining("Estado invalido");
    }
}
