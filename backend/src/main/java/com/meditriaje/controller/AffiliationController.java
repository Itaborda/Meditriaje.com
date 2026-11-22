package com.meditriaje.controller;

import com.meditriaje.dto.affiliation.*;
import com.meditriaje.model.EpsEntity;
import com.meditriaje.service.AffiliationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Endpoints REST para Gestión de Afiliaciones EPS, Importación Segura y Citas Avanzadas (Fases A y C, ADR-025, ADR-026).
 */
@RestController
@RequestMapping("/api/v1/affiliations")
public class AffiliationController {

    private final AffiliationService affiliationService;

    public AffiliationController(AffiliationService affiliationService) {
        this.affiliationService = Objects.requireNonNull(affiliationService, "affiliationService no puede ser nulo");
    }

    private String extractClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        return (xf != null && !xf.isBlank()) ? xf.split(",")[0].trim() : request.getRemoteAddr();
    }

    /**
     * Lista las entidades promotoras de salud (EPS) activas en el sistema.
     */
    @GetMapping("/eps")
    @PreAuthorize("hasAnyRole('ROLE_ADMINISTRADOR', 'ROLE_ENFERMERIA', 'ROLE_PROFESIONAL', 'ROLE_PACIENTE')")
    public ResponseEntity<List<EpsEntity>> listEps() {
        return ResponseEntity.ok(affiliationService.listActiveEps());
    }

    /**
     * Descarga la plantilla oficial en formato Excel (.xlsx) para importación masiva de afiliados.
     */
    @GetMapping(value = "/template", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] archivo = affiliationService.generateExcelTemplate();
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plantilla_afiliados_eps.xlsx\"")
                .body(archivo);
    }

    /**
     * A02: Carga un archivo Excel (XLSX) con protección Zip Bomb y sanitización en staging preview.
     */
    @PostMapping(value = "/upload-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ROLE_ADMINISTRADOR')")
    public ResponseEntity<BatchPreviewResponse> uploadPreview(
            @RequestParam("file") MultipartFile file,
            @RequestParam("epsPublicId") String epsPublicId,
            Authentication auth,
            HttpServletRequest httpRequest
    ) throws IOException {
        String usuarioPubId = auth.getName();
        byte[] bytes = file.getBytes();
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "importacion.xlsx";

        BatchPreviewResponse response = affiliationService.previewExcelImport(
                bytes,
                originalFilename,
                epsPublicId,
                usuarioPubId,
                extractClientIp(httpRequest)
        );
        return ResponseEntity.ok(response);
    }

    /**
     * A03/A04: Confirma y aplica el commit atómico o por filas válidas de un lote en staging.
     */
    @PostMapping("/batches/{lotePublicId}/commit")
    @PreAuthorize("hasRole('ROLE_ADMINISTRADOR')")
    public ResponseEntity<Void> confirmBatch(
            @PathVariable("lotePublicId") String batchPublicId,
            @Valid @RequestBody(required = false) CommitBatchRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth.getName();
        affiliationService.commitBatch(batchPublicId, request, usuarioPubId, extractClientIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    /**
     * A05: Consulta el estado de afiliación de un paciente por tipo y número de documento (no bloqueante en urgencias).
     */
    @GetMapping("/patients/{tipoDocumento}/{numeroDocumento}")
    @PreAuthorize("hasAnyRole('ROLE_ADMINISTRADOR', 'ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<AffiliationResponse> getAffiliation(
            @PathVariable("tipoDocumento") String documentType,
            @PathVariable("numeroDocumento") String documentNumber,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth.getName();
        AffiliationResponse response = affiliationService.getAffiliation(
                documentType,
                documentNumber,
                usuarioPubId,
                extractClientIp(httpRequest)
        );
        return ResponseEntity.ok(response);
    }

    /**
     * C02: Registra una ausencia médica o bloqueo temporal de agenda.
     */
    @PostMapping("/absences")
    @PreAuthorize("hasAnyRole('ROLE_ADMINISTRADOR', 'ROLE_PROFESIONAL')")
    public ResponseEntity<AbsenceResponse> registerAbsence(
            @Valid @RequestBody RegisterAbsenceRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth.getName();
        AbsenceResponse response = affiliationService.registerMedicalAbsence(request, usuarioPubId, extractClientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * C02: Lista las ausencias médicas de un profesional.
     */
    @GetMapping("/professionals/{profesionalPublicId}/absences")
    @PreAuthorize("hasAnyRole('ROLE_ADMINISTRADOR', 'ROLE_PROFESIONAL')")
    public ResponseEntity<List<AbsenceResponse>> listAbsences(
            @PathVariable("profesionalPublicId") String professionalPublicId
    ) {
        return ResponseEntity.ok(affiliationService.listProfessionalAbsences(professionalPublicId));
    }

    /**
     * C03: Registra una representación legal para agendamiento de menores de edad.
     */
    @PostMapping("/guardians")
    @PreAuthorize("hasAnyRole('ROLE_ADMINISTRADOR', 'ROLE_PACIENTE')")
    public ResponseEntity<LegalRepresentationResponse> registerRepresentation(
            @Valid @RequestBody LegalRepresentationRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth.getName();
        LegalRepresentationResponse response = affiliationService.registerLegalRepresentation(request, usuarioPubId, extractClientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * C03: Lista los menores a cargo de un tutor o representante.
     */
    @GetMapping("/guardians/{tutorPublicId}/minors")
    @PreAuthorize("hasAnyRole('ROLE_ADMINISTRADOR', 'ROLE_PACIENTE')")
    public ResponseEntity<List<LegalRepresentationResponse>> listDependents(
            @PathVariable("tutorPublicId") String guardianPublicId
    ) {
        return ResponseEntity.ok(affiliationService.listRepresentationsByGuardian(guardianPublicId));
    }
}
