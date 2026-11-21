package com.meditriaje.dto.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Métricas agregadas de agendamiento y estado de citas (F2.6, RF-30).
 */
public record AppointmentMetricsDto(
        @JsonProperty("totalCitas")
        long totalAppointments,
        @JsonProperty("programadas")
        long scheduled,
        @JsonProperty("confirmadas")
        long confirmed,
        @JsonProperty("atendidas")
        long attended,
        @JsonProperty("canceladas")
        long cancelled,
        @JsonProperty("noAsistio")
        long noShow,
        @JsonProperty("reprogramadas")
        long rescheduled,
        @JsonProperty("tasaCumplimiento")
        double complianceRate,
        @JsonProperty("tasaCancelacion")
        double cancellationRate,
        @JsonProperty("porEspecialidad")
        List<DistributionItemDto> bySpecialty,
        @JsonProperty("porSede")
        List<DistributionItemDto> bySite
) {}
