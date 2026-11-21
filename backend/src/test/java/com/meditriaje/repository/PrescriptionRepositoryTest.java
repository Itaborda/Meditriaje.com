package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meditriaje.dto.prescription.PrescriptionDetailResponse;
import com.meditriaje.dto.prescription.PrescriptionResponse;
import com.meditriaje.model.Prescription;
import com.meditriaje.model.PrescriptionDetail;
import com.meditriaje.repository.plsql.PackageGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** El repositorio de recetas delega en PKG_PHARMACY a través de la pasarela. */
class PrescriptionRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final PrescriptionRepository repository = new PrescriptionRepository(gateway);

    @Test
    @DisplayName("crearReceta - Inserta fila y retorna el ID autogenerado")
    void createPrescription_returnsGeneratedId() {
        Prescription receta = new Prescription(null, "receta-uuid-1", 10L, 20L, 30L, 30, Instant.now());
        when(gateway.execute(eq(PrescriptionRepository.CREATE_PRESCRIPTION),
                        argThat(p -> "receta-uuid-1".equals(p.getValue("p_public_id"))
                                && Long.valueOf(10L).equals(p.getValue("p_encounter_id"))
                                && Long.valueOf(20L).equals(p.getValue("p_patient_id"))
                                && Long.valueOf(30L).equals(p.getValue("p_professional_id"))
                                && Integer.valueOf(30).equals(p.getValue("p_validity_days")))))
                .thenReturn(Map.of("o_id", new BigDecimal("555")));

        Long id = repository.createPrescription(receta);

        assertThat(id).isEqualTo(555L);
    }

    @Test
    @DisplayName("crearReceta - Falla si el paquete no devuelve o_id")
    void createPrescription_withoutGeneratedId_fails() {
        Prescription receta = new Prescription(null, "receta-uuid-1", 10L, 20L, 30L, 30, Instant.now());
        when(gateway.execute(eq(PrescriptionRepository.CREATE_PRESCRIPTION), any(SqlParameterSource.class)))
                .thenReturn(new HashMap<>());

        assertThatThrownBy(() -> repository.createPrescription(receta)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("guardarDetalles - Invoca create_prescription_item por cada ítem con snapshots inmutables")
    void saveDetails_executesPerItem() {
        PrescriptionDetail detalle1 = new PrescriptionDetail(
                null, 555L, 100L, "Acetaminofen", "Acetaminofen", "Tableta", "500 mg",
                "500 mg", "Cada 8 horas", 5, 15, "Tomar con abundante agua");
        PrescriptionDetail detalle2 = new PrescriptionDetail(
                null, 555L, 101L, "Ibuprofeno", "Ibuprofeno", "Tableta", "400 mg",
                "400 mg", "Cada 12 horas", 3, 6, null);

        repository.saveDetails(555L, List.of(detalle1, detalle2));

        verify(gateway, times(2)).execute(eq(PrescriptionRepository.CREATE_PRESCRIPTION_ITEM), any(SqlParameterSource.class));
        verify(gateway).execute(eq(PrescriptionRepository.CREATE_PRESCRIPTION_ITEM),
                argThat(p -> Long.valueOf(555L).equals(p.getValue("p_prescription_id"))
                        && Long.valueOf(100L).equals(p.getValue("p_medication_id"))
                        && "Acetaminofen".equals(p.getValue("p_snapshot_name"))
                        && "Tomar con abundante agua".equals(p.getValue("p_instructions"))));
    }

    @Test
    @DisplayName("guardarDetalles - Lista nula o vacía no consulta la base")
    void saveDetails_emptyList_doesNothing() {
        repository.saveDetails(555L, null);
        repository.saveDetails(555L, List.of());
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("buscarPorPublicId - Retorna receta con detalles completos")
    void findByPublicId_returnsPrescriptionWithDetails() {
        PrescriptionResponse base = new PrescriptionResponse("receta-uuid-1", "atencion-uuid-1", "pac-uuid-1", "Juan Perez",
                "prof-uuid-1", "Carlos Gomez", "Medicina General", 30, Instant.now(), List.of());
        PrescriptionDetailResponse item = new PrescriptionDetailResponse("med-uuid-1", "MED-ACE-500", "Acetaminofen",
                "Acetaminofen", "Tableta", "500 mg", "500 mg", "Cada 8 horas", 5, 15, "Tomar con agua");

        when(gateway.<PrescriptionRepository.PrescriptionHeaderRow>single(
                        eq(PrescriptionRepository.GET_PRESCRIPTION_VIEW_BY_PUBLIC_ID),
                        argThat(p -> "receta-uuid-1".equals(p.getValue("p_public_id"))), eq("o_row")))
                .thenReturn(Optional.of(new PrescriptionRepository.PrescriptionHeaderRow(555L, base)));
        when(gateway.<PrescriptionDetailResponse>list(eq(PrescriptionRepository.LIST_PRESCRIPTION_ITEMS),
                        argThat(p -> Long.valueOf(555L).equals(p.getValue("p_prescription_id"))), eq("o_rows")))
                .thenReturn(List.of(item));

        Optional<PrescriptionResponse> resultado = repository.findByPublicId(" receta-uuid-1 ");

        assertThat(resultado).isPresent();
        PrescriptionResponse receta = resultado.get();
        assertThat(receta.publicId()).isEqualTo("receta-uuid-1");
        assertThat(receta.patientName()).isEqualTo("Juan Perez");
        assertThat(receta.professionalName()).isEqualTo("Carlos Gomez");
        assertThat(receta.specialtyName()).isEqualTo("Medicina General");
        assertThat(receta.details()).containsExactly(item);
    }

    @Test
    @DisplayName("buscarPorPublicId - Retorna vacío con publicId nulo o en blanco")
    void findByPublicId_nullOrBlank_returnsEmpty() {
        assertThat(repository.findByPublicId(null)).isEmpty();
        assertThat(repository.findByPublicId("   ")).isEmpty();
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("buscarEntidadPorPublicId - Retorna Receta si existe")
    void findEntityByPublicId_exists_returnsEntity() {
        Prescription receta = new Prescription(555L, "receta-uuid-1", 10L, 20L, 30L, 30, Instant.now());
        when(gateway.<Prescription>single(eq(PrescriptionRepository.GET_PRESCRIPTION_BY_PUBLIC_ID),
                        argThat(p -> "receta-uuid-1".equals(p.getValue("p_public_id"))), eq("o_row")))
                .thenReturn(Optional.of(receta));

        Optional<Prescription> resultado = repository.findEntityByPublicId("receta-uuid-1");

        assertThat(resultado).contains(receta);
    }

    @Test
    @DisplayName("buscarEntidadPorPublicId - Retorna vacío si no existe")
    void findEntityByPublicId_notFound_returnsEmpty() {
        when(gateway.<Prescription>single(eq(PrescriptionRepository.GET_PRESCRIPTION_BY_PUBLIC_ID),
                        any(SqlParameterSource.class), eq("o_row")))
                .thenReturn(Optional.empty());

        assertThat(repository.findEntityByPublicId("receta-inexistente")).isEmpty();
    }

    @Test
    @DisplayName("buscarDetallesPorRecetaId - RecetaId nulo retorna lista vacía")
    void findDetailsByPrescriptionId_null_returnsEmpty() {
        assertThat(repository.findDetailsByPrescriptionId(null)).isEmpty();
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("existePorAtencionId - Interpreta el conteo de recetas por atención")
    void existsByAttentionId_tests() {
        assertThat(repository.existsByAttentionId(null)).isFalse();
        verifyNoInteractions(gateway);

        when(gateway.<Object>callFunction(eq(PrescriptionRepository.COUNT_PRESCRIPTIONS_BY_ENCOUNTER),
                        argThat(p -> Long.valueOf(10L).equals(p.getValue("p_encounter_id")))))
                .thenReturn(BigDecimal.ONE, BigDecimal.ZERO);

        assertThat(repository.existsByAttentionId(10L)).isTrue();
        assertThat(repository.existsByAttentionId(10L)).isFalse();
    }

    @Test
    @DisplayName("listarPorPacienteId - Retorna lista vacía si pacienteId es nulo")
    void listByPatientId_null_returnsEmpty() {
        assertThat(repository.listByPatientId(null, 0, 10)).isEmpty();
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("listarPorPacienteId - Pagina, limita el tamaño a 100 y completa detalles")
    void listByPatientId_success() {
        PrescriptionResponse base = new PrescriptionResponse("receta-uuid-1", "atencion-uuid-1", "pac-uuid-1", "Juan Perez",
                "prof-uuid-1", "Carlos Gomez", "Medicina General", 30, Instant.now(), List.of());
        PrescriptionDetailResponse item = new PrescriptionDetailResponse("med-uuid-1", "MED-ACE-500", "Acetaminofen",
                "Acetaminofen", "Tableta", "500 mg", "500 mg", "Cada 8 horas", 5, 15, "Tomar con agua");

        when(gateway.<PrescriptionRepository.PrescriptionHeaderRow>list(eq(PrescriptionRepository.LIST_PRESCRIPTIONS_BY_PATIENT),
                        argThat(p -> Long.valueOf(20L).equals(p.getValue("p_patient_id"))
                                && Integer.valueOf(100).equals(p.getValue("p_offset"))
                                && Integer.valueOf(100).equals(p.getValue("p_size"))),
                        eq("o_rows")))
                .thenReturn(List.of(new PrescriptionRepository.PrescriptionHeaderRow(555L, base)));
        when(gateway.<PrescriptionDetailResponse>list(eq(PrescriptionRepository.LIST_PRESCRIPTION_ITEMS),
                        any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of(item));

        List<PrescriptionResponse> resultado = repository.listByPatientId(20L, 1, 500);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).publicId()).isEqualTo("receta-uuid-1");
        assertThat(resultado.get(0).details()).containsExactly(item);
    }

    @Test
    @DisplayName("contarPorPacienteId - Retorna 0 si pacienteId es nulo o conteo desde BD")
    void countByPatientId_tests() {
        assertThat(repository.countByPatientId(null)).isZero();
        verifyNoInteractions(gateway);

        when(gateway.<Object>callFunction(eq(PrescriptionRepository.COUNT_PRESCRIPTIONS_BY_PATIENT),
                        argThat(p -> Long.valueOf(20L).equals(p.getValue("p_patient_id")))))
                .thenReturn(new BigDecimal("5"));

        assertThat(repository.countByPatientId(20L)).isEqualTo(5);
    }
}
