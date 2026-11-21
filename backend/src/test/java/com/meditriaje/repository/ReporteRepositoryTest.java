package com.meditriaje.repository;

import com.meditriaje.dto.report.DistribucionItemDto;
import com.meditriaje.repository.ReporteRepository.CountRow;
import com.meditriaje.repository.ReporteRepository.DispensationStatusRow;
import com.meditriaje.repository.ReporteRepository.DistributionRow;
import com.meditriaje.repository.plsql.PackageGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reportes operativos vía PKG_REPORTS (sin SQL en Java). */
class ReporteRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final ReporteRepository reporteRepository = new ReporteRepository(gateway);

    @Test
    @DisplayName("contarCitasPorEstado - envía ventana temporal y mapea estados")
    void contarCitasPorEstado_exito() {
        Instant desde = Instant.now().minusSeconds(86400);
        Instant hasta = Instant.now();

        when(gateway.<CountRow>list(eq(ReporteRepository.COUNT_APPOINTMENTS_BY_STATUS), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of(
                        new CountRow("ATENDIDA", 15L),
                        new CountRow("CANCELADA", 3L)
                ));

        Map<String, Long> estados = reporteRepository.contarCitasPorEstado(desde, hasta);

        assertThat(estados).containsEntry("ATENDIDA", 15L).containsEntry("CANCELADA", 3L);
        verify(gateway).list(eq(ReporteRepository.COUNT_APPOINTMENTS_BY_STATUS), argThat((SqlParameterSource p) ->
                Timestamp.from(desde).equals(p.getValue("p_from"))
                        && Timestamp.from(hasta).equals(p.getValue("p_to"))), eq("o_rows"));
    }

    @Test
    @DisplayName("obtenerCitasPorEspecialidad - calcula porcentajes y redondeo")
    void obtenerCitasPorEspecialidad_exito() {
        when(gateway.<DistributionRow>list(eq(ReporteRepository.LIST_APPOINTMENTS_BY_SPECIALTY), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of(
                        new DistributionRow("esp-1", "Medicina General", 2L),
                        new DistributionRow("esp-2", "Pediatría", 1L)
                ));

        List<DistribucionItemDto> items = reporteRepository.obtenerCitasPorEspecialidad(null, null);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).publicId()).isEqualTo("esp-1");
        assertThat(items.get(0).etiqueta()).isEqualTo("Medicina General");
        assertThat(items.get(0).cantidad()).isEqualTo(2L);
        assertThat(items.get(0).porcentaje()).isEqualTo(66.67);

        assertThat(items.get(1).etiqueta()).isEqualTo("Pediatría");
        assertThat(items.get(1).cantidad()).isEqualTo(1L);
        assertThat(items.get(1).porcentaje()).isEqualTo(33.33);
    }

    @Test
    @DisplayName("obtenerCitasPorSede - agrupa por sede hospitalaria")
    void obtenerCitasPorSede_exito() {
        when(gateway.<DistributionRow>list(eq(ReporteRepository.LIST_APPOINTMENTS_BY_SITE), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of(
                        new DistributionRow("sed-1", "Sede Norte", 60L),
                        new DistributionRow("sed-2", "Sede Centro", 40L)
                ));

        List<DistribucionItemDto> items = reporteRepository.obtenerCitasPorSede(null, null);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).etiqueta()).isEqualTo("Sede Norte");
        assertThat(items.get(0).porcentaje()).isEqualTo(60.0);
    }

    @Test
    @DisplayName("contarTriajesPorNivel, emergencias y rutas - consulta correcta")
    void contarTriajes_exito() {
        when(gateway.<CountRow>list(eq(ReporteRepository.COUNT_TRIAGES_BY_LEVEL), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of(new CountRow("II", 8L)));
        when(gateway.callFunction(eq(ReporteRepository.COUNT_EMERGENCY_TRIAGES), any(SqlParameterSource.class)))
                .thenReturn(new BigDecimal("4"));
        when(gateway.<DistributionRow>list(eq(ReporteRepository.LIST_TRIAGES_BY_ROUTE), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of(new DistributionRow("URGENCIAS", "URGENCIAS", 5L)));

        Map<String, Long> niveles = reporteRepository.contarTriajesPorNivel(null, null);
        long emergencias = reporteRepository.contarTriajesEmergencia(null, null);
        List<DistribucionItemDto> rutas = reporteRepository.obtenerTriajesPorRuta(null, null);

        assertThat(niveles).containsEntry("II", 8L);
        assertThat(emergencias).isEqualTo(4L);
        assertThat(rutas).containsExactly(new DistribucionItemDto("URGENCIAS", "URGENCIAS", 5L, 100.0));
    }

    @Test
    @DisplayName("contarRecetasPorEstadoDispensacion - mapea pendientes, parciales, totales y default en cero")
    void contarRecetasPorEstadoDispensacion_exito() {
        when(gateway.<DispensationStatusRow>single(eq(ReporteRepository.COUNT_DISPENSATION_STATUSES), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(Optional.of(new DispensationStatusRow(10L, 5L, 20L)))
                .thenReturn(Optional.empty());
        when(gateway.callFunction(eq(ReporteRepository.COUNT_PRESCRIPTIONS), any(SqlParameterSource.class)))
                .thenReturn(new BigDecimal("35"));
        when(gateway.callFunction(eq(ReporteRepository.SUM_DISPENSED_UNITS), any(SqlParameterSource.class)))
                .thenReturn(new BigDecimal("150"));

        long emitidas = reporteRepository.contarRecetasEmitidas(null, null);
        long unidades = reporteRepository.contarUnidadesDispensadas(null, null);
        Map<String, Long> estados = reporteRepository.contarRecetasPorEstadoDispensacion(null, null);
        Map<String, Long> vacio = reporteRepository.contarRecetasPorEstadoDispensacion(null, null);

        assertThat(emitidas).isEqualTo(35L);
        assertThat(unidades).isEqualTo(150L);
        assertThat(estados).containsEntry("PENDIENTE", 10L)
                .containsEntry("DISPENSADA_PARCIAL", 5L)
                .containsEntry("DISPENSADA_TOTAL", 20L);
        assertThat(vacio).containsEntry("PENDIENTE", 0L)
                .containsEntry("DISPENSADA_PARCIAL", 0L)
                .containsEntry("DISPENSADA_TOTAL", 0L);
    }

    @Test
    @DisplayName("contarActivacionesBreakGlass y activos - métricas de emergencia y guarda de nulo")
    void metricasBreakGlass_exito() {
        Instant ahora = Instant.now();
        when(gateway.callFunction(eq(ReporteRepository.COUNT_BREAK_GLASS_ACTIVATIONS), any(SqlParameterSource.class)))
                .thenReturn(new BigDecimal("6"));
        when(gateway.callFunction(eq(ReporteRepository.COUNT_BREAK_GLASS_ACTIVE), any(SqlParameterSource.class)))
                .thenReturn(new BigDecimal("2"));
        when(gateway.<DistributionRow>list(eq(ReporteRepository.LIST_BREAK_GLASS_BY_SPECIALTY), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of(new DistributionRow("esp-1", "Urgencias", 6L)));

        long total = reporteRepository.contarActivacionesBreakGlass(null, null);
        long activas = reporteRepository.contarBreakGlassActivos(ahora);
        List<DistribucionItemDto> porEsp = reporteRepository.obtenerBreakGlassPorEspecialidad(null, null);

        assertThat(total).isEqualTo(6L);
        assertThat(activas).isEqualTo(2L);
        assertThat(porEsp).hasSize(1);
        assertThat(porEsp.get(0).cantidad()).isEqualTo(6L);
        assertThat(porEsp.get(0).porcentaje()).isEqualTo(100.0);

        assertThatThrownBy(() -> reporteRepository.contarBreakGlassActivos(null))
                .isInstanceOf(NullPointerException.class);
    }
}
