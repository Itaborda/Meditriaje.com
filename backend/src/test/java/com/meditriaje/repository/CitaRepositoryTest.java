package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meditriaje.dto.admin.AdminCitaDetalleResponse;
import com.meditriaje.dto.appointment.CitaResponse;
import com.meditriaje.model.Cita;
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
class CitaRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final CitaRepository repository = new CitaRepository(gateway);

    @Test
    void declaraLasRutinasDePkgScheduling() {
        assertThat(CitaRepository.CREATE_APPOINTMENT.qualifiedName()).isEqualTo("PKG_SCHEDULING.create_appointment");
        assertThat(CitaRepository.CREATE_APPOINTMENT.parameters())
                .extracting(ProcedureSpec.Param::name)
                .containsExactly("p_public_id", "p_slot_id", "p_patient_id", "p_triage_id",
                        "p_origin_appointment_id", "p_status", "p_cancel_reason", "o_id");
        assertThat(CitaRepository.LIST_ADMIN_APPOINTMENTS.qualifiedName())
                .isEqualTo("PKG_SCHEDULING.list_admin_appointments");
    }

    @Test
    void crear_insertaCitaYRetornaIdGenerado() {
        Cita cita = new Cita("cita-uuid-1", 10L, 20L, null, null, "PROGRAMADA", null);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(gateway.execute(eq(CitaRepository.CREATE_APPOINTMENT), params.capture()))
                .thenReturn(Map.of("o_id", new BigDecimal("101")));

        Long id = repository.crear(cita);

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
    void buscarPorPublicId_retornaOptionalConCitaResponse() {
        CitaResponse mockResponse = sampleCitaResponse();
        when(gateway.<CitaResponse>list(
                        eq(CitaRepository.GET_APPOINTMENT_DETAIL), any(SqlParameterSource.class), eq("o_row")))
                .thenReturn(List.of(mockResponse));

        Optional<CitaResponse> resultado = repository.buscarPorPublicId("cita-uuid-1");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().publicId()).isEqualTo("cita-uuid-1");
        assertThat(resultado.get().pacienteNombre()).isEqualTo("Juan Perez");
        assertThat(resultado.get().profesionalNombre()).isEqualTo("Dr. House");
        assertThat(resultado.get().estado()).isEqualTo("PROGRAMADA");
    }

    @Test
    void buscarEntidadPorPublicIdYPorId_retornanOptionalConCita() {
        Cita mockCita = new Cita(
                1L, "cita-uuid-1", 10L, 20L, null, null, "PROGRAMADA", null, Instant.now(), null);
        when(gateway.<Cita>list(
                        eq(CitaRepository.GET_APPOINTMENT_BY_PUBLIC_ID), any(SqlParameterSource.class), eq("o_row")))
                .thenReturn(List.of(mockCita));
        when(gateway.<Cita>list(eq(CitaRepository.GET_APPOINTMENT_BY_ID), any(SqlParameterSource.class), eq("o_row")))
                .thenReturn(List.of(mockCita));

        assertThat(repository.buscarEntidadPorPublicId("cita-uuid-1")).contains(mockCita);
        assertThat(repository.buscarEntidadPorId(1L)).contains(mockCita);
    }

    @Test
    void actualizarEstado_delegaEnElPaqueteConParametros() {
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);

        repository.actualizarEstado(1L, "CANCELADA", "Paciente solicito cancelacion");

        verify(gateway).execute(eq(CitaRepository.UPDATE_APPOINTMENT_STATUS), params.capture());
        assertThat(params.getValue().getValue("p_id")).isEqualTo(1L);
        assertThat(params.getValue().getValue("p_status")).isEqualTo("CANCELADA");
        assertThat(params.getValue().getValue("p_cancel_reason")).isEqualTo("Paciente solicito cancelacion");
    }

    @Test
    void existeCitaActivaEnSlot_retornaSegunConteo() {
        when(gateway.<Number>callFunction(
                        eq(CitaRepository.COUNT_ACTIVE_APPOINTMENTS_IN_SLOT), any(SqlParameterSource.class)))
                .thenReturn(new BigDecimal("1"), new BigDecimal("0"));

        assertThat(repository.existeCitaActivaEnSlot(10L)).isTrue();
        assertThat(repository.existeCitaActivaEnSlot(10L)).isFalse();
    }

    @Test
    void listarAgendaProfesional_normalizaEstadoYPagina() {
        CitaResponse mockResponse = sampleCitaResponse();
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(gateway.<CitaResponse>list(
                        eq(CitaRepository.LIST_PROFESSIONAL_AGENDA), params.capture(), eq("o_rows")))
                .thenReturn(List.of(mockResponse));

        List<CitaResponse> resultado = repository.listarAgendaProfesional(
                5L, Instant.now(), Instant.now().plusSeconds(3600), " programada ", 1, 20);

        assertThat(resultado).hasSize(1);
        assertThat(params.getValue().getValue("p_professional_id")).isEqualTo(5L);
        assertThat(params.getValue().getValue("p_status")).isEqualTo("PROGRAMADA");
        assertThat(params.getValue().getValue("p_offset")).isEqualTo(20);
        assertThat(params.getValue().getValue("p_limit")).isEqualTo(20);
    }

    @Test
    void contarAgendaProfesional_retornaTotalOCeroCuandoNull() {
        when(gateway.<Number>callFunction(
                        eq(CitaRepository.COUNT_PROFESSIONAL_AGENDA), any(SqlParameterSource.class)))
                .thenReturn(new BigDecimal("3"), null);

        assertThat(repository.contarAgendaProfesional(5L, null, null, null)).isEqualTo(3);
        assertThat(repository.contarAgendaProfesional(5L, null, null, " ")).isZero();
    }

    @Test
    void listarYContarPorPacienteId_retornanResultados() {
        when(gateway.<CitaResponse>list(
                        eq(CitaRepository.LIST_PATIENT_APPOINTMENTS), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of(sampleCitaResponse()));
        when(gateway.<Number>callFunction(
                        eq(CitaRepository.COUNT_PATIENT_APPOINTMENTS), any(SqlParameterSource.class)))
                .thenReturn(new BigDecimal("5"), null);

        assertThat(repository.listarPorPacienteId(10L, 0, 10)).hasSize(1);
        assertThat(repository.contarPorPacienteId(10L)).isEqualTo(5);
        assertThat(repository.contarPorPacienteId(10L)).isZero();
    }

    @Test
    void listarCitasAdmin_conSizeCeroEnviaLimitNull() {
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(gateway.<AdminCitaDetalleResponse>list(
                        eq(CitaRepository.LIST_ADMIN_APPOINTMENTS), params.capture(), eq("o_rows")))
                .thenReturn(List.of());

        repository.listarCitasAdmin(null, null, " prof-1 ", " ", " programada ", 0, 0);

        SqlParameterSource sent = params.getValue();
        assertThat(sent.getValue("p_professional_public_id")).isEqualTo("prof-1");
        assertThat(sent.getValue("p_specialty_public_id")).isNull();
        assertThat(sent.getValue("p_status")).isEqualTo("PROGRAMADA");
        assertThat(sent.getValue("p_limit")).isNull();
        assertThat(sent.getSqlType("p_limit")).isEqualTo(Types.NUMERIC);
    }

    private static CitaResponse sampleCitaResponse() {
        return new CitaResponse(
                "cita-uuid-1", "slot-uuid-1", "pac-uuid-1", "Juan Perez",
                "prof-uuid-1", "Dr. House", "esp-uuid-1", "Medicina General",
                "sede-uuid-1", "Sede Norte", "Calle 100",
                Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "PROGRAMADA", null, null, Instant.now());
    }
}
