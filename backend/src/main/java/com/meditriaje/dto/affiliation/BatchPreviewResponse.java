package com.meditriaje.dto.affiliation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record BatchPreviewResponse(
        @JsonProperty("lotePublicId")
        String batchPublicId,
        @JsonProperty("nombreArchivo")
        String fileName,
        @JsonProperty("epsCodigo")
        String epsCode,
        @JsonProperty("epsNombre")
        String epsName,
        @JsonProperty("totalFilas")
        int totalRows,
        @JsonProperty("filasValidas")
        int validRows,
        @JsonProperty("filasFallidas")
        int failedRows,
        @JsonProperty("estado")
        String status,
        @JsonProperty("muestraFilas")
        List<RowPreviewItem> sampleRows
) {
    public record RowPreviewItem(
            @JsonProperty("numeroFila")
            int rowNumber,
            @JsonProperty("tipoDocumento")
            String documentType,
            @JsonProperty("numeroDocumento")
            String documentNumber,
            @JsonProperty("nombres")
            String firstNames,
            @JsonProperty("apellidos")
            String lastNames,
            @JsonProperty("regimen")
            String regime,
            @JsonProperty("tipoAfiliado")
            String affiliateType,
            @JsonProperty("estadoFila")
            String rowStatus,
            @JsonProperty("errorMotivo")
            String errorReason
    ) {
    }
}
