package com.meditriaje.service;

import com.meditriaje.dto.affiliation.*;
import com.meditriaje.exception.OperationConflictException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.*;
import com.meditriaje.repository.*;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Servicio de Afiliaciones EPS, Importación Masiva Segura y Citas Avanzadas (Fases A y C, ADR-025, ADR-026).
 */
@Service
public class AffiliationService {

    private final EpsEntityRepository epsRepository;
    private final PatientAffiliationRepository affiliationRepository;
    private final EpsImportBatchRepository batchRepository;
    private final MedicalAbsenceRepository absenceRepository;
    private final LegalRepresentationRepository representationRepository;
    private final PatientRepository patientRepository;
    private final ProfessionalRepository professionalRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final Clock clock;

    private static final Set<String> VALID_DOCUMENT_TYPES = Set.of("CC", "TI", "RC", "CE", "PA", "PE", "PPT");
    private static final Set<String> VALID_REGIMES = Set.of("CONTRIBUTIVO", "SUBSIDIADO", "ESPECIAL", "NO_ASEGURADO");
    private static final int MAX_BATCH_ROWS = 5000;

    public AffiliationService(
            EpsEntityRepository epsRepository,
            PatientAffiliationRepository affiliationRepository,
            EpsImportBatchRepository batchRepository,
            MedicalAbsenceRepository absenceRepository,
            LegalRepresentationRepository representationRepository,
            PatientRepository patientRepository,
            ProfessionalRepository professionalRepository,
            UserRepository userRepository,
            AuditService auditService,
            Clock clock
    ) {
        this.epsRepository = Objects.requireNonNull(epsRepository);
        this.affiliationRepository = Objects.requireNonNull(affiliationRepository);
        this.batchRepository = Objects.requireNonNull(batchRepository);
        this.absenceRepository = Objects.requireNonNull(absenceRepository);
        this.representationRepository = Objects.requireNonNull(representationRepository);
        this.patientRepository = Objects.requireNonNull(patientRepository);
        this.professionalRepository = Objects.requireNonNull(professionalRepository);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.auditService = Objects.requireNonNull(auditService);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Procesa y valida un archivo Excel XLSX en staging (A02, A03).
     * Aplica protección contra Zip Bomb, sanitización de fórmulas y aislamiento transaccional.
     */
    @Transactional
    public BatchPreviewResponse previewExcelImport(
            byte[] archivoBytes,
            String nombreArchivo,
            String epsPublicId,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(archivoBytes, "archivoBytes no puede ser nulo");
        if (archivoBytes.length == 0) {
            throw new OperationConflictException("El archivo suministrado está vacío.");
        }
        if (archivoBytes.length > 10 * 1024 * 1024) {
            throw new OperationConflictException("El archivo supera el tamaño máximo permitido de 10 MB.");
        }

        EpsEntity eps = epsRepository.findByPublicId(epsPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("EPS no encontrada: " + epsPublicId));

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        String hashSha256 = computeSha256(archivoBytes);

        // Mitigación Zip Bomb (CVE-2014-3529 / POI threshold)
        ZipSecureFile.setMinInflateRatio(0.01);

        List<EpsImportDetail> filasStaging = new ArrayList<>();
        int totalFilas = 0;
        int filasValidas = 0;
        int filasFallidas = 0;

        try (InputStream is = new ByteArrayInputStream(archivoBytes);
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new OperationConflictException("El archivo Excel no contiene hojas de cálculo legibles.");
            }

            int lastRow = sheet.getLastRowNum();
            if (lastRow > MAX_BATCH_ROWS) {
                throw new OperationConflictException("El archivo supera el límite de " + MAX_BATCH_ROWS + " filas por lote.");
            }

            // Fila 0 es encabezado (TIPO_DOCUMENTO, NUMERO_DOCUMENTO, NOMBRES, APELLIDOS, REGIMEN, TIPO_AFILIADO)
            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isEmptyCell(row.getCell(0))) {
                    continue;
                }

                totalFilas++;
                String tipoDoc = sanitizeCellValue(getCellValue(row.getCell(0)));
                String numDoc = sanitizeCellValue(getCellValue(row.getCell(1)));
                String nombres = sanitizeCellValue(getCellValue(row.getCell(2)));
                String apellidos = sanitizeCellValue(getCellValue(row.getCell(3)));
                String regimen = sanitizeCellValue(getCellValue(row.getCell(4)));
                String tipoAfiliado = sanitizeCellValue(getCellValue(row.getCell(5)));

                String errorMotivo = validateRow(tipoDoc, numDoc, regimen);
                boolean esValida = errorMotivo == null;

                if (esValida) {
                    filasValidas++;
                } else {
                    filasFallidas++;
                }

                filasStaging.add(new EpsImportDetail(
                        null,
                        null,
                        r + 1,
                        tipoDoc,
                        numDoc,
                        nombres,
                        apellidos,
                        regimen != null ? regimen.toUpperCase() : eps.defaultRegime(),
                        tipoAfiliado != null ? tipoAfiliado.toUpperCase() : "COTIZANTE",
                        esValida ? "VALIDO" : "ERROR",
                        errorMotivo
                ));
            }

        } catch (Exception e) {
            if (e instanceof OperationConflictException) throw (OperationConflictException) e;
            throw new OperationConflictException("Error al procesar el archivo Excel: " + e.getMessage());
        }

        if (totalFilas == 0) {
            throw new OperationConflictException("El archivo Excel no contiene filas de datos para procesar.");
        }

        String lotePubId = UUID.randomUUID().toString();
        Instant ahora = Instant.now(clock);
        EpsImportBatch lote = new EpsImportBatch(
                null,
                lotePubId,
                nombreArchivo != null ? nombreArchivo : "afiliados.xlsx",
                hashSha256,
                eps.id(),
                totalFilas,
                filasValidas,
                filasFallidas,
                "PREVIEW",
                usuario.id(),
                ahora,
                null
        );

        EpsImportBatch guardado = batchRepository.saveBatch(lote);
        batchRepository.saveBatchDetails(guardado.id(), filasStaging);

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.IMPORTACION_EPS_PREVIEW,
                "LOTE_IMPORTACION_EPS",
                lotePubId,
                AuditResult.EXITO,
                ipOrigen
        ));

        List<EpsImportDetail> muestra = batchRepository.listDetails(guardado.id(), 20);
        List<BatchPreviewResponse.RowPreviewItem> itemsMuestra = muestra.stream().map(d -> new BatchPreviewResponse.RowPreviewItem(
                d.rowNumber(),
                d.documentType(),
                d.documentNumber(),
                d.firstNames(),
                d.lastNames(),
                d.regime(),
                d.affiliateType(),
                d.rowStatus(),
                d.errorReason()
        )).toList();

        return new BatchPreviewResponse(
                lotePubId,
                guardado.fileName(),
                eps.minSaludCode(),
                eps.name(),
                totalFilas,
                filasValidas,
                filasFallidas,
                "PREVIEW",
                itemsMuestra
        );
    }

    /**
     * Aplica el commit definitivo de un lote en staging a la base de datos (A03, A04).
     */
    @Transactional
    public void commitBatch(
            String lotePublicId,
            CommitBatchRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        EpsImportBatch lote = batchRepository.findByPublicId(lotePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote no encontrado: " + lotePublicId));

        if (!"PREVIEW".equals(lote.status())) {
            throw new OperationConflictException("El lote ya fue procesado o no se encuentra en estado PREVIEW (Estado: " + lote.status() + ").");
        }

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        String modo = request != null && request.commitMode() != null ? request.commitMode() : "VALID_ROWS";
        if ("ATOMIC_ALL".equals(modo) && lote.failedRows() > 0) {
            throw new OperationConflictException("El lote contiene " + lote.failedRows() + " filas con error y el modo seleccionado es ATOMIC_ALL.");
        }

        List<EpsImportDetail> filasValidas = batchRepository.listValidRows(lote.id());
        Instant ahora = Instant.now(clock);

        for (EpsImportDetail fila : filasValidas) {
            // Verificar si el paciente ya tiene registro civil en PACIENTE
            Optional<Patient> pacienteOpt = patientRepository.findByDocument(fila.documentType(), fila.documentNumber());
            Long pacienteId = pacienteOpt.map(Patient::id).orElse(null);

            Optional<PatientAffiliation> previa = affiliationRepository.findByDocument(fila.documentType(), fila.documentNumber());
            if (previa.isPresent()) {
                // Actualizar cobertura EPS existente
                affiliationRepository.updateAffiliation(
                        previa.get().id(),
                        lote.epsId(),
                        fila.regime(),
                        fila.affiliateType(),
                        "ACTIVO",
                        ahora
                );
            } else {
                // Insertar nueva afiliación
                PatientAffiliation nueva = new PatientAffiliation(
                        null,
                        UUID.randomUUID().toString(),
                        pacienteId,
                        fila.documentType(),
                        fila.documentNumber(),
                        lote.epsId(),
                        fila.regime(),
                        fila.affiliateType(),
                        "ACTIVO",
                        LocalDate.now(clock),
                        "CARGA_MASIVA",
                        ahora,
                        ahora,
                        ahora
                );
                affiliationRepository.save(nueva);
            }
        }

        batchRepository.changeBatchStatus(lote.id(), "PROCESADO", ahora);

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.IMPORTACION_EPS_COMMIT,
                "LOTE_IMPORTACION_EPS",
                lote.publicId(),
                AuditResult.EXITO,
                ipOrigen
        ));
    }

    /**
     * Consulta el estado de afiliación y aseguramiento EPS de un paciente (A05).
     * Nunca bloquea la atención médica de urgencias si el paciente no tiene EPS activa.
     */
    public AffiliationResponse getAffiliation(
            String tipoDocumento,
            String numeroDocumento,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado."));

        Optional<AffiliationResponse> detalle = affiliationRepository.findDetailByDocument(tipoDocumento, numeroDocumento);

        // S-04: Registrar identificador seudónimo en auditoría para no exponer documento de identidad en bitácora
        String idRecursoAuditoria = detalle.map(AffiliationResponse::publicId).orElse("SIN_REGISTRO_PREVIO");

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.AFILIACION_CONSULTADA,
                "AFILIACION_PACIENTE",
                idRecursoAuditoria,
                AuditResult.EXITO,
                ipOrigen
        ));

        return detalle.orElse(new AffiliationResponse(
                null,
                null,
                tipoDocumento,
                numeroDocumento,
                null,
                "NO_EPS",
                "PARTICULAR / NO ASEGURADO",
                "NO_ASEGURADO",
                "NO_AFILIADO",
                "NO_AFILIADO",
                null,
                "CONSULTA_DIRECTA",
                Instant.now(clock)
        ));
    }

    /**
     * Registra una ausencia médica o bloqueo de agenda (C02).
     */
    @Transactional
    public AbsenceResponse registerMedicalAbsence(
            RegisterAbsenceRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        if (request.endDate().isBefore(request.startDate())) {
            throw new OperationConflictException("fechaFin no puede ser anterior a fechaInicio.");
        }

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        Professional prof = professionalRepository.findByPublicId(request.professionalPublicId())
                .orElseThrow(() -> new ResourceNotFoundException("Profesional no encontrado: " + request.professionalPublicId()));

        if (absenceRepository.existsAbsenceOverlap(prof.id(), request.startDate(), request.endDate())) {
            throw new OperationConflictException("Ya existe una ausencia médica activa registrada en ese rango horario.");
        }

        Instant ahora = Instant.now(clock);
        String ausenciaPubId = UUID.randomUUID().toString();
        MedicalAbsence ausencia = new MedicalAbsence(
                null,
                ausenciaPubId,
                prof.id(),
                request.startDate(),
                request.endDate(),
                request.reason(),
                "ACTIVA",
                usuario.id(),
                ahora
        );
        absenceRepository.save(ausencia);

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.AUSENCIA_MEDICA_REGISTRADA,
                "AUSENCIA_MEDICA",
                ausenciaPubId,
                AuditResult.EXITO,
                ipOrigen
        ));

        return new AbsenceResponse(
                ausenciaPubId,
                prof.publicId(),
                prof.firstNames() + " " + prof.lastNames(),
                request.startDate(),
                request.endDate(),
                request.reason(),
                "ACTIVA"
        );
    }

    /**
     * Registra el vínculo de representación legal para menores de edad (C03).
     */
    @Transactional
    public LegalRepresentationResponse registerLegalRepresentation(
            LegalRepresentationRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        Patient menor = patientRepository.findByPublicId(request.minorPublicId())
                .orElseThrow(() -> new ResourceNotFoundException("Menor de edad no encontrado: " + request.minorPublicId()));

        Patient rep = patientRepository.findByPublicId(request.representativePublicId())
                .orElseThrow(() -> new ResourceNotFoundException("Representante legal no encontrado: " + request.representativePublicId()));

        if (menor.id().equals(rep.id())) {
            throw new OperationConflictException("El menor y el representante legal no pueden ser el mismo paciente.");
        }

        Instant ahora = Instant.now(clock);
        String repPubId = UUID.randomUUID().toString();
        LegalRepresentation rl = new LegalRepresentation(
                null,
                repPubId,
                menor.id(),
                rep.id(),
                request.relationship(),
                request.supportingDocument(),
                true,
                ahora
        );
        representationRepository.save(rl);

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.REPRESENTACION_LEGAL_REGISTRADA,
                "REPRESENTACION_LEGAL",
                repPubId,
                AuditResult.EXITO,
                ipOrigen
        ));

        return new LegalRepresentationResponse(
                repPubId,
                menor.publicId(),
                menor.firstNames() + " " + menor.lastNames(),
                menor.documentNumber(),
                rep.publicId(),
                rep.firstNames() + " " + rep.lastNames(),
                rep.documentNumber(),
                request.relationship(),
                true
        );
    }

    public List<EpsEntity> listActiveEps() {
        return epsRepository.listActive();
    }

    public List<AbsenceResponse> listProfessionalAbsences(String profesionalPublicId) {
        Professional prof = professionalRepository.findByPublicId(profesionalPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Profesional no encontrado: " + profesionalPublicId));
        return absenceRepository.listByProfessionalId(prof.id());
    }

    public List<LegalRepresentationResponse> listRepresentationsByGuardian(String tutorPublicId) {
        Patient tutor = patientRepository.findByPublicId(tutorPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Tutor no encontrado: " + tutorPublicId));
        return representationRepository.listByRepresentativeId(tutor.id());
    }

    // --- Métodos de Utilidad y Seguridad ---

    private String validateRow(String tipoDoc, String numDoc, String regimen) {
        if (tipoDoc == null || !VALID_DOCUMENT_TYPES.contains(tipoDoc.toUpperCase())) {
            return "Tipo de documento no válido. Esperado: CC, TI, RC, CE, PA, PPT.";
        }
        if (numDoc == null || numDoc.isBlank() || numDoc.length() < 3 || numDoc.length() > 20) {
            return "Número de documento inválido o de longitud incorrecta.";
        }
        if (regimen != null && !VALID_REGIMES.contains(regimen.toUpperCase())) {
            return "Régimen no válido. Esperado: CONTRIBUTIVO, SUBSIDIADO, ESPECIAL.";
        }
        return null;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private boolean isEmptyCell(Cell cell) {
        String val = getCellValue(cell);
        return val == null || val.trim().isEmpty();
    }

    /**
     * Sanitiza el valor de celda para neutralizar inyecciones de fórmulas (D1, SEC-001).
     */
    private String sanitizeCellValue(String val) {
        if (val == null) return null;
        String s = val.trim();
        if (s.startsWith("=") || s.startsWith("+") || s.startsWith("-") || s.startsWith("@")) {
            return "'" + s;
        }
        return s;
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    /**
     * Genera la plantilla Excel oficial (.xlsx) para importación de afiliados EPS.
     * Diseñada para Valledupar, Cesar con encabezados estandarizados, estilos y hoja de instrucciones.
     */
    public byte[] generateExcelTemplate() {
        try (Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 11);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);

            // Hoja 1: Afiliados EPS
            Sheet sheetAfiliados = workbook.createSheet("AFILIADOS_EPS");
            sheetAfiliados.setDisplayGridlines(true);

            String[] columnas = {
                    "TIPO_DOCUMENTO",
                    "NUMERO_DOCUMENTO",
                    "NOMBRES",
                    "APELLIDOS",
                    "REGIMEN",
                    "TIPO_AFILIADO"
            };

            Row headerRow = sheetAfiliados.createRow(0);
            headerRow.setHeightInPoints(26);
            for (int i = 0; i < columnas.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columnas[i]);
                cell.setCellStyle(headerStyle);
            }

            // Ejemplos realistas para Colombia / Cesar
            String[][] ejemplos = {
                    {"CC", "1065123456", "Carlos Andrés", "Gómez Quintero", "CONTRIBUTIVO", "COTIZANTE"},
                    {"TI", "1098765432", "Valentina", "Dangond Baute", "CONTRIBUTIVO", "BENEFICIARIO"},
                    {"CC", "1065987654", "Andrés Felipe", "Cuello Oñate", "SUBSIDIADO", "CABEZA_FAMILIA"},
                    {"RC", "1024567890", "Sofía", "Martínez Mejía", "SUBSIDIADO", "BENEFICIARIO"},
                    {"CE", "456789012", "María Elena", "Mendoza Gutiérrez", "CONTRIBUTIVO", "COTIZANTE"}
            };

            for (int r = 0; r < ejemplos.length; r++) {
                Row row = sheetAfiliados.createRow(r + 1);
                row.setHeightInPoints(20);
                for (int c = 0; c < ejemplos[r].length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(ejemplos[r][c]);
                    cell.setCellStyle(dataStyle);
                }
            }

            for (int i = 0; i < columnas.length; i++) {
                sheetAfiliados.autoSizeColumn(i);
                sheetAfiliados.setColumnWidth(i, Math.max(sheetAfiliados.getColumnWidth(i) + 1200, 4200));
            }

            // Hoja 2: Instrucciones y Catálogos
            Sheet sheetInstrucciones = workbook.createSheet("INSTRUCCIONES");
            sheetInstrucciones.setDisplayGridlines(true);

            Row r0 = sheetInstrucciones.createRow(0);
            Cell c0 = r0.createCell(0);
            c0.setCellValue("GUÍA OFICIAL DE DILIGENCIAMIENTO — IMPORTACIÓN DE AFILIADOS MEDITRIAJE");
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 13);
            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);
            c0.setCellStyle(titleStyle);

            String[][] reglas = {
                    {"Columna", "Obligatorio", "Valores Permitidos / Formato", "Descripción y Reglas de Negocio"},
                    {"TIPO_DOCUMENTO", "SÍ", "CC, TI, RC, CE, PA, PE, PPT", "Tipo de documento de identidad oficial en Colombia (MinSalud)."},
                    {"NUMERO_DOCUMENTO", "SÍ", "Numérico o alfanumérico limpio", "Sin puntos, espacios ni guiones."},
                    {"NOMBRES", "SÍ", "Texto (hasta 60 caracteres)", "Nombres del usuario tal como figura en su documento."},
                    {"APELLIDOS", "SÍ", "Texto (hasta 60 caracteres)", "Apellidos del usuario."},
                    {"REGIMEN", "SÍ", "CONTRIBUTIVO, SUBSIDIADO, ESPECIAL, NO_ASEGURADO", "Régimen de aseguramiento de salud SGSSS."},
                    {"TIPO_AFILIADO", "SÍ", "COTIZANTE, BENEFICIARIO, CABEZA_FAMILIA, ADICIONAL", "Condición del usuario ante la EPS."}
            };

            for (int i = 0; i < reglas.length; i++) {
                Row row = sheetInstrucciones.createRow(i + 2);
                row.setHeightInPoints(22);
                for (int j = 0; j < reglas[i].length; j++) {
                    Cell cell = row.createCell(j);
                    cell.setCellValue(reglas[i][j]);
                    if (i == 0) {
                        cell.setCellStyle(headerStyle);
                    } else {
                        cell.setCellStyle(dataStyle);
                    }
                }
            }

            for (int j = 0; j < 4; j++) {
                sheetInstrucciones.autoSizeColumn(j);
                sheetInstrucciones.setColumnWidth(j, sheetInstrucciones.getColumnWidth(j) + 1500);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new OperationConflictException("Error al generar la plantilla Excel de afiliados: " + e.getMessage());
        }
    }
}
