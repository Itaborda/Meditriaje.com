package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meditriaje.model.Paciente;
import com.meditriaje.repository.plsql.PackageGateway;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** El repositorio de pacientes delega en PKG_PATIENTS a través de la pasarela. */
class PacienteRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final PacienteRepository repository = new PacienteRepository(gateway);

    private static Paciente paciente() {
        return new Paciente(10L, 1L, "pac-uuid", "CC", "12345678", "Pepito", "Perez",
                LocalDate.of(1990, 1, 1), "3001234567", Instant.now(), null);
    }

    @Test
    void buscarPorUsuarioId_retornaPacienteSiExiste() {
        when(gateway.<Paciente>single(eq(PacienteRepository.GET_PATIENT_BY_USER_ID),
                        argThat(p -> Long.valueOf(1L).equals(p.getValue("p_user_id"))), eq("o_row")))
                .thenReturn(Optional.of(paciente()));

        Optional<Paciente> resultado = repository.buscarPorUsuarioId(1L);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().id()).isEqualTo(10L);
        assertThat(resultado.get().nombres()).isEqualTo("Pepito");
    }

    @Test
    void buscarPorUsuarioId_retornaVacioSiNoExiste() {
        when(gateway.<Paciente>single(eq(PacienteRepository.GET_PATIENT_BY_USER_ID), any(SqlParameterSource.class),
                        eq("o_row")))
                .thenReturn(Optional.empty());

        assertThat(repository.buscarPorUsuarioId(99L)).isEmpty();
    }

    @Test
    void buscarPorPublicIdYDocumentoUsanSusRutinas() {
        when(gateway.<Paciente>single(eq(PacienteRepository.GET_PATIENT_BY_PUBLIC_ID),
                        argThat(p -> "pac-uuid".equals(p.getValue("p_public_id"))), eq("o_row")))
                .thenReturn(Optional.of(paciente()));
        when(gateway.<Paciente>single(eq(PacienteRepository.GET_PATIENT_BY_DOCUMENT),
                        argThat(p -> "CC".equals(p.getValue("p_document_type"))
                                && "12345678".equals(p.getValue("p_document_number"))), eq("o_row")))
                .thenReturn(Optional.of(paciente()));

        assertThat(repository.buscarPorPublicId("pac-uuid")).isPresent();
        assertThat(repository.buscarPorDocumento("CC", "12345678")).isPresent();
    }

    @Test
    void existePorDocumento_interpretaElConteo() {
        when(gateway.<Object>callFunction(eq(PacienteRepository.COUNT_PATIENTS_BY_DOCUMENT),
                        any(SqlParameterSource.class)))
                .thenReturn(BigDecimal.ONE, BigDecimal.ZERO);

        assertThat(repository.existePorDocumento("CC", "1")).isTrue();
        assertThat(repository.existePorDocumento("CC", "1")).isFalse();
    }

    @Test
    void crear_enviaLaFechaComoDateYDevuelveElId() {
        Map<String, Object> out = new HashMap<>();
        out.put("o_id", new BigDecimal("77"));
        when(gateway.execute(eq(PacienteRepository.CREATE_PATIENT),
                        argThat(p -> Date.valueOf(LocalDate.of(1990, 1, 1)).equals(p.getValue("p_birth_date")))))
                .thenReturn(out);

        Long id = repository.crear(1L, "pub", "CC", "1", "Ana", "Diaz", LocalDate.of(1990, 1, 1), null);

        assertThat(id).isEqualTo(77L);
    }

    @Test
    void crear_sinIdGeneradoFalla() {
        when(gateway.execute(eq(PacienteRepository.CREATE_PATIENT), any(SqlParameterSource.class)))
                .thenReturn(new HashMap<>());

        assertThatThrownBy(() -> repository.crear(1L, "pub", "CC", "1", "Ana", "Diaz", LocalDate.now(), null))
                .isInstanceOf(IllegalStateException.class);
    }
}
