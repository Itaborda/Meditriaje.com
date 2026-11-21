package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meditriaje.model.Profesional;
import com.meditriaje.repository.plsql.PackageGateway;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** Profesionales vía PKG_STAFF (sin SQL en Java). */
class ProfesionalRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final ProfesionalRepository repository = new ProfesionalRepository(gateway);

    @Test
    void crear_usaCcPorDefectoYDevuelveElIdGenerado() {
        when(gateway.execute(eq(ProfesionalRepository.CREATE_PROFESSIONAL), any(SqlParameterSource.class)))
                .thenReturn(Map.of("o_professional_id", new BigDecimal("77")));
        Profesional prof = new Profesional(10L, "prof-uuid", 5L, "RM-12345", "Carlos", "Perez");

        Long id = repository.crear(prof);

        assertThat(id).isEqualTo(77L);
        verify(gateway).execute(eq(ProfesionalRepository.CREATE_PROFESSIONAL), argThat((SqlParameterSource p) ->
                "CC".equals(p.getValue("p_document_type"))
                        && "prof-uuid".equals(p.getValue("p_public_id"))
                        && Long.valueOf(10L).equals(p.getValue("p_user_id"))));
    }

    @Test
    void actualizar_enviaLosCamposEditables() {
        repository.actualizar(new Profesional(10L, "prof-uuid", 5L, "RM-12345", "Carlos", "Perez"));

        verify(gateway).execute(eq(ProfesionalRepository.UPDATE_PROFESSIONAL), argThat((SqlParameterSource p) ->
                "prof-uuid".equals(p.getValue("p_public_id"))
                        && Long.valueOf(5L).equals(p.getValue("p_specialty_id"))
                        && "Carlos".equals(p.getValue("p_first_names"))
                        && "Perez".equals(p.getValue("p_last_names"))
                        && p.getValue("p_phone") == null));
    }

    @Test
    void buscarPorPublicId_devuelveLaPrimeraFila() {
        Profesional prof = new Profesional(10L, "prof-uuid", 5L, "RM-12345", "Carlos", "Perez");
        when(gateway.list(eq(ProfesionalRepository.GET_PROFESSIONAL_BY_PUBLIC_ID), any(SqlParameterSource.class),
                eq("o_professional"))).thenReturn(List.of(prof));

        Optional<Profesional> res = repository.buscarPorPublicId("prof-uuid");

        assertThat(res).map(Profesional::registroMedico).contains("RM-12345");
    }

    @Test
    void buscarPorId_sinFilasEsVacio() {
        when(gateway.list(eq(ProfesionalRepository.GET_PROFESSIONAL_BY_ID), any(SqlParameterSource.class),
                eq("o_professional"))).thenReturn(List.of());

        assertThat(repository.buscarPorId(1L)).isEmpty();
    }

    @Test
    void existePorDocumento_conDatosNulosNoLlamaALaBase() {
        assertThat(repository.existePorDocumento(null, "1")).isFalse();
        assertThat(repository.existePorDocumentoYNoPublicId("CC", null, "x")).isFalse();

        verifyNoInteractions(gateway);
    }

    @Test
    void existePorDocumento_segunConteo() {
        when(gateway.callFunction(eq(ProfesionalRepository.COUNT_BY_DOCUMENT), any(SqlParameterSource.class)))
                .thenReturn(BigDecimal.ONE)
                .thenReturn(BigDecimal.ZERO);

        assertThat(repository.existePorDocumento("CC", "1020304050")).isTrue();
        assertThat(repository.existePorDocumento("CC", "1020304050")).isFalse();
    }

    @Test
    void existePorRegistroMedicoYNoPublicId_enviaAmbosParametros() {
        when(gateway.callFunction(eq(ProfesionalRepository.COUNT_OTHER_BY_MEDICAL_REGISTRY), any(SqlParameterSource.class)))
                .thenReturn(BigDecimal.ONE);

        assertThat(repository.existePorRegistroMedicoYNoPublicId("RM-12345", "prof-uuid")).isTrue();
        verify(gateway).callFunction(eq(ProfesionalRepository.COUNT_OTHER_BY_MEDICAL_REGISTRY),
                argThat((SqlParameterSource p) -> "RM-12345".equals(p.getValue("p_medical_registry"))
                        && "prof-uuid".equals(p.getValue("p_public_id"))));
    }

    @Test
    void listar_normalizaFiltrosYCalculaElDesplazamiento() {
        when(gateway.list(eq(ProfesionalRepository.LIST_PROFESSIONALS), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of());

        assertThat(repository.listar(2, 10, " esp-uuid ", "activo")).isEmpty();

        verify(gateway).list(eq(ProfesionalRepository.LIST_PROFESSIONALS), argThat((SqlParameterSource p) ->
                "esp-uuid".equals(p.getValue("p_specialty_public_id"))
                        && "ACTIVO".equals(p.getValue("p_status"))
                        && Integer.valueOf(20).equals(p.getValue("p_offset"))
                        && Integer.valueOf(10).equals(p.getValue("p_size"))), eq("o_rows"));
    }

    @Test
    void contar_filtrosEnBlancoNoFiltranYNullEsCero() {
        when(gateway.callFunction(eq(ProfesionalRepository.COUNT_PROFESSIONALS), any(SqlParameterSource.class)))
                .thenReturn(new BigDecimal("5"))
                .thenReturn(null);

        assertThat(repository.contar(" ", null)).isEqualTo(5);
        assertThat(repository.contar(null, null)).isZero();
        verify(gateway, times(2)).callFunction(eq(ProfesionalRepository.COUNT_PROFESSIONALS),
                argThat((SqlParameterSource p) -> p.getValue("p_specialty_public_id") == null
                        && p.getValue("p_status") == null));
    }
}
