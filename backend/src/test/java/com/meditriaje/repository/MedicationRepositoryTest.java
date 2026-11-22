package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meditriaje.dto.prescription.MedicationResponse;
import com.meditriaje.model.Medication;
import com.meditriaje.repository.plsql.PackageGateway;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** El repositorio de medicamentos delega en PKG_PHARMACY a través de la pasarela. */
class MedicationRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final MedicationRepository repository = new MedicationRepository(gateway);

    @Test
    @DisplayName("buscarPorPublicId - Retorna medicamento si existe")
    void findByPublicId_exists_returnsMedication() {
        Medication med = new Medication(
                1L, "med-uuid-1", "MED-ACE-500", "Acetaminofen", "Acetaminofen", "Tableta", "500 mg", "ACTIVO");
        when(gateway.<Medication>single(eq(MedicationRepository.GET_MEDICATION_BY_PUBLIC_ID),
                        argThat(p -> "med-uuid-1".equals(p.getValue("p_public_id"))), eq("o_row")))
                .thenReturn(Optional.of(med));

        Optional<Medication> resultado = repository.findByPublicId(" med-uuid-1 ");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().publicId()).isEqualTo("med-uuid-1");
        assertThat(resultado.get().code()).isEqualTo("MED-ACE-500");
        assertThat(resultado.get().isActive()).isTrue();
    }

    @Test
    @DisplayName("buscarPorPublicId - Retorna vacío si no existe")
    void findByPublicId_notFound_returnsEmpty() {
        when(gateway.<Medication>single(eq(MedicationRepository.GET_MEDICATION_BY_PUBLIC_ID),
                        any(SqlParameterSource.class), eq("o_row")))
                .thenReturn(Optional.empty());

        assertThat(repository.findByPublicId("med-inexistente")).isEmpty();
    }

    @Test
    @DisplayName("buscarPorPublicId - Retorna vacío con publicId nulo o en blanco sin consultar BD")
    void findByPublicId_nullOrBlank_returnsEmpty() {
        assertThat(repository.findByPublicId(null)).isEmpty();
        assertThat(repository.findByPublicId("   ")).isEmpty();
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("buscarPorId - Retorna medicamento si existe")
    void findById_exists_returnsMedication() {
        Medication med = new Medication(
                2L, "med-uuid-2", "MED-IBU-400", "Ibuprofeno", "Ibuprofeno", "Tableta", "400 mg", "ACTIVO");
        when(gateway.<Medication>single(eq(MedicationRepository.GET_MEDICATION_BY_ID),
                        argThat(p -> Long.valueOf(2L).equals(p.getValue("p_id"))), eq("o_row")))
                .thenReturn(Optional.of(med));

        Optional<Medication> resultado = repository.findById(2L);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().id()).isEqualTo(2L);
        assertThat(resultado.get().brandName()).isEqualTo("Ibuprofeno");
    }

    @Test
    @DisplayName("buscarPorId - Retorna vacío si id es nulo")
    void findById_null_returnsEmpty() {
        assertThat(repository.findById(null)).isEmpty();
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("listarActivos - Con filtro y paginación")
    void listActive_withFilter_returnsList() {
        MedicationResponse item = new MedicationResponse(
                "med-uuid-1", "MED-ACE-500", "Acetaminofen", "Acetaminofen", "Tableta", "500 mg", "ACTIVO");
        when(gateway.<MedicationResponse>list(eq(MedicationRepository.LIST_ACTIVE_MEDICATIONS),
                        argThat(p -> "%ACETA%".equals(p.getValue("p_pattern"))
                                && Integer.valueOf(0).equals(p.getValue("p_offset"))
                                && Integer.valueOf(10).equals(p.getValue("p_size"))),
                        eq("o_rows")))
                .thenReturn(List.of(item));

        List<MedicationResponse> resultado = repository.listActive("aceta", 0, 10);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).code()).isEqualTo("MED-ACE-500");
    }

    @Test
    @DisplayName("contarActivos - Retorna conteo total de activos")
    void countActive_withFilter_returnsTotal() {
        when(gateway.<Object>callFunction(eq(MedicationRepository.COUNT_ACTIVE_MEDICATIONS),
                        argThat(p -> "%MED%".equals(p.getValue("p_pattern")))))
                .thenReturn(new BigDecimal("16"));

        assertThat(repository.countActive("med")).isEqualTo(16);
    }

    @Test
    @DisplayName("contarActivos - Retorna 0 si la función retorna null")
    void countActive_null_returnsZero() {
        when(gateway.<Object>callFunction(eq(MedicationRepository.COUNT_ACTIVE_MEDICATIONS),
                        argThat(p -> p.getValue("p_pattern") == null)))
                .thenReturn(null);

        assertThat(repository.countActive(null)).isZero();
    }
}
