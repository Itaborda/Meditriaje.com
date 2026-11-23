package com.meditriaje.controller;

import com.meditriaje.dto.hospital.*;
import com.meditriaje.service.HospitalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * Endpoints REST para Gestión Hospitalaria, Camas, Quirófanos y Egresos (Fase H, ADR-024).
 */
@RestController
@RequestMapping("/api/v1/hospital")
public class HospitalController {

    private final HospitalService hospitalService;

    public HospitalController(HospitalService hospitalService) {
        this.hospitalService = Objects.requireNonNull(hospitalService, "hospitalService no puede ser nulo");
    }

    private String extractClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        return (xf != null && !xf.isBlank()) ? xf.split(",")[0].trim() : request.getRemoteAddr();
    }

    /**
     * H01/H02: Asignación de cama a un episodio hospitalario.
     */
    @PostMapping("/episodes/{episodeId}/beds/assign")
    @PreAuthorize("hasAnyRole('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<BedDetailResponse> assignBed(
            @PathVariable("episodeId") String episodeId,
            @Valid @RequestBody AssignBedRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth.getName();
        BedDetailResponse resp = hospitalService.assignBed(episodeId, request, usuarioPubId, extractClientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    /**
     * H03: Traslado intrahospitalario longitudinal del paciente.
     */
    @PostMapping("/episodes/{episodeId}/beds/transfer")
    @PreAuthorize("hasAnyRole('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<MovementResponse> transferPatient(
            @PathVariable("episodeId") String episodeId,
            @Valid @RequestBody TransferPatientRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth.getName();
        MovementResponse resp = hospitalService.transferPatient(episodeId, request, usuarioPubId, extractClientIp(httpRequest));
        return ResponseEntity.ok(resp);
    }

    /**
     * H01: Actualización de estado de cama (ej. LIMPIEZA -> DISPONIBLE).
     */
    @PatchMapping("/beds/{camaId}/status")
    @PreAuthorize("hasAnyRole('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL', 'ROLE_ADMINISTRADOR')")
    public ResponseEntity<Void> changeBedStatus(
            @PathVariable("camaId") String bedId,
            @RequestParam("nuevoEstado") String newStatus,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth.getName();
        hospitalService.changeBedStatus(bedId, newStatus, usuarioPubId, extractClientIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    /**
     * H04: Registro de procedimiento quirúrgico o intervención en quirófano.
     */
    @PostMapping("/episodes/{episodeId}/procedures")
    @PreAuthorize("hasRole('ROLE_PROFESIONAL')")
    public ResponseEntity<ProcedureResponse> registerProcedure(
            @PathVariable("episodeId") String episodeId,
            @Valid @RequestBody RegisterProcedureRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth.getName();
        ProcedureResponse resp = hospitalService.registerProcedure(episodeId, request, usuarioPubId, extractClientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    /**
     * H04: Transición de estado en procedimiento quirúrgico/recuperación.
     */
    @PatchMapping("/procedures/{procedimientoId}/status")
    @PreAuthorize("hasRole('ROLE_PROFESIONAL')")
    public ResponseEntity<Void> updateProcedureStatus(
            @PathVariable("procedimientoId") String procedureId,
            @Valid @RequestBody UpdateProcedureStatusRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth.getName();
        hospitalService.updateProcedureStatus(procedureId, request, usuarioPubId, extractClientIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    /**
     * H05: Egreso hospitalario definitivo y liberación de cama.
     */
    @PostMapping("/episodes/{episodeId}/discharge")
    @PreAuthorize("hasRole('ROLE_PROFESIONAL')")
    public ResponseEntity<Void> registerHospitalDischarge(
            @PathVariable("episodeId") String episodeId,
            @Valid @RequestBody RegisterDischargeRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth.getName();
        hospitalService.registerHospitalDischarge(episodeId, request, usuarioPubId, auth.getAuthorities(), extractClientIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    /**
     * H06: Centro de Control Hospitalario: Censo de camas e indicadores de ocupación en tiempo real.
     */
    @GetMapping("/census")
    @PreAuthorize("hasAnyRole('ROLE_ADMINISTRADOR', 'ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<BedCensusResponse> getBedCensus() {
        BedCensusResponse resp = hospitalService.getBedCensus();
        return ResponseEntity.ok(resp);
    }

    /**
     * H01: Listado de camas con estado y capacidad. Para ROLE_ADMINISTRADOR anonimiza datos de paciente (S-02).
     */
    @GetMapping("/beds")
    @PreAuthorize("hasAnyRole('ROLE_ADMINISTRADOR', 'ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<List<BedDetailResponse>> listBeds(
            Authentication authentication
    ) {
        boolean esSoloAdmin = authentication != null
                && authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMINISTRADOR".equals(a.getAuthority()))
                && authentication.getAuthorities().stream().noneMatch(a -> "ROLE_PROFESIONAL".equals(a.getAuthority()) || "ROLE_ENFERMERIA".equals(a.getAuthority()));
        List<BedDetailResponse> resp = hospitalService.listBedDetails(esSoloAdmin);
        return ResponseEntity.ok(resp);
    }

    /**
     * H03: Historial longitudinal de movimientos y traslados de un episodio.
     */
    @GetMapping("/episodes/{episodeId}/movements")
    @PreAuthorize("hasAnyRole('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<List<MovementResponse>> listMovements(@PathVariable("episodeId") String episodeId) {
        List<MovementResponse> resp = hospitalService.listEpisodeMovements(episodeId);
        return ResponseEntity.ok(resp);
    }

    /**
     * H04: Historial de procedimientos de un episodio.
     */
    @GetMapping("/episodes/{episodeId}/procedures")
    @PreAuthorize("hasAnyRole('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<List<ProcedureResponse>> listProcedures(@PathVariable("episodeId") String episodeId) {
        List<ProcedureResponse> resp = hospitalService.listEpisodeProcedures(episodeId);
        return ResponseEntity.ok(resp);
    }

    /**
     * H01: Lista las áreas hospitalarias con sus habitaciones y salas.
     */
    @GetMapping("/areas")
    @PreAuthorize("hasAnyRole('ROLE_ADMINISTRADOR', 'ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<List<HospitalAreaResponse>> listAreas() {
        return ResponseEntity.ok(hospitalService.listAreasWithRooms());
    }

    /**
     * H01: Crea una nueva área o pabellón hospitalario (solo Administrador).
     */
    @PostMapping("/areas")
    @PreAuthorize("hasRole('ROLE_ADMINISTRADOR')")
    public ResponseEntity<HospitalAreaResponse> createArea(
            @Valid @RequestBody CreateHospitalAreaRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        HospitalAreaResponse resp = hospitalService.createArea(request, auth.getName(), extractClientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    /**
     * H01: Crea una habitación o sala dentro de un área hospitalaria (solo Administrador).
     */
    @PostMapping("/areas/{areaPublicId}/rooms")
    @PreAuthorize("hasRole('ROLE_ADMINISTRADOR')")
    public ResponseEntity<HospitalRoomResponse> createRoom(
            @PathVariable("areaPublicId") String areaPublicId,
            @Valid @RequestBody CreateHospitalRoomRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        HospitalRoomResponse resp = hospitalService.createRoom(
                areaPublicId, request, auth.getName(), extractClientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    /**
     * H01: Crea una o varias camas disponibles dentro de una habitación o sala (solo Administrador).
     */
    @PostMapping("/rooms/{roomPublicId}/beds")
    @PreAuthorize("hasRole('ROLE_ADMINISTRADOR')")
    public ResponseEntity<List<BedDetailResponse>> createBeds(
            @PathVariable("roomPublicId") String roomPublicId,
            @Valid @RequestBody CreateHospitalBedRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        List<BedDetailResponse> resp = hospitalService.createBeds(
                roomPublicId, request, auth.getName(), extractClientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }
}
