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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio de generación y gestión de slots de disponibilidad médica (HU-10, ADR-002, ADR-003, ADR-005, ADR-006, ADR-011).
 * Toda operación temporal se ejecuta bajo la zona horaria 'America/Bogota' (ADR-005).
 */
@Service
public class SlotGeneratorService {

    public static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private static final String STATUS_ACTIVE = "ACTIVO";
    private static final String STATUS_FREE = "LIBRE";
    private static final String STATUS_BLOCKED = "BLOQUEADO";
    private static final String STATUS_BUSY = "OCUPADO";
    private static final Set<String> ALLOWED_STATUSES = Set.of(STATUS_FREE, STATUS_BLOCKED, STATUS_BUSY);
    private static final String RESOURCE_SLOT = "DISPONIBILIDAD_SLOT";

    private final AvailabilitySlotRepository slotRepository;
    private final ProfessionalRepository professionalRepository;
    private final UserRepository userRepository;
    private final SiteRepository siteRepository;
    private final SpecialtyRepository specialtyRepository;
    private final AuditService auditService;

    public SlotGeneratorService(
            AvailabilitySlotRepository slotRepository,
            ProfessionalRepository professionalRepository,
            UserRepository userRepository,
            SiteRepository siteRepository,
            SpecialtyRepository specialtyRepository,
            AuditService auditService
    ) {
        this.slotRepository = Objects.requireNonNull(slotRepository, "DisponibilidadSlotRepository no puede ser nulo");
        this.professionalRepository = Objects.requireNonNull(professionalRepository, "ProfesionalRepository no puede ser nulo");
        this.userRepository = Objects.requireNonNull(userRepository, "UsuarioRepository no puede ser nulo");
        this.siteRepository = Objects.requireNonNull(siteRepository, "SiteRepository no puede ser nulo");
        this.specialtyRepository = Objects.requireNonNull(specialtyRepository, "EspecialidadRepository no puede ser nulo");
        this.auditService = Objects.requireNonNull(auditService, "AuditoriaService no puede ser nulo");
    }

    /**
     * Genera en lote los slots de disponibilidad según los parámetros indicados.
     * Valida reglas de negocio, inexistencia de solapes y registra auditoría inmutable.
     */
    @Transactional
    public GenerateSlotsResponse generateSlots(GenerateSlotsRequest request, String adminPublicId, String ipOrigen) {
        Objects.requireNonNull(request, "La solicitud de generacion no puede ser nula");

        // 1. Validar profesional existente y con usuario activo
        Professional profesional = professionalRepository.findByPublicId(request.professionalPublicId().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Profesional no encontrado: " + request.professionalPublicId()));

        User usuario = userRepository.findById(profesional.userId())
                .orElseThrow(() -> new InvalidDataException("El profesional no tiene un usuario valido en el sistema."));

        if (!STATUS_ACTIVE.equalsIgnoreCase(usuario.status())) {
            throw new InvalidDataException("El profesional no tiene un usuario activo en el sistema.");
        }

        // 2. Hospital único: los slots se generan siempre en la única sede (la resuelve el servidor)
        Site sede = siteRepository.getSingleSite();

        // 3. Validar rango de fechas (zona America/Bogota)
        LocalDate hoyBogota = LocalDate.now(BOGOTA_ZONE);
        if (request.startDate().isBefore(hoyBogota)) {
            throw new InvalidDataException("La fecha de inicio no puede ser anterior a la fecha actual (" + hoyBogota + ").");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new InvalidDataException("La fecha de fin no puede ser anterior a la fecha de inicio.");
        }
        if (ChronoUnit.DAYS.between(request.startDate(), request.endDate()) > 90) {
            throw new InvalidDataException("El rango de fechas no puede superar los 90 dias.");
        }

        // 4. Validar horas
        if (!request.endTime().isAfter(request.startTime())) {
            throw new InvalidDataException("La hora de fin debe ser posterior a la hora de inicio.");
        }

        // 5. Determinar duracion del slot
        Specialty esp = specialtyRepository.findById(profesional.specialtyId())
                .orElseThrow(() -> new ResourceNotFoundException("Especialidad del profesional"));

        int duracion = (request.durationMinutes() != null)
                ? request.durationMinutes()
                : (esp.slotDurationMinutes() > 0 ? esp.slotDurationMinutes() : 20);

        if (duracion < 5 || duracion > 120) {
            throw new InvalidDataException("La duracion del slot debe estar entre 5 y 120 minutos.");
        }

        // 6. Modalidad
        String modalidad = "PRESENCIAL";
        if (request.modality() != null && !request.modality().isBlank()) {
            modalidad = request.modality().trim().toUpperCase(Locale.ROOT);
            if (!"PRESENCIAL".equals(modalidad) && !"TELEMEDICINA".equals(modalidad)) {
                throw new InvalidDataException("Modalidad invalida. Los valores permitidos son: PRESENCIAL, TELEMEDICINA.");
            }
        }

        // 7. Algoritmo generador de turnos
        List<DayOfWeek> diasSemana = request.daysOfWeek();
        List<AvailabilitySlot> slotsGenerados = new ArrayList<>();

        LocalDate fechaActual = request.startDate();
        while (!fechaActual.isAfter(request.endDate())) {
            if (diasSemana != null && !diasSemana.isEmpty() && !diasSemana.contains(fechaActual.getDayOfWeek())) {
                fechaActual = fechaActual.plusDays(1);
                continue;
            }

            ZonedDateTime windowStart = ZonedDateTime.of(fechaActual, request.startTime(), BOGOTA_ZONE);
            ZonedDateTime windowEnd = ZonedDateTime.of(fechaActual, request.endTime(), BOGOTA_ZONE);

            Instant slotInicio = windowStart.toInstant();
            Instant slotFin = slotInicio.plus(Duration.ofMinutes(duracion));

            while (!slotFin.isAfter(windowEnd.toInstant())) {
                String publicId = UUID.randomUUID().toString();
                AvailabilitySlot slot = new AvailabilitySlot(
                        publicId,
                        profesional.id(),
                        sede.id(),
                        profesional.specialtyId(),
                        slotInicio,
                        slotFin,
                        modalidad,
                        STATUS_FREE
                );
                slotsGenerados.add(slot);

                slotInicio = slotFin;
                slotFin = slotInicio.plus(Duration.ofMinutes(duracion));
            }

            fechaActual = fechaActual.plusDays(1);
        }

        if (slotsGenerados.isEmpty()) {
            throw new InvalidDataException("No fue posible generar ningun slot con los criterios especificados.");
        }

        // 8. Control de solapes con BD
        for (AvailabilitySlot slot : slotsGenerados) {
            if (slotRepository.existsOverlap(profesional.id(), slot.startDateTime(), slot.endDateTime())) {
                ZonedDateTime zdtInicio = slot.startDateTime().atZone(BOGOTA_ZONE);
                ZonedDateTime zdtFin = slot.endDateTime().atZone(BOGOTA_ZONE);
                throw new InvalidDataException(
                        "Existe un solape de horario para el profesional en el intervalo "
                        + zdtInicio.toLocalDateTime() + " - " + zdtFin.toLocalDateTime()
                );
            }
        }

        // 9. Guardar lote
        slotRepository.saveBatch(slotsGenerados);

        // 10. Auditar
        Long adminId = getAdminUserId(adminPublicId);
        auditService.recordEvent(
                adminId,
                AuditableAction.CAMBIO_ADMINISTRATIVO,
                RESOURCE_SLOT,
                profesional.publicId(),
                AuditResult.EXITO,
                ipOrigen
        );

        // 11. Mapear respuesta
        String profesionalNombre = profesional.firstNames() + " " + profesional.lastNames();
        List<SlotResponse> responseList = slotsGenerados.stream()
                .map(s -> new SlotResponse(
                        s.publicId(),
                        profesional.publicId(),
                        profesionalNombre,
                        sede.publicId(),
                        sede.name(),
                        esp.publicId(),
                        esp.name(),
                        s.startDateTime(),
                        s.endDateTime(),
                        s.modality(),
                        s.status()
                ))
                .toList();

        return new GenerateSlotsResponse(
                responseList.size(),
                responseList,
                "Se generaron exitosamente " + responseList.size() + " slots de disponibilidad."
        );
    }

    /**
     * Bloquea un slot que se encuentre en estado LIBRE.
     */
    @Transactional
    public SlotResponse blockSlot(String publicId, String adminPublicId, String ipOrigen) {
        SlotResponse slot = slotRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot no encontrado: " + publicId));

        if (!STATUS_FREE.equalsIgnoreCase(slot.status())) {
            throw new InvalidDataException("Solo se pueden bloquear slots en estado LIBRE. Estado actual: " + slot.status());
        }

        slotRepository.changeStatus(publicId, STATUS_BLOCKED);

        Long adminId = getAdminUserId(adminPublicId);
        auditService.recordEvent(
                adminId,
                AuditableAction.CAMBIO_ADMINISTRATIVO,
                RESOURCE_SLOT,
                publicId,
                AuditResult.EXITO,
                ipOrigen
        );

        return new SlotResponse(
                slot.publicId(),
                slot.professionalPublicId(),
                slot.professionalName(),
                slot.sitePublicId(),
                slot.siteName(),
                slot.specialtyPublicId(),
                slot.specialtyName(),
                slot.startDateTime(),
                slot.endDateTime(),
                slot.modality(),
                STATUS_BLOCKED
        );
    }

    /**
     * Desbloquea un slot que se encuentre en estado BLOQUEADO, regresándolo a LIBRE.
     */
    @Transactional
    public SlotResponse unblockSlot(String publicId, String adminPublicId, String ipOrigen) {
        SlotResponse slot = slotRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot no encontrado: " + publicId));

        if (!STATUS_BLOCKED.equalsIgnoreCase(slot.status())) {
            throw new InvalidDataException("Solo se pueden desbloquear slots en estado BLOQUEADO. Estado actual: " + slot.status());
        }

        slotRepository.changeStatus(publicId, STATUS_FREE);

        Long adminId = getAdminUserId(adminPublicId);
        auditService.recordEvent(
                adminId,
                AuditableAction.CAMBIO_ADMINISTRATIVO,
                RESOURCE_SLOT,
                publicId,
                AuditResult.EXITO,
                ipOrigen
        );

        return new SlotResponse(
                slot.publicId(),
                slot.professionalPublicId(),
                slot.professionalName(),
                slot.sitePublicId(),
                slot.siteName(),
                slot.specialtyPublicId(),
                slot.specialtyName(),
                slot.startDateTime(),
                slot.endDateTime(),
                slot.modality(),
                STATUS_FREE
        );
    }

    /**
     * Elimina físicamente un slot si se encuentra en estado LIBRE.
     */
    @Transactional
    public void deleteSlot(String publicId, String adminPublicId, String ipOrigen) {
        SlotResponse slot = slotRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot no encontrado: " + publicId));

        if (!STATUS_FREE.equalsIgnoreCase(slot.status())) {
            throw new InvalidDataException("Solo se pueden eliminar slots en estado LIBRE. Estado actual: " + slot.status());
        }

        int eliminados = slotRepository.delete(publicId);
        if (eliminados == 0) {
            throw new InvalidDataException("No fue posible eliminar el slot.");
        }

        Long adminId = getAdminUserId(adminPublicId);
        auditService.recordEvent(
                adminId,
                AuditableAction.CAMBIO_ADMINISTRATIVO,
                RESOURCE_SLOT,
                publicId,
                AuditResult.EXITO,
                ipOrigen
        );
    }

    /**
     * Obtiene el detalle de un slot por su publicId.
     */
    public SlotResponse getByPublicId(String publicId) {
        return slotRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot no encontrado: " + publicId));
    }

    /**
     * Lista slots paginados con filtros dinámicos.
     */
    public PaginatedResponse<SlotResponse> list(
            int page,
            int size,
            String profesionalPublicId,
            String especialidadPublicId,
            Instant fechaDesde,
            Instant fechaHasta,
            String estado
    ) {
        String estadoFiltro = null;
        if (estado != null && !estado.isBlank()) {
            estadoFiltro = estado.trim().toUpperCase(Locale.ROOT);
            if (!ALLOWED_STATUSES.contains(estadoFiltro)) {
                throw new InvalidDataException("Estado invalido. Los valores permitidos son: LIBRE, OCUPADO, BLOQUEADO.");
            }
        }

        String profFiltro = (profesionalPublicId != null && !profesionalPublicId.isBlank()) ? profesionalPublicId.trim() : null;
        // Hospital único: no hay filtro de sede.
        String sedeFiltro = null;
        String espFiltro = (especialidadPublicId != null && !especialidadPublicId.isBlank()) ? especialidadPublicId.trim() : null;

        List<SlotResponse> items = slotRepository.list(
                page, size, profFiltro, sedeFiltro, espFiltro, fechaDesde, fechaHasta, estadoFiltro
        );
        int total = slotRepository.count(
                profFiltro, sedeFiltro, espFiltro, fechaDesde, fechaHasta, estadoFiltro
        );

        return PaginatedResponse.of(items, page, size, (long) total);
    }

    private Long getAdminUserId(String adminPublicId) {
        return userRepository.findByPublicId(adminPublicId)
                .map(User::id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario administrador"));
    }
}
