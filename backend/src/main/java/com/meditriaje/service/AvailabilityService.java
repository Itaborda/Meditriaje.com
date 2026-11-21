package com.meditriaje.service;

import com.meditriaje.dto.availability.AvailabilitySlotResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.repository.AvailabilitySlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Servicio para la consulta pública y asistencial de disponibilidad de citas (HU-03, ADR-003, ADR-005, ADR-006).
 * Toda operación temporal se ejecuta bajo la zona horaria 'America/Bogota' (ADR-005).
 */
@Service
@Transactional(readOnly = true)
public class AvailabilityService {

    public static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private static final Set<String> ALLOWED_MODALITIES = Set.of("PRESENCIAL", "TELEMEDICINA");

    private final AvailabilitySlotRepository slotRepository;
    private final Clock clock;

    @Autowired
    public AvailabilityService(AvailabilitySlotRepository slotRepository) {
        this(slotRepository, Clock.systemUTC());
    }

    public AvailabilityService(AvailabilitySlotRepository slotRepository, Clock clock) {
        this.slotRepository = Objects.requireNonNull(slotRepository, "DisponibilidadSlotRepository no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "Clock no puede ser nulo");
    }

    /**
     * Consulta disponibilidad de slots libres futuros con filtros opcionales y paginación.
     *
     * @param especialidadPublicId UUID público de la especialidad (opcional)
     * @param modalidad Modalidad de atención ('PRESENCIAL' o 'TELEMEDICINA', opcional)
     * @param fecha Fecha específica a consultar en zona horaria America/Bogota (opcional)
     * @param page Número de página (>= 0)
     * @param size Tamaño de página (1 a 100)
     * @return Respuesta paginada con slots disponibles
     */
    public PaginatedResponse<AvailabilitySlotResponse> checkAvailability(
            String especialidadPublicId,
            String modalidad,
            LocalDate fecha,
            int page,
            int size
    ) {
        return checkAvailability(especialidadPublicId, null, modalidad, fecha, page, size);
    }

    /**
     * Consulta disponibilidad de slots libres futuros con filtros opcionales (incluyendo profesional) y paginación.
     */
    public PaginatedResponse<AvailabilitySlotResponse> checkAvailability(
            String especialidadPublicId,
            String profesionalPublicId,
            String modalidad,
            LocalDate fecha,
            int page,
            int size
    ) {
        // Validación de paginación
        if (page < 0) {
            throw new InvalidDataException("El número de página no puede ser menor a 0.");
        }
        if (size < 1 || size > 100) {
            throw new InvalidDataException("El tamaño de página debe estar entre 1 y 100.");
        }

        // Validación de modalidad
        String modalidadFiltro = null;
        if (modalidad != null && !modalidad.isBlank()) {
            modalidadFiltro = modalidad.trim().toUpperCase(Locale.ROOT);
            if (!ALLOWED_MODALITIES.contains(modalidadFiltro)) {
                throw new InvalidDataException(
                        "Modalidad inválida: '" + modalidad + "'. Los valores permitidos son: PRESENCIAL, TELEMEDICINA."
                );
            }
        }

        String espFiltro = (especialidadPublicId != null && !especialidadPublicId.isBlank())
                ? especialidadPublicId.trim()
                : null;
        String profFiltro = (profesionalPublicId != null && !profesionalPublicId.isBlank())
                ? profesionalPublicId.trim()
                : null;
        // Hospital único: no hay filtro de sede.
        String sedeFiltro = null;

        Instant now = clock.instant();
        Instant fechaDesde;
        Instant fechaHasta;

        if (fecha != null) {
            ZonedDateTime startOfDay = fecha.atStartOfDay(BOGOTA_ZONE);
            ZonedDateTime endOfDay = fecha.atTime(23, 59, 59, 999_999_999).atZone(BOGOTA_ZONE);
            Instant startInstant = startOfDay.toInstant();
            Instant endInstant = endOfDay.toInstant();

            // Si todo el día ya pasó, retornar lista vacía sin consultar slots pasados
            if (!endInstant.isAfter(now)) {
                return PaginatedResponse.of(List.of(), page, size, 0L);
            }

            // Acotar rango al día solicitado y asegurar que sea estrictamente futuro respecto a now
            fechaDesde = startInstant.isAfter(now) ? startInstant : now;
            fechaHasta = endInstant;
        } else {
            // Sin fecha específica: buscar desde el instante actual hacia el futuro sin límite superior
            fechaDesde = now;
            fechaHasta = null;
        }

        List<AvailabilitySlotResponse> items;
        int total;

        if (profFiltro != null) {
            items = slotRepository.findAvailable(
                    espFiltro,
                    profFiltro,
                    sedeFiltro,
                    modalidadFiltro,
                    fechaDesde,
                    fechaHasta,
                    page,
                    size
            );
            total = slotRepository.countAvailable(
                    espFiltro,
                    profFiltro,
                    sedeFiltro,
                    modalidadFiltro,
                    fechaDesde,
                    fechaHasta
            );
        } else {
            items = slotRepository.findAvailable(
                    espFiltro,
                    sedeFiltro,
                    modalidadFiltro,
                    fechaDesde,
                    fechaHasta,
                    page,
                    size
            );
            total = slotRepository.countAvailable(
                    espFiltro,
                    sedeFiltro,
                    modalidadFiltro,
                    fechaDesde,
                    fechaHasta
            );
        }

        return PaginatedResponse.of(items, page, size, (long) total);
    }
}
