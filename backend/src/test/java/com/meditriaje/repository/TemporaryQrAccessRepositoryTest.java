package com.meditriaje.repository;

import com.meditriaje.model.TemporaryQrAccess;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Accesos temporales QR vía PKG_OPERATIONS (sin SQL en Java). */
class TemporaryQrAccessRepositoryTest {

    private final PackageGateway gateway = mock(PackageGateway.class);
    private final TemporaryQrAccessRepository repository = new TemporaryQrAccessRepository(gateway);

    @Test
    @DisplayName("crear ejecuta CREATE_QR_ACCESS con banderas 0/1 y retorna ID")
    void create_executesProcedureAndReturnsId() {
        Instant expira = Instant.now().plusSeconds(900);
        TemporaryQrAccess acceso = new TemporaryQrAccess(
                null,
                "qr-uuid-1",
                10L,
                "tokenhash123456",
                null,
                true,
                false,
                true,
                false,
                3,
                0,
                false,
                0,
                expira,
                Instant.now(),
                Instant.now()
        );

        when(gateway.execute(eq(TemporaryQrAccessRepository.CREATE_QR_ACCESS), any(SqlParameterSource.class)))
                .thenReturn(Map.of("o_id", new BigDecimal("42")));

        Long id = repository.create(acceso);

        assertThat(id).isEqualTo(42L);
        verify(gateway).execute(eq(TemporaryQrAccessRepository.CREATE_QR_ACCESS), argThat((SqlParameterSource p) ->
                "qr-uuid-1".equals(p.getValue("p_public_id"))
                        && Long.valueOf(10L).equals(p.getValue("p_patient_id"))
                        && p.getValue("p_pin_hash") == null
                        && Integer.valueOf(1).equals(p.getValue("p_include_allergies"))
                        && Integer.valueOf(0).equals(p.getValue("p_include_medications"))
                        && Timestamp.from(expira).equals(p.getValue("p_expires_at"))));
    }

    @Test
    @DisplayName("buscarPorTokenHash retorna Optional con entidad si existe")
    void findByTokenHash_findsRecord() {
        String tokenHash = "hash-token-abc";
        TemporaryQrAccess esperado = new TemporaryQrAccess(
                1L, "qr-1", 10L, tokenHash, null,
                true, true, true, true,
                3, 0, false, Instant.now().plusSeconds(900), Instant.now(), null
        );

        when(gateway.<TemporaryQrAccess>single(
                eq(TemporaryQrAccessRepository.GET_QR_ACCESS_BY_TOKEN_HASH), any(SqlParameterSource.class), eq("o_access")))
                .thenReturn(Optional.of(esperado));

        Optional<TemporaryQrAccess> resultado = repository.findByTokenHash(tokenHash);

        assertThat(resultado).contains(esperado);
    }

    @Test
    @DisplayName("buscarPorTokenHash retorna Optional vacío cuando no hay filas")
    void findByTokenHash_notFound() {
        String tokenHash = "inexistente";
        when(gateway.<TemporaryQrAccess>single(
                eq(TemporaryQrAccessRepository.GET_QR_ACCESS_BY_TOKEN_HASH), any(SqlParameterSource.class), eq("o_access")))
                .thenReturn(Optional.empty());

        Optional<TemporaryQrAccess> resultado = repository.findByTokenHash(tokenHash);

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("buscarPorPublicId retorna Optional con entidad")
    void findByPublicId_findsRecord() {
        String publicId = "qr-uuid-abc";
        TemporaryQrAccess esperado = new TemporaryQrAccess(
                1L, publicId, 10L, "hash", null,
                true, true, true, true,
                3, 0, false, Instant.now().plusSeconds(900), Instant.now(), null
        );

        when(gateway.<TemporaryQrAccess>single(
                eq(TemporaryQrAccessRepository.GET_QR_ACCESS_BY_PUBLIC_ID), any(SqlParameterSource.class), eq("o_access")))
                .thenReturn(Optional.of(esperado));

        Optional<TemporaryQrAccess> resultado = repository.findByPublicId(publicId);

        assertThat(resultado).contains(esperado);
    }

    @Test
    @DisplayName("listarPorPacienteId retorna lista ordenada")
    void listByPatientId_returnsList() {
        Long pacienteId = 10L;
        TemporaryQrAccess a1 = new TemporaryQrAccess(
                1L, "qr-1", pacienteId, "h1", null, true, true, true, true, 3, 0, false,
                Instant.now(), Instant.now(), null);
        when(gateway.<TemporaryQrAccess>list(
                eq(TemporaryQrAccessRepository.LIST_QR_ACCESSES_BY_PATIENT), any(SqlParameterSource.class), eq("o_rows")))
                .thenReturn(List.of(a1));

        List<TemporaryQrAccess> lista = repository.listByPatientId(pacienteId);

        assertThat(lista).containsExactly(a1);
    }

    @Test
    @DisplayName("registrarAcceso ejecuta RECORD_QR_ACCESS y retorna filas actualizadas")
    void recordAccess_executesProcedure() {
        Long id = 5L;
        Instant now = Instant.now();
        when(gateway.execute(eq(TemporaryQrAccessRepository.RECORD_QR_ACCESS), any(SqlParameterSource.class)))
                .thenReturn(Map.of("o_updated", BigDecimal.ONE));

        int actualizadas = repository.recordAccess(id, now);

        assertThat(actualizadas).isEqualTo(1);
        verify(gateway).execute(eq(TemporaryQrAccessRepository.RECORD_QR_ACCESS), argThat((SqlParameterSource p) ->
                id.equals(p.getValue("p_id")) && Timestamp.from(now).equals(p.getValue("p_now"))));
    }

    @Test
    @DisplayName("registrarPinFallido ejecuta RECORD_FAILED_QR_PIN y retorna filas actualizadas")
    void recordFailedPin_executesProcedure() {
        when(gateway.execute(eq(TemporaryQrAccessRepository.RECORD_FAILED_QR_PIN), any(SqlParameterSource.class)))
                .thenReturn(Map.of("o_updated", BigDecimal.ONE));

        assertThat(repository.recordFailedPin(7L)).isEqualTo(1);
    }

    @Test
    @DisplayName("revocar ejecuta REVOKE_QR_ACCESS con publicId y pacienteId")
    void revoke_executesProcedure() {
        String publicId = "qr-123";
        Long pacienteId = 10L;
        when(gateway.execute(eq(TemporaryQrAccessRepository.REVOKE_QR_ACCESS), any(SqlParameterSource.class)))
                .thenReturn(Map.of("o_updated", BigDecimal.ONE));

        int actualizadas = repository.revoke(publicId, pacienteId);

        assertThat(actualizadas).isEqualTo(1);
        verify(gateway).execute(eq(TemporaryQrAccessRepository.REVOKE_QR_ACCESS), argThat((SqlParameterSource p) ->
                publicId.equals(p.getValue("p_public_id")) && pacienteId.equals(p.getValue("p_patient_id"))));
    }
}
