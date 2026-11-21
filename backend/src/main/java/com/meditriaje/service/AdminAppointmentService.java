package com.meditriaje.service;

import com.meditriaje.dto.admin.AdminAppointmentDetailResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.repository.AppointmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Servicio administrativo para la consulta, calendario semanal y exportación de citas médicas (ADR-003, ADR-006, RF-26).
 * Toda operación temporal se calcula bajo la zona horaria institucional 'America/Bogota' (ADR-005).
 */
@Service
@Transactional(readOnly = true)
public class AdminAppointmentService {

    public static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a", new Locale("es", "CO"));

    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    @Autowired
    public AdminAppointmentService(AppointmentRepository appointmentRepository) {
        this(appointmentRepository, Clock.systemUTC());
    }

    public AdminAppointmentService(AppointmentRepository appointmentRepository, Clock clock) {
        this.appointmentRepository = Objects.requireNonNull(appointmentRepository, "CitaRepository no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "Clock no puede ser nulo");
    }

    /**
     * Consulta paginada de citas para el visor y calendario administrativo.
     */
    public PaginatedResponse<AdminAppointmentDetailResponse> listAppointments(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            String profesionalPublicId,
            String especialidadPublicId,
            String estado,
            int page,
            int size
    ) {
        if (page < 0) {
            throw new InvalidDataException("El número de página no puede ser menor a 0.");
        }
        if (size < 1 || size > 500) {
            throw new InvalidDataException("El tamaño de página debe estar entre 1 y 500.");
        }

        Instant desdeInstant = null;
        Instant hastaInstant = null;

        if (fechaDesde != null) {
            desdeInstant = fechaDesde.atStartOfDay(BOGOTA_ZONE).toInstant();
        }
        if (fechaHasta != null) {
            hastaInstant = fechaHasta.atTime(23, 59, 59, 999_999_999).atZone(BOGOTA_ZONE).toInstant();
        }

        if (desdeInstant != null && hastaInstant != null && desdeInstant.isAfter(hastaInstant)) {
            throw new InvalidDataException("La fecha inicial no puede ser posterior a la fecha final.");
        }

        List<AdminAppointmentDetailResponse> content = appointmentRepository.listAdminAppointments(
                desdeInstant,
                hastaInstant,
                profesionalPublicId,
                especialidadPublicId,
                estado,
                page,
                size
        );

        int total = appointmentRepository.countAdminAppointments(
                desdeInstant,
                hastaInstant,
                profesionalPublicId,
                especialidadPublicId,
                estado
        );

        return PaginatedResponse.of(content, page, size, (long) total);
    }

    /**
     * Genera la plantilla de exportación CSV/Excel compatible nativamente con Microsoft Excel.
     * Incorpora BOM UTF-8 y delimitador punto y coma (;) estándar para entornos hispanohablantes.
     */
    public byte[] exportAppointmentsCsv(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            String profesionalPublicId,
            String especialidadPublicId,
            String estado
    ) {
        Instant desdeInstant = null;
        Instant hastaInstant = null;

        if (fechaDesde != null) {
            desdeInstant = fechaDesde.atStartOfDay(BOGOTA_ZONE).toInstant();
        }
        if (fechaHasta != null) {
            hastaInstant = fechaHasta.atTime(23, 59, 59, 999_999_999).atZone(BOGOTA_ZONE).toInstant();
        }

        // Obtener hasta 10,000 registros para el reporte analítico
        List<AdminAppointmentDetailResponse> citas = appointmentRepository.listAdminAppointments(
                desdeInstant,
                hastaInstant,
                profesionalPublicId,
                especialidadPublicId,
                estado,
                0,
                10000
        );

        StringBuilder sb = new StringBuilder();
        // BOM UTF-8 para apertura directa en Microsoft Excel
        sb.append('\uFEFF');

        // Encabezados
        sb.append("Código Cita;Fecha Cita;Hora Inicio;Hora Fin;Modalidad;Estado;Paciente;Tipo Documento;Número Documento;Teléfono;Email;Médico Asignado;Registro Médico;Especialidad;Sede;Ciudad;Motivo Consulta;Nivel Triaje;Fecha Solicitud\r\n");

        for (AdminAppointmentDetailResponse c : citas) {
            String fechaCita = c.startDateTime() != null ? DATE_FMT.format(c.startDateTime().atZone(BOGOTA_ZONE)) : "";
            String horaInicio = c.startDateTime() != null ? TIME_FMT.format(c.startDateTime().atZone(BOGOTA_ZONE)) : "";
            String horaFin = c.endDateTime() != null ? TIME_FMT.format(c.endDateTime().atZone(BOGOTA_ZONE)) : "";
            String fechaCreacion = c.createdAt() != null ? DATE_FMT.format(c.createdAt().atZone(BOGOTA_ZONE)) : "";

            sb.append(sanitizeCsv(c.appointmentPublicId())).append(';')
              .append(sanitizeCsv(fechaCita)).append(';')
              .append(sanitizeCsv(horaInicio)).append(';')
              .append(sanitizeCsv(horaFin)).append(';')
              .append(sanitizeCsv(c.modality())).append(';')
              .append(sanitizeCsv(c.status())).append(';')
              .append(sanitizeCsv(c.patientName())).append(';')
              .append(sanitizeCsv(c.patientDocumentType())).append(';')
              .append(sanitizeCsv(c.patientDocumentNumber())).append(';')
              .append(sanitizeCsv(c.patientPhone())).append(';')
              .append(sanitizeCsv(c.patientEmail())).append(';')
              .append(sanitizeCsv(c.professionalName())).append(';')
              .append(sanitizeCsv(c.medicalLicense())).append(';')
              .append(sanitizeCsv(c.specialtyName())).append(';')
              .append(sanitizeCsv(c.siteName())).append(';')
              .append(sanitizeCsv(c.siteCity())).append(';')
              .append(sanitizeCsv(c.consultationReason())).append(';')
              .append(sanitizeCsv(c.triageLevel())).append(';')
              .append(sanitizeCsv(fechaCreacion)).append("\r\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String sanitizeCsv(String valor) {
        if (valor == null) return "";
        String s = valor.replace("\"", "\"\"");
        // Prevención de inyección CSV si inicia con fórmulas (=, +, -, @, \t, \r) (SEC-001)
        if (s.startsWith("=") || s.startsWith("+") || s.startsWith("-") || s.startsWith("@") || s.startsWith("\t") || s.startsWith("\r")) {
            s = "'" + s;
        }
        return "\"" + s + "\"";
    }
}
