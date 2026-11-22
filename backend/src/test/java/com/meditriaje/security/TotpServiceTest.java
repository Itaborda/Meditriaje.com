package com.meditriaje.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private TotpService totpService;

    @BeforeEach
    void setUp() {
        totpService = new TotpService();
    }

    @Test
    void generateNewSecret_returnsSecretDatabase32Of32Chars() {
        String secreto = totpService.generateNewSecret();

        assertThat(secreto).isNotNull().hasSize(32);
        assertThat(secreto).matches("^[A-Z2-7]{32}$");
    }

    @Test
    void generateCode_returnsCodeNumericoOf6Digitos() {
        String secreto = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
        Instant ahora = Instant.now();

        String codigo = totpService.generateCode(secreto, ahora);

        assertThat(codigo).isNotNull().hasSize(6);
        assertThat(codigo).matches("^[0-9]{6}$");
    }

    @Test
    void validateCode_acceptsCodeValidInSameStep() {
        String secreto = totpService.generateNewSecret();
        Instant ahora = Instant.ofEpochSecond(1700000000L); // Paso exacto

        String codigo = totpService.generateCode(secreto, ahora);

        assertThat(totpService.validateCode(secreto, codigo, ahora)).isTrue();
    }

    @Test
    void validateCode_acceptsCodeWithDerivaOfAStep() {
        String secreto = totpService.generateNewSecret();
        Instant t0 = Instant.ofEpochSecond(1700000000L);
        Instant tMenos25s = t0.minusSeconds(25); // Paso anterior (-30s ventana)
        Instant tMas25s = t0.plusSeconds(25);   // Paso siguiente (+30s ventana)

        String codigo = totpService.generateCode(secreto, t0);

        assertThat(totpService.validateCode(secreto, codigo, tMenos25s)).isTrue();
        assertThat(totpService.validateCode(secreto, codigo, tMas25s)).isTrue();
    }

    @Test
    void validateCode_rejectsCodeOutsideWindow() {
        String secreto = totpService.generateNewSecret();
        Instant t0 = Instant.ofEpochSecond(1700000000L);
        Instant tFuera = t0.plusSeconds(90); // 3 pasos después

        String codigoAntiguo = totpService.generateCode(secreto, t0);

        assertThat(totpService.validateCode(secreto, codigoAntiguo, tFuera)).isFalse();
    }

    @Test
    void validateCode_rejectsInvalidOrWrongFormatCode() {
        String secreto = totpService.generateNewSecret();
        Instant ahora = Instant.now();

        assertThat(totpService.validateCode(secreto, "000000", ahora)).isFalse();
        assertThat(totpService.validateCode(secreto, "abc", ahora)).isFalse();
        assertThat(totpService.validateCode(secreto, null, ahora)).isFalse();
        assertThat(totpService.validateCode(null, "123456", ahora)).isFalse();
    }

    @Test
    void generateOtpAuthUri_construyeUriStandardCompatible() {
        String uri = totpService.generateOtpAuthUri("MediTriaje", "dr.perez@hospital.com", "JBSWY3DPEHPK3PXP");

        assertThat(uri).startsWith("otpauth://totp/MediTriaje:dr.perez%40hospital.com");
        assertThat(uri).contains("secret=JBSWY3DPEHPK3PXP");
        assertThat(uri).contains("issuer=MediTriaje");
        assertThat(uri).contains("algorithm=SHA1");
        assertThat(uri).contains("digits=6");
        assertThat(uri).contains("period=30");
    }
}
