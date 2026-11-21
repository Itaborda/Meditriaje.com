package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meditriaje.dto.affiliation.AffiliationResponse;
import com.meditriaje.model.PatientAffiliation;
import com.meditriaje.repository.plsql.PackageGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** El repositorio de AFILIACION_PACIENTE delega en PKG_AFFILIATIONS a través de la pasarela. */
class PatientAffiliationRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final PatientAffiliationRepository repository = new PatientAffiliationRepository(gateway);

    @Test
    void save_insertsAndRetrievesById() {
        PatientAffiliation nueva = new PatientAffiliation(
                null, "af-1", null, "CC", "1001", 2L, "CONTRIBUTIVO", null, null, null, null, null, null, null);
        PatientAffiliation guardada = new PatientAffiliation(
                10L, "af-1", null, "CC", "1001", 2L, "CONTRIBUTIVO", "COTIZANTE", "ACTIVO",
                null, "CARGA_MASIVA", Instant.now(), Instant.now(), Instant.now());

        when(gateway.execute(eq(PatientAffiliationRepository.CREATE_AFFILIATION),
                        argThat(p -> "af-1".equals(p.getValue("p_public_id"))
                                && p.getValue("p_patient_id") == null
                                && p.getValue("p_affiliate_type") == null)))
                .thenReturn(Map.of("o_id", BigDecimal.TEN));
        when(gateway.<PatientAffiliation>single(eq(PatientAffiliationRepository.GET_AFFILIATION_BY_ID),
                        argThat(p -> Long.valueOf(10L).equals(p.getValue("p_id"))), eq("o_row")))
                .thenReturn(Optional.of(guardada));

        assertThat(repository.save(nueva)).isEqualTo(guardada);
    }

    @Test
    void save_failsWhenRowNotRetrieved() {
        PatientAffiliation nueva = new PatientAffiliation(
                null, "af-1", null, "CC", "1001", 2L, "CONTRIBUTIVO", null, null, null, null, null, null, null);
        when(gateway.execute(eq(PatientAffiliationRepository.CREATE_AFFILIATION), any(SqlParameterSource.class)))
                .thenReturn(Map.of());

        assertThatThrownBy(() -> repository.save(nueva)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void findByNullId_doesNotQueryDatabase() {
        assertThat(repository.findById(null)).isEmpty();
        verifyNoInteractions(gateway);
    }

    @Test
    void updatesAndViews_useTheirRoutines() {
        AffiliationResponse resp = new AffiliationResponse(
                "af-1", "pac-1", "CC", "1001", "Ana Diaz", "EPS037", "NUEVA EPS",
                "CONTRIBUTIVO", "COTIZANTE", "ACTIVO", null, "CARGA_MASIVA", Instant.now());
        when(gateway.<AffiliationResponse>single(eq(PatientAffiliationRepository.GET_AFFILIATION_DETAIL_BY_DOCUMENT),
                        any(SqlParameterSource.class), eq("o_row")))
                .thenReturn(Optional.of(resp));
        when(gateway.<AffiliationResponse>single(eq(PatientAffiliationRepository.GET_AFFILIATION_DETAIL_BY_PATIENT_ID),
                        any(SqlParameterSource.class), eq("o_row")))
                .thenReturn(Optional.of(resp));

        Instant verifAt = Instant.now();
        repository.updateAffiliation(10L, 3L, "SUBSIDIADO", "BENEFICIARIO", "ACTIVO", verifAt);
        repository.linkPatient("CC", "1001", 77L);

        verify(gateway).execute(eq(PatientAffiliationRepository.UPDATE_AFFILIATION),
                argThat(p -> Long.valueOf(10L).equals(p.getValue("p_id"))
                        && Long.valueOf(3L).equals(p.getValue("p_eps_id"))));
        verify(gateway).execute(eq(PatientAffiliationRepository.LINK_PATIENT_AFFILIATION),
                argThat(p -> Long.valueOf(77L).equals(p.getValue("p_patient_id"))));
        assertThat(repository.findDetailByDocument("CC", "1001")).contains(resp);
        assertThat(repository.findDetailByPatientId(77L)).contains(resp);
    }
}
