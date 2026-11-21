package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meditriaje.dto.clinical.ClinicalAttentionResponse;
import com.meditriaje.dto.clinical.AmendmentResponse;
import com.meditriaje.repository.plsql.PackageGateway;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** Pruebas unitarias de {@link AtencionRepository}: delega en PKG_CLINICAL a través de la pasarela. */
class ClinicalAttentionRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final ClinicalAttentionRepository atencionRepository = new ClinicalAttentionRepository(gateway);

    @Test
    void existsPreviousAttentionInWindow_returnsTrueWhenExists() {
        Instant fechaLimite = Instant.now().minusSeconds(86400 * 365);
        when(gateway.<Object>callFunction(eq(ClinicalAttentionRepository.COUNT_ENCOUNTERS_SINCE),
                        argThat(p -> Long.valueOf(2L).equals(p.getValue("p_patient_id"))
                                && Long.valueOf(1L).equals(p.getValue("p_professional_id"))
                                && Timestamp.from(fechaLimite).equals(p.getValue("p_since")))))
                .thenReturn(BigDecimal.ONE);

        assertThat(atencionRepository.existsPreviousAttentionInWindow(1L, 2L, fechaLimite)).isTrue();
    }

    @Test
    void existsPreviousAttentionInWindow_returnsFalseWhenEmpty() {
        when(gateway.<Object>callFunction(eq(ClinicalAttentionRepository.COUNT_ENCOUNTERS_SINCE), any(SqlParameterSource.class)))
                .thenReturn(BigDecimal.ZERO);

        assertThat(atencionRepository.existsPreviousAttentionInWindow(1L, 2L, Instant.now())).isFalse();
    }

    @Test
    void nulls_doNotQueryDatabase() {
        assertThat(atencionRepository.findAmendmentsByAttentionId(null)).isEmpty();
        assertThat(atencionRepository.listPatientHistory(null, 0, 10)).isEmpty();
        assertThat(atencionRepository.countPatientHistory(null)).isZero();
        verifyNoInteractions(gateway);
    }

    @Test
    void countPatientHistory_returnsTotal() {
        when(gateway.<Object>callFunction(eq(ClinicalAttentionRepository.COUNT_PATIENT_HISTORY), any(SqlParameterSource.class)))
                .thenReturn(new BigDecimal("3"));

        assertThat(atencionRepository.countPatientHistory(5L)).isEqualTo(3);
    }

    @Test
    void closeAttention_failsWithoutUpdatedRows() {
        when(gateway.execute(eq(ClinicalAttentionRepository.CLOSE_ENCOUNTER), any(SqlParameterSource.class)))
                .thenReturn(Map.of("o_affected", BigDecimal.ZERO));

        assertThatThrownBy(() -> atencionRepository.closeAttention(1L, 2L, "m", "e", "i", Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void listPatientHistory_limitsSizeAndCompletesVitalsAndAmendments() {
        ClinicalAttentionResponse base = new ClinicalAttentionResponse("at-1", "ci-1", "pa-1", "Ana", "pr-1", "Luis", "General",
                "CERRADA", Instant.now(), null, "J00", "Rino", "m", "e", "i", null, List.of());
        AmendmentResponse enmienda = new AmendmentResponse("pr-1", "Luis", "motivo", "contenido", Instant.now());
        when(gateway.<ClinicalAttentionRepository.EncounterDetailRow>list(eq(ClinicalAttentionRepository.LIST_PATIENT_HISTORY),
                        argThat(p -> Integer.valueOf(100).equals(p.getValue("p_size"))
                                && Integer.valueOf(100).equals(p.getValue("p_offset"))), eq("o_rows")))
                .thenReturn(List.of(new ClinicalAttentionRepository.EncounterDetailRow(9L, base)));
        when(gateway.list(eq(ClinicalAttentionRepository.LIST_VITAL_SIGNS), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of());
        when(gateway.<AmendmentResponse>list(eq(ClinicalAttentionRepository.LIST_AMENDMENTS), any(SqlParameterSource.class),
                        eq("o_rows")))
                .thenReturn(List.of(enmienda));

        List<ClinicalAttentionResponse> historia = atencionRepository.listPatientHistory(5L, 1, 500);

        assertThat(historia).hasSize(1);
        assertThat(historia.get(0).publicId()).isEqualTo("at-1");
        assertThat(historia.get(0).vitalSigns()).isNull();
        assertThat(historia.get(0).amendments()).containsExactly(enmienda);
    }
}
