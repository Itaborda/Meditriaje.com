package com.meditriaje.service;

import com.meditriaje.dto.affiliation.*;
import com.meditriaje.exception.OperationConflictException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.*;
import com.meditriaje.repository.*;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AffiliationServiceTest {

    @Mock
    private EpsEntityRepository epsRepository;

    @Mock
    private PatientAffiliationRepository affiliationRepository;

    @Mock
    private EpsImportBatchRepository batchRepository;

    @Mock
    private MedicalAbsenceRepository absenceRepository;

    @Mock
    private LegalRepresentationRepository representationRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private ProfessionalRepository professionalRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    private Clock clock;
    private AffiliationService service;

    private final Instant now = Instant.parse("2026-03-30T10:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(now, ZoneOffset.UTC);
        service = new AffiliationService(
                epsRepository,
                affiliationRepository,
                batchRepository,
                absenceRepository,
                representationRepository,
                patientRepository,
                professionalRepository,
                userRepository,
                auditService,
                clock
        );
    }

    private byte[] crearExcelPrueba() throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Afiliados");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("TIPO_DOCUMENTO");
            header.createCell(1).setCellValue("NUMERO_DOCUMENTO");
            header.createCell(2).setCellValue("NOMBRES");
            header.createCell(3).setCellValue("APELLIDOS");
            header.createCell(4).setCellValue("REGIMEN");
            header.createCell(5).setCellValue("TIPO_AFILIADO");

            // Fila 1: Válida con fórmula maliciosa en Nombres
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("CC");
            row1.createCell(1).setCellValue("1065123456");
            row1.createCell(2).setCellValue("=CMD|' /C calc'!A0");
            row1.createCell(3).setCellValue("Perez");
            row1.createCell(4).setCellValue("CONTRIBUTIVO");
            row1.createCell(5).setCellValue("COTIZANTE");

            // Fila 2: Inválida (Tipo doc inexistente)
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("PASAPORTE_INVALIDO");
            row2.createCell(1).setCellValue("999");
            row2.createCell(2).setCellValue("Carlos");
            row2.createCell(3).setCellValue("Gomez");
            row2.createCell(4).setCellValue("SUBSIDIADO");
            row2.createCell(5).setCellValue("BENEFICIARIO");

            wb.write(bos);
            return bos.toByteArray();
        }
    }

    @Test
    @DisplayName("A02/A03: procesarArchivoExcelPreview valida filas, sanitiza fórmulas y retorna preview en staging")
    void previewExcelImport_validAndSanitizaExitosamente() throws IOException {
        byte[] excel = crearExcelPrueba();

        EpsEntity eps = new EpsEntity(1L, "eps-uuid", "EPS001", "Nueva EPS", "900123456", "CONTRIBUTIVO", "ACTIVA", now);
        when(epsRepository.findByPublicId("eps-uuid")).thenReturn(Optional.of(eps));

        User usuario = new User(10L, "usr-admin", "admin@hospital.com", "HASH", "ACTIVO", 0, null, now, false);
        when(userRepository.findByPublicId("usr-admin")).thenReturn(Optional.of(usuario));

        EpsImportBatch loteGuardado = new EpsImportBatch(
                100L, "lote-uuid", "test.xlsx", "hash", 1L, 2, 1, 1, "PREVIEW", 10L, now, null
        );
        when(batchRepository.saveBatch(any(EpsImportBatch.class))).thenReturn(loteGuardado);

        EpsImportDetail d1 = new EpsImportDetail(
                1L, 100L, 1, "CC", "1065123456", "'=CMD|' /C calc'!A0", "Perez", "CONTRIBUTIVO", "COTIZANTE", "VALIDA", null
        );
        when(batchRepository.listDetails(100L, 20)).thenReturn(List.of(d1));

        BatchPreviewResponse resp = service.previewExcelImport(
                excel, "test.xlsx", "eps-uuid", "usr-admin", "127.0.0.1"
        );

        assertNotNull(resp);
        assertEquals(2, resp.totalRows());
        assertEquals(1, resp.validRows());
        assertEquals(1, resp.failedRows());
        assertEquals("PREVIEW", resp.status());

        // Verificar que las filas de staging enviadas a guardar incluyen sanitización
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EpsImportDetail>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchRepository).saveBatchDetails(eq(100L), captor.capture());
        List<EpsImportDetail> filasGuardadas = captor.getValue();
        assertEquals(2, filasGuardadas.size());

        EpsImportDetail f1 = filasGuardadas.get(0);
        assertEquals("VALIDO", f1.rowStatus());
        assertTrue(f1.firstNames().startsWith("'="), "La celda con fórmula debe comenzar con apóstrofe");

        EpsImportDetail f2 = filasGuardadas.get(1);
        assertEquals("ERROR", f2.rowStatus());
        assertNotNull(f2.errorReason());
    }

    @Test
    @DisplayName("A04: commitLote en modo ATOMIC_ALL falla si hay filas con error")
    void commitBatch_modeAtomicAll_failsWhenExistsErrors() {
        EpsImportBatch lote = new EpsImportBatch(
                100L, "lote-uuid", "test.xlsx", "hash", 1L, 2, 1, 1, "PREVIEW", 10L, now, null
        );
        when(batchRepository.findByPublicId("lote-uuid")).thenReturn(Optional.of(lote));

        User usuario = new User(10L, "usr-admin", "admin@hospital.com", "HASH", "ACTIVO", 0, null, now, false);
        when(userRepository.findByPublicId("usr-admin")).thenReturn(Optional.of(usuario));

        CommitBatchRequest req = new CommitBatchRequest("ATOMIC_ALL");

        OperationConflictException ex = assertThrows(OperationConflictException.class, () ->
                service.commitBatch("lote-uuid", req, "usr-admin", "127.0.0.1")
        );
        assertTrue(ex.getMessage().contains("filas con error"));
    }

    @Test
    @DisplayName("A04: commitLote en modo VALID_ROWS aplica registros válidos e ignora errores")
    void commitBatch_modeValidRows_appliesExitosamente() {
        EpsImportBatch lote = new EpsImportBatch(
                100L, "lote-uuid", "test.xlsx", "hash", 1L, 2, 1, 1, "PREVIEW", 10L, now, null
        );
        when(batchRepository.findByPublicId("lote-uuid")).thenReturn(Optional.of(lote));

        User usuario = new User(10L, "usr-admin", "admin@hospital.com", "HASH", "ACTIVO", 0, null, now, false);
        when(userRepository.findByPublicId("usr-admin")).thenReturn(Optional.of(usuario));

        EpsImportDetail d1 = new EpsImportDetail(
                1L, 100L, 1, "CC", "1065123456", "Juan", "Perez", "CONTRIBUTIVO", "COTIZANTE", "VALIDA", null
        );
        when(batchRepository.listValidRows(100L)).thenReturn(List.of(d1));
        when(affiliationRepository.findByDocument("CC", "1065123456")).thenReturn(Optional.empty());

        CommitBatchRequest req = new CommitBatchRequest("VALID_ROWS");
        service.commitBatch("lote-uuid", req, "usr-admin", "127.0.0.1");

        verify(affiliationRepository).save(any(PatientAffiliation.class));
        verify(batchRepository).changeBatchStatus(eq(100L), eq("PROCESADO"), any(Instant.class));
        verify(auditService).audit(any(AuditEvent.class));
    }

    @Test
    @DisplayName("A05: consultarAfiliacion no bloquea ni falla si el paciente no tiene EPS activa")
    void getAffiliation_notBloqueanteWhenNotExists() {
        User usuario = new User(20L, "usr-enf", "enf@hospital.com", "HASH", "ACTIVO", 0, null, now, false);
        when(userRepository.findByPublicId("usr-enf")).thenReturn(Optional.of(usuario));
        when(affiliationRepository.findDetailByDocument("CC", "99999999")).thenReturn(Optional.empty());

        AffiliationResponse resp = service.getAffiliation("CC", "99999999", "usr-enf", "127.0.0.1");

        assertNotNull(resp);
        assertEquals("NO_ASEGURADO", resp.regime());
        assertEquals("NO_AFILIADO", resp.status());
        verify(auditService).audit(any(AuditEvent.class));
    }

    @Test
    @DisplayName("C02: registrarAusenciaMedica rechaza si fechaFin es antes de fechaInicio")
    void registerAbsence_datesInconsistent_throwsConflict() {
        RegisterAbsenceRequest req = new RegisterAbsenceRequest(
                "prof-uuid",
                now.plusSeconds(3600),
                now,
                "Vacaciones"
        );

        assertThrows(OperationConflictException.class, () ->
                service.registerMedicalAbsence(req, "usr-admin", "127.0.0.1")
        );
    }

    @Test
    @DisplayName("C02: registrarAusenciaMedica rechaza si existe traslape horario")
    void registerAbsence_overlap_throwsConflict() {
        RegisterAbsenceRequest req = new RegisterAbsenceRequest(
                "prof-uuid",
                now,
                now.plusSeconds(7200),
                "Congreso Médico"
        );

        User usuario = new User(10L, "usr-admin", "admin@hospital.com", "HASH", "ACTIVO", 0, null, now, false);
        when(userRepository.findByPublicId("usr-admin")).thenReturn(Optional.of(usuario));

        Professional prof = new Professional(
                5L, 50L, "prof-uuid", 1L, "CC", "12345", "RM-1", "Carlos", "Mendoza", "300123", now, now
        );
        when(professionalRepository.findByPublicId("prof-uuid")).thenReturn(Optional.of(prof));
        when(absenceRepository.existsAbsenceOverlap(eq(5L), any(Instant.class), any(Instant.class))).thenReturn(true);

        assertThrows(OperationConflictException.class, () ->
                service.registerMedicalAbsence(req, "usr-admin", "127.0.0.1")
        );
    }

    @Test
    @DisplayName("C03: registrarRepresentacionLegal rechaza si el menor y el tutor son la misma persona")
    void registerRepresentation_samePatient_throwsConflict() {
        LegalRepresentationRequest req = new LegalRepresentationRequest(
                "pac-1", "pac-1", "MADRE", "REGISTRO_CIVIL"
        );

        User usuario = new User(10L, "usr-pac", "pac@hospital.com", "HASH", "ACTIVO", 0, null, now, false);
        when(userRepository.findByPublicId("usr-pac")).thenReturn(Optional.of(usuario));

        Patient pac = new Patient(
                1L, 10L, "pac-1", "CC", "1065000", "Ana", "Torres", LocalDate.of(1990, 1, 1), "300", now, now
        );
        when(patientRepository.findByPublicId("pac-1")).thenReturn(Optional.of(pac));

        assertThrows(OperationConflictException.class, () ->
                service.registerLegalRepresentation(req, "usr-pac", "127.0.0.1")
        );
    }

    @Test
    @DisplayName("C03: registrarRepresentacionLegal guarda tutoría exitosamente")
    void registerRepresentation_success() {
        LegalRepresentationRequest req = new LegalRepresentationRequest(
                "pac-menor", "pac-madre", "MADRE", "REGISTRO_CIVIL_FOLIO_123"
        );

        User usuario = new User(10L, "usr-pac", "pac@hospital.com", "HASH", "ACTIVO", 0, null, now, false);
        when(userRepository.findByPublicId("usr-pac")).thenReturn(Optional.of(usuario));

        Patient menor = new Patient(
                1L, 101L, "pac-menor", "RC", "1065111", "Lucas", "Torres", LocalDate.of(2020, 1, 1), "300", now, now
        );
        Patient madre = new Patient(
                2L, 10L, "pac-madre", "CC", "1065222", "Ana", "Torres", LocalDate.of(1990, 1, 1), "300", now, now
        );
        when(patientRepository.findByPublicId("pac-menor")).thenReturn(Optional.of(menor));
        when(patientRepository.findByPublicId("pac-madre")).thenReturn(Optional.of(madre));

        LegalRepresentationResponse resp = service.registerLegalRepresentation(req, "usr-pac", "127.0.0.1");

        assertNotNull(resp);
        assertEquals("pac-menor", resp.minorPublicId());
        assertEquals("pac-madre", resp.representativePublicId());
        assertEquals("MADRE", resp.relationship());
        verify(representationRepository).save(any(LegalRepresentation.class));
        verify(auditService).audit(any(AuditEvent.class));
    }

    @Test
    @DisplayName("A02: generarPlantillaExcel produce un archivo XLSX válido con hojas y columnas requeridas")
    void generateExcelTemplate_valid() throws Exception {
        byte[] bytes = service.generateExcelTemplate();
        assertNotNull(bytes);
        assertTrue(bytes.length > 1000);

        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bytes);
             Workbook workbook = new XSSFWorkbook(bais)) {
            assertEquals(2, workbook.getNumberOfSheets());
            Sheet sheetAfiliados = workbook.getSheet("AFILIADOS_EPS");
            assertNotNull(sheetAfiliados);
            Row header = sheetAfiliados.getRow(0);
            assertEquals("TIPO_DOCUMENTO", header.getCell(0).getStringCellValue());
            assertEquals("NUMERO_DOCUMENTO", header.getCell(1).getStringCellValue());
            assertEquals("NOMBRES", header.getCell(2).getStringCellValue());
            assertEquals("APELLIDOS", header.getCell(3).getStringCellValue());
            assertEquals("REGIMEN", header.getCell(4).getStringCellValue());
            assertEquals("TIPO_AFILIADO", header.getCell(5).getStringCellValue());

            Sheet sheetInstrucciones = workbook.getSheet("INSTRUCCIONES");
            assertNotNull(sheetInstrucciones);
            assertTrue(sheetInstrucciones.getLastRowNum() >= 7);
        }

        // Exportar a frontend/assets para disponibilidad estática directa
        java.io.File assetsDir = new java.io.File("../frontend/assets");
        if (assetsDir.exists()) {
            java.nio.file.Files.write(new java.io.File(assetsDir, "plantilla_afiliados_eps.xlsx").toPath(), bytes);
        }
    }
}
