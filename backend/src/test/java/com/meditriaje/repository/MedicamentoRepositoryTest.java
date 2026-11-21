package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meditriaje.dto.prescription.MedicamentoResponse;
import com.meditriaje.model.Medicamento;
import com.meditriaje.repository.plsql.PackageGateway;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** El repositorio de medicamentos delega en PKG_PHARMACY a través de la pasarela. */
class MedicamentoRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final MedicamentoRepository repository = new MedicamentoRepository(gateway);

    @Test
    @DisplayName("buscarPorPublicId - Retorna medicamento si existe")
    void buscarPorPublicId_existe_retornaMedicamento() {
        Medicamento med = new Medicamento(
                1L, "med-uuid-1", "MED-ACE-500", "Acetaminofen", "Acetaminofen", "Tableta", "500 mg", "ACTIVO");
        when(gateway.<Medicamento>single(eq(MedicamentoRepository.GET_MEDICATION_BY_PUBLIC_ID),
                        argThat(p -> "med-uuid-1".equals(p.getValue("p_public_id"))), eq("o_row")))
                .thenReturn(Optional.of(med));

        Optional<Medicamento> resultado = repository.buscarPorPublicId(" med-uuid-1 ");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().publicId()).isEqualTo("med-uuid-1");
        assertThat(resultado.get().codigo()).isEqualTo("MED-ACE-500");
        assertThat(resultado.get().estaActivo()).isTrue();
    }

    @Test
    @DisplayName("buscarPorPublicId - Retorna vacío si no existe")
    void buscarPorPublicId_noExiste_retornaVacio() {
        when(gateway.<Medicamento>single(eq(MedicamentoRepository.GET_MEDICATION_BY_PUBLIC_ID),
                        any(SqlParameterSource.class), eq("o_row")))
                .thenReturn(Optional.empty());

        assertThat(repository.buscarPorPublicId("med-inexistente")).isEmpty();
    }

    @Test
    @DisplayName("buscarPorPublicId - Retorna vacío con publicId nulo o en blanco sin consultar BD")
    void buscarPorPublicId_nuloOBlanco_retornaVacio() {
        assertThat(repository.buscarPorPublicId(null)).isEmpty();
        assertThat(repository.buscarPorPublicId("   ")).isEmpty();
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("buscarPorId - Retorna medicamento si existe")
    void buscarPorId_existe_retornaMedicamento() {
        Medicamento med = new Medicamento(
                2L, "med-uuid-2", "MED-IBU-400", "Ibuprofeno", "Ibuprofeno", "Tableta", "400 mg", "ACTIVO");
        when(gateway.<Medicamento>single(eq(MedicamentoRepository.GET_MEDICATION_BY_ID),
                        argThat(p -> Long.valueOf(2L).equals(p.getValue("p_id"))), eq("o_row")))
                .thenReturn(Optional.of(med));

        Optional<Medicamento> resultado = repository.buscarPorId(2L);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().id()).isEqualTo(2L);
        assertThat(resultado.get().nombreComercial()).isEqualTo("Ibuprofeno");
    }

    @Test
    @DisplayName("buscarPorId - Retorna vacío si id es nulo")
    void buscarPorId_nulo_retornaVacio() {
        assertThat(repository.buscarPorId(null)).isEmpty();
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("listarActivos - Con filtro y paginación")
    void listarActivos_conFiltro_retornaLista() {
        MedicamentoResponse item = new MedicamentoResponse(
                "med-uuid-1", "MED-ACE-500", "Acetaminofen", "Acetaminofen", "Tableta", "500 mg", "ACTIVO");
        when(gateway.<MedicamentoResponse>list(eq(MedicamentoRepository.LIST_ACTIVE_MEDICATIONS),
                        argThat(p -> "%ACETA%".equals(p.getValue("p_pattern"))
                                && Integer.valueOf(0).equals(p.getValue("p_offset"))
                                && Integer.valueOf(10).equals(p.getValue("p_size"))),
                        eq("o_rows")))
                .thenReturn(List.of(item));

        List<MedicamentoResponse> resultado = repository.listarActivos("aceta", 0, 10);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).codigo()).isEqualTo("MED-ACE-500");
    }

    @Test
    @DisplayName("contarActivos - Retorna conteo total de activos")
    void contarActivos_conFiltro_retornaTotal() {
        when(gateway.<Object>callFunction(eq(MedicamentoRepository.COUNT_ACTIVE_MEDICATIONS),
                        argThat(p -> "%MED%".equals(p.getValue("p_pattern")))))
                .thenReturn(new BigDecimal("16"));

        assertThat(repository.contarActivos("med")).isEqualTo(16);
    }

    @Test
    @DisplayName("contarActivos - Retorna 0 si la función retorna null")
    void contarActivos_null_retornaCero() {
        when(gateway.<Object>callFunction(eq(MedicamentoRepository.COUNT_ACTIVE_MEDICATIONS),
                        argThat(p -> p.getValue("p_pattern") == null)))
                .thenReturn(null);

        assertThat(repository.contarActivos(null)).isZero();
    }
}
