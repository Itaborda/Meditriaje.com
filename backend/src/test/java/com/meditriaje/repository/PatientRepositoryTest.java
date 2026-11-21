package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meditriaje.model.Patient;
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
class PatientRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final PatientRepository repository = new PatientRepository(gateway);

    private static Patient paciente() {
        return new Patient(10L, 1L, "pac-uuid", "CC", "12345678", "Pepito", "Perez",
                LocalDate.of(1990, 1, 1), "3001234567", Instant.now(), null);
    }

    @Test
    void findByUserId_returnsPatientIfExists() {
        when(gateway.<Patient>single(eq(PatientRepository.GET_PATIENT_BY_USER_ID),
                        argThat(p -> Long.valueOf(1L).equals(p.getValue("p_user_id"))), eq("o_row")))
                .thenReturn(Optional.of(paciente()));

        Optional<Patient> resultado = repository.findByUserId(1L);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().id()).isEqualTo(10L);
        assertThat(resultado.get().firstNames()).isEqualTo("Pepito");
    }

    @Test
    void findByUserId_returnsEmptyIfNotExists() {
        when(gateway.<Patient>single(eq(PatientRepository.GET_PATIENT_BY_USER_ID), any(SqlParameterSource.class),
                        eq("o_row")))
                .thenReturn(Optional.empty());

        assertThat(repository.findByUserId(99L)).isEmpty();
    }

    @Test
    void findByPublicIdAndDocument_useTheirRoutines() {
        when(gateway.<Patient>single(eq(PatientRepository.GET_PATIENT_BY_PUBLIC_ID),
                        argThat(p -> "pac-uuid".equals(p.getValue("p_public_id"))), eq("o_row")))
                .thenReturn(Optional.of(paciente()));
        when(gateway.<Patient>single(eq(PatientRepository.GET_PATIENT_BY_DOCUMENT),
                        argThat(p -> "CC".equals(p.getValue("p_document_type"))
                                && "12345678".equals(p.getValue("p_document_number"))), eq("o_row")))
                .thenReturn(Optional.of(paciente()));

        assertThat(repository.findByPublicId("pac-uuid")).isPresent();
        assertThat(repository.findByDocument("CC", "12345678")).isPresent();
    }

    @Test
    void existsByDocument_interpretsCount() {
        when(gateway.<Object>callFunction(eq(PatientRepository.COUNT_PATIENTS_BY_DOCUMENT),
                        any(SqlParameterSource.class)))
                .thenReturn(BigDecimal.ONE, BigDecimal.ZERO);

        assertThat(repository.existsByDocument("CC", "1")).isTrue();
        assertThat(repository.existsByDocument("CC", "1")).isFalse();
    }

    @Test
    void create_sendsDateAsSqlDateAndReturnsId() {
        Map<String, Object> out = new HashMap<>();
        out.put("o_id", new BigDecimal("77"));
        when(gateway.execute(eq(PatientRepository.CREATE_PATIENT),
                        argThat(p -> Date.valueOf(LocalDate.of(1990, 1, 1)).equals(p.getValue("p_birth_date")))))
                .thenReturn(out);

        Long id = repository.create(1L, "pub", "CC", "1", "Ana", "Diaz", LocalDate.of(1990, 1, 1), null);

        assertThat(id).isEqualTo(77L);
    }

    @Test
    void create_withoutGeneratedId_fails() {
        when(gateway.execute(eq(PatientRepository.CREATE_PATIENT), any(SqlParameterSource.class)))
                .thenReturn(new HashMap<>());

        assertThatThrownBy(() -> repository.create(1L, "pub", "CC", "1", "Ana", "Diaz", LocalDate.now(), null))
                .isInstanceOf(IllegalStateException.class);
    }
}
