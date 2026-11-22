package com.meditriaje.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String TEST_SECRET = "0123456789012345678901234567890123456789"; // > 256 bits
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, 15);
    }

    @Test
    void constructor_secretShort_throwsException() {
        assertThatThrownBy(() -> new JwtService("secreto-demasiado-corto", 15))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos 256 bits");
    }

    @Test
    void generateAccessToken_createsTokenValidWithSubjectAndClaims() {
        String publicId = "d7c80521-4cb5-45dc-9081-4ebdf768f771";
        String email = "paciente@example.com";
        List<String> roles = List.of("ROLE_PACIENTE");

        String token = jwtService.generateAccessToken(publicId, email, roles);

        assertThat(token).isNotBlank();
        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(jwtService.extractPublicId(token)).isEqualTo(publicId);
        assertThat(jwtService.extractRoles(token)).containsExactly("ROLE_PACIENTE");

        Claims claims = jwtService.extractClaims(token);
        assertThat(claims.get("email", String.class)).isEqualTo(email);
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void isValid_tokenInvalidOrMalformed_returnsFalse() {
        assertThat(jwtService.isValid("token.invalido.malformado")).isFalse();
        assertThat(jwtService.isValid(null)).isFalse();
        assertThat(jwtService.isValid("")).isFalse();
    }

    @Test
    void generateMfaChallengeToken_createsChallengeValidAndRechazadoByIsValid() {
        String publicId = "user-mfa-123";
        String email = "medico@example.com";
        List<String> roles = List.of("ROLE_PROFESIONAL");

        String challengeToken = jwtService.generateMfaChallengeToken(publicId, email, roles);

        assertThat(challengeToken).isNotBlank();
        assertThat(jwtService.isValidMfaChallenge(challengeToken)).isTrue();
        assertThat(jwtService.isValid(challengeToken)).isFalse(); // No debe ser aceptado como access token de sesión
        assertThat(jwtService.isAccessToken(challengeToken)).isFalse();
        assertThat(jwtService.extractPublicId(challengeToken)).isEqualTo(publicId);
    }
}
