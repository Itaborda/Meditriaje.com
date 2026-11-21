package com.meditriaje.controller;

import com.meditriaje.dto.availability.AvailabilitySlotResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.service.AvailabilityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Controlador para la consulta de disponibilidad de citas (HU-03, ADR-002, ADR-003, ADR-005, ADR-006).
 * Permite a cualquier usuario autenticado (paciente, profesional o administrador) consultar slots disponibles.
 */
@RestController
@RequestMapping("/api/v1/availability")
@PreAuthorize("isAuthenticated()")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = Objects.requireNonNull(availabilityService, "AvailabilityService no puede ser nulo");
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<AvailabilitySlotResponse>> checkAvailability(
            @RequestParam(value = "especialidad", required = false) String specialty,
            @RequestParam(value = "especialidadPublicId", required = false) String specialtyPublicId,
            @RequestParam(value = "profesional", required = false) String profesional,
            @RequestParam(value = "profesionalPublicId", required = false) String professionalPublicId,
            @RequestParam(value = "fecha", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "modalidad", required = false) String modality,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        String espFiltro = (specialtyPublicId != null && !specialtyPublicId.isBlank())
                ? specialtyPublicId
                : specialty;

        String profFiltro = (professionalPublicId != null && !professionalPublicId.isBlank())
                ? professionalPublicId
                : profesional;

        PaginatedResponse<AvailabilitySlotResponse> response;
        if (profFiltro != null && !profFiltro.isBlank()) {
            response = availabilityService.checkAvailability(
                    espFiltro,
                    profFiltro,
                    modality,
                    date,
                    page,
                    size
            );
        } else {
            response = availabilityService.checkAvailability(
                    espFiltro,
                    modality,
                    date,
                    page,
                    size
            );
        }

        return ResponseEntity.ok(response);
    }
}
