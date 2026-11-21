package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meditriaje.dto.admin.AdminAppointmentDetailResponse;
import com.meditriaje.dto.appointment.AppointmentResponse;
import com.meditriaje.model.Appointment;
import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** El repositorio de citas no contiene SQL: declara las rutinas de PKG_SCHEDULING y delega en PackageGateway. */
class AppointmentRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final AppointmentRepository repository = new AppointmentRepository(gateway);

    @Test
    void declaresPkgSchedulingRoutines() {
        assertThat(AppointmentRepository.CREATE_APPOINTMENT.qualifiedName()).isEqualTo("PKG_SCHEDULING.create_appointment");
        assertThat(AppointmentRepository.CREATE_APPOINTMENT.parameters())
                .extracting(ProcedureSpec.Param::name)
                .containsExactly("p_public_id", "p_slot_id", "p_patient_id", "p_triage_id",
                        "p_origin_appointment_id", "p_status", "p_cancel_reason", "o_id");
        assertThat(AppointmentRepository.LIST_ADMIN_APPOINTMENTS.qualifiedName())
                .isEqualTo("PKG_SCHEDULING.list_admin_appointments");
    }

    @Test
    void create_insertsAppointmentAndReturnsGeneratedId() {
        Appointment cita = new Appointment("cita-uuid-1", 10L, 20L, null, null, "PROGRAMADA", null);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(gateway.execute(eq(AppointmentRepository.CREATE_APPOINTMENT), params.capture()))
                .thenReturn(Map.of("o_id", new BigDecimal("101")));

        Long id = repository.create(cita);

        assertThat(id).isEqualTo(101L);
        SqlParameterSource sent = params.getValue();
        assertThat(sent.getValue("p_public_id")).isEqualTo("cita-uuid-1");
        assertThat(sent.getValue("p_slot_id")).isEqualTo(10L);
        assertThat(sent.getValue("p_patient_id")).isEqualTo(20L);
        assertThat(sent.getValue("p_triage_id")).isNull();
        assertThat(sent.getSqlType("p_triage_id")).isEqualTo(Types.NUMERIC);
        assertThat(sent.getSqlType("p_origin_appointment_id")).isEqualTo(Types.NUMERIC);
        assertThat(sent.getValue("p_status")).isEqualTo("PROGRAMADA");
        assertThat(sent.getSqlType("p_cancel_reason")).isEqualTo(Types.VARCHAR);
    }

    @Test
    void findByPublicId_returnsOptionalWithAppointmentResponse() {
        AppointmentResponse mockResponse = sampleCitaResponse();
        when(gateway.<AppointmentResponse>list(
                        eq(AppointmentRepository.GET_APPOINTMENT_DETAIL), any(SqlParameterSource.class), eq("o_row")))
                .thenReturn(List.of(mockResponse));

        Optional<AppointmentResponse> resultado = repository.findByPublicId("cita-uuid-1");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().publicId()).isEqualTo("cita-uuid-1");
        assertThat(resultado.get().patientName()).isEqualTo("Juan Perez");
        assertThat(resultado.get().professionalName()).isEqualTo("Dr. House");
        assertThat(resultado.get().status()).isEqualTo("PROGRAMADA");
    }

    @Test
    void findEntityByPublicIdAndById_returnOptionalWithAppointment() {
        Appointment mockCita = new Appointment(
                1L, "cita-uuid-1", 10L, 20L, null, null, "PROGRAMADA", null, Instant.now(), null);
        when(gateway.<Appointment>list(
                        eq(AppointmentRepository.GET_APPOINTMENT_BY_PUBLIC_ID), any(SqlParameterSource.class), eq("o_row")))
                .thenReturn(List.of(mockCita));
        when(gateway.<Appointment>list(eq(AppointmentRepository.GET_APPOINTMENT_BY_ID), any(SqlParameterSource.class), eq("o_row")))
                .thenReturn(List.of(mockCita));

        assertThat(repository.findEntityByPublicId("cita-uuid-1")).contains(mockCita);
        assertThat(repository.findEntityById(1L)).contains(mockCita);
    }

    @Test
    void updateStatus_delegatesToPackageWithParams() {
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);

        repository.updateStatus(1L, "CANCELADA", "Paciente solicito cancelacion");

        verify(gateway).execute(eq(AppointmentRepository.UPDATE_APPOINTMENT_STATUS), params.capture());
        assertThat(params.getValue().getValue("p_id")).isEqualTo(1L);
        assertThat(params.getValue().getValue("p_status")).isEqualTo("CANCELADA");
        assertThat(params.getValue().getValue("p_cancel_reason")).isEqualTo("Paciente solicito cancelacion");
    }

    @Test
    void existsActiveAppointmentInSlot_returnsBasedOnCount() {
        when(gateway.<Number>callFunction(
                        eq(AppointmentRepository.COUNT_ACTIVE_APPOINTMENTS_IN_SLOT), any(SqlParameterSource.class)))
                .thenReturn(new BigDecimal("1"), new BigDecimal("0"));

        assertThat(repository.existsActiveAppointmentInSlot(10L)).isTrue();
        assertThat(repository.existsActiveAppointmentInSlot(10L)).isFalse();
    }

    @Test
    void listProfessionalAgenda_normalizesStatusAndPaginates() {
        AppointmentResponse mockResponse = sampleCitaResponse();
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(gateway.<AppointmentResponse>list(
                        eq(AppointmentRepository.LIST_PROFESSIONAL_AGENDA), params.capture(), eq("o_rows")))
                .thenReturn(List.of(mockResponse));

        List<AppointmentResponse> resultado = repository.listProfessionalAgenda(
                5L, Instant.now(), Instant.now().plusSeconds(3600), " programada ", 1, 20);

        assertThat(resultado).hasSize(1);
        assertThat(params.getValue().getValue("p_professional_id")).isEqualTo(5L);
        assertThat(params.getValue().getValue("p_status")).isEqualTo("PROGRAMADA");
        assertThat(params.getValue().getValue("p_offset")).isEqualTo(20);
        assertThat(params.getValue().getValue("p_limit")).isEqualTo(20);
    }

    @Test
    void countProfessionalAgenda_returnsTotalOrZeroWhenNull() {
        when(gateway.<Number>callFunction(
                        eq(AppointmentRepository.COUNT_PROFESSIONAL_AGENDA), any(SqlParameterSource.class)))
                .thenReturn(new BigDecimal("3"), null);

        assertThat(repository.countProfessionalAgenda(5L, null, null, null)).isEqualTo(3);
        assertThat(repository.countProfessionalAgenda(5L, null, null, " ")).isZero();
    }

    @Test
    void listAndCountByPatientId_returnResults() {
        when(gateway.<AppointmentResponse>list(
                        eq(AppointmentRepository.LIST_PATIENT_APPOINTMENTS), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of(sampleCitaResponse()));
        when(gateway.<Number>callFunction(
                        eq(AppointmentRepository.COUNT_PATIENT_APPOINTMENTS), any(SqlParameterSource.class)))
                .thenReturn(new BigDecimal("5"), null);

        assertThat(repository.listByPatientId(10L, 0, 10)).hasSize(1);
        assertThat(repository.countByPatientId(10L)).isEqualTo(5);
        assertThat(repository.countByPatientId(10L)).isZero();
    }

    @Test
    void listAdminAppointments_withZeroSizeSendsNullLimit() {
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(gateway.<AdminAppointmentDetailResponse>list(
                        eq(AppointmentRepository.LIST_ADMIN_APPOINTMENTS), params.capture(), eq("o_rows")))
                .thenReturn(List.of());

        repository.listAdminAppointments(null, null, " prof-1 ", " ", " programada ", 0, 0);

        SqlParameterSource sent = params.getValue();
        assertThat(sent.getValue("p_professional_public_id")).isEqualTo("prof-1");
        assertThat(sent.getValue("p_specialty_public_id")).isNull();
        assertThat(sent.getValue("p_status")).isEqualTo("PROGRAMADA");
        assertThat(sent.getValue("p_limit")).isNull();
        assertThat(sent.getSqlType("p_limit")).isEqualTo(Types.NUMERIC);
    }

    private static AppointmentResponse sampleCitaResponse() {
        return new AppointmentResponse(
                "cita-uuid-1", "slot-uuid-1", "pac-uuid-1", "Juan Perez",
                "prof-uuid-1", "Dr. House", "esp-uuid-1", "Medicina General",
                "sede-uuid-1", "Sede Norte", "Calle 100",
                Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "PROGRAMADA", null, null, Instant.now());
    }
}
