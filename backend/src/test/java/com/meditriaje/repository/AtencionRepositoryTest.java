package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meditriaje.dto.clinical.AtencionResponse;
import com.meditriaje.dto.clinical.EnmiendaResponse;
import com.meditriaje.repository.plsql.PackageGateway;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** Pruebas unitarias de {@link AtencionRepository}: delega en PKG_CLINICAL a través de la pasarela. */
class AtencionRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final AtencionRepository atencionRepository = new AtencionRepository(gateway);

    @Test
    void existeAtencionPreviaEnVentana_retornaTrueCuandoExisteRegistro() {
        Instant fechaLimite = Instant.now().minusSeconds(86400 * 365);
        when(gateway.<Object>callFunction(eq(AtencionRepository.COUNT_ENCOUNTERS_SINCE),
                        argThat(p -> Long.valueOf(2L).equals(p.getValue("p_patient_id"))
                                && Long.valueOf(1L).equals(p.getValue("p_professional_id"))
                                && Timestamp.from(fechaLimite).equals(p.getValue("p_since")))))
                .thenReturn(BigDecimal.ONE);

        assertThat(atencionRepository.existeAtencionPreviaEnVentana(1L, 2L, fechaLimite)).isTrue();
    }

    @Test
    void existeAtencionPreviaEnVentana_retornaFalseCuandoNoHayRegistro() {
        when(gateway.<Object>callFunction(eq(AtencionRepository.COUNT_ENCOUNTERS_SINCE), any(SqlParameterSource.class)))
                .thenReturn(BigDecimal.ZERO);

        assertThat(atencionRepository.existeAtencionPreviaEnVentana(1L, 2L, Instant.now())).isFalse();
    }

    @Test
    void nulosNoConsultanLaBase() {
        assertThat(atencionRepository.buscarEnmiendasPorAtencionId(null)).isEmpty();
        assertThat(atencionRepository.listarHistoriaPaciente(null, 0, 10)).isEmpty();
        assertThat(atencionRepository.contarHistoriaPaciente(null)).isZero();
        verifyNoInteractions(gateway);
    }

    @Test
    void contarHistoriaPaciente_retornaTotal() {
        when(gateway.<Object>callFunction(eq(AtencionRepository.COUNT_PATIENT_HISTORY), any(SqlParameterSource.class)))
                .thenReturn(new BigDecimal("3"));

        assertThat(atencionRepository.contarHistoriaPaciente(5L)).isEqualTo(3);
    }

    @Test
    void cerrarAtencion_sinFilasActualizadasFalla() {
        when(gateway.execute(eq(AtencionRepository.CLOSE_ENCOUNTER), any(SqlParameterSource.class)))
                .thenReturn(Map.of("o_affected", BigDecimal.ZERO));

        assertThatThrownBy(() -> atencionRepository.cerrarAtencion(1L, 2L, "m", "e", "i", Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void listarHistoriaPaciente_limitaElTamanoYCompletaSignosYEnmiendas() {
        AtencionResponse base = new AtencionResponse("at-1", "ci-1", "pa-1", "Ana", "pr-1", "Luis", "General",
                "CERRADA", Instant.now(), null, "J00", "Rino", "m", "e", "i", null, List.of());
        EnmiendaResponse enmienda = new EnmiendaResponse("pr-1", "Luis", "motivo", "contenido", Instant.now());
        when(gateway.<AtencionRepository.EncounterDetailRow>list(eq(AtencionRepository.LIST_PATIENT_HISTORY),
                        argThat(p -> Integer.valueOf(100).equals(p.getValue("p_size"))
                                && Integer.valueOf(100).equals(p.getValue("p_offset"))), eq("o_rows")))
                .thenReturn(List.of(new AtencionRepository.EncounterDetailRow(9L, base)));
        when(gateway.list(eq(AtencionRepository.LIST_VITAL_SIGNS), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of());
        when(gateway.<EnmiendaResponse>list(eq(AtencionRepository.LIST_AMENDMENTS), any(SqlParameterSource.class),
                        eq("o_rows")))
                .thenReturn(List.of(enmienda));

        List<AtencionResponse> historia = atencionRepository.listarHistoriaPaciente(5L, 1, 500);

        assertThat(historia).hasSize(1);
        assertThat(historia.get(0).publicId()).isEqualTo("at-1");
        assertThat(historia.get(0).signosVitales()).isNull();
        assertThat(historia.get(0).enmiendas()).containsExactly(enmienda);
    }
}
