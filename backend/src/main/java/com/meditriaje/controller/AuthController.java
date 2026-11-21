package com.meditriaje.controller;

import com.meditriaje.dto.AuthSessionResponse;
import com.meditriaje.dto.AuthTokens;
import com.meditriaje.dto.ChangePasswordRequest;
import com.meditriaje.dto.LoginRequest;
import com.meditriaje.dto.RegisterPatientRequest;
import com.meditriaje.dto.RegisterPatientResponse;
import com.meditriaje.dto.auth.MfaAuthenticateRequest;
import com.meditriaje.dto.auth.MfaSetupResponse;
import com.meditriaje.dto.auth.MfaVerifyRequest;
import com.meditriaje.dto.auth.MfaVerifyResponse;
import com.meditriaje.dto.auth.ResetPasswordRequest;
import com.meditriaje.dto.auth.ResetPasswordResponse;
import com.meditriaje.dto.auth.RequestPasswordResetRequest;
import com.meditriaje.dto.auth.RequestPasswordResetResponse;
import com.meditriaje.service.AuthService;
import com.meditriaje.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Endpoints de autenticación, ciclo de vida de sesiones y registro (HU-01, ADR-002).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final boolean cookieSecure;

    public AuthController(
            AuthService authService,
            @Value("${security.cookie.secure:false}") boolean cookieSecure
    ) {
        this.authService = Objects.requireNonNull(authService, "AuthService no puede ser nulo");
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterPatientResponse> registerPatient(
            @Valid @RequestBody RegisterPatientRequest request,
            HttpServletRequest httpRequest
    ) {
        String sourceIp = IpUtil.extractIp(httpRequest);
        RegisterPatientResponse response = authService.registerPatient(request, sourceIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthSessionResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String sourceIp = IpUtil.extractIp(httpRequest);
        AuthTokens tokens = authService.login(request, sourceIp);

        // Si se requiere segundo factor MFA, retornar el desafío sin fijar cookies de sesión (ADR-014, F2.1.4)
        if (tokens.sessionResponse().mfaRequired()) {
            return ResponseEntity.ok(tokens.sessionResponse());
        }

        ResponseCookie accessCookie = createAccessCookie(tokens.accessToken(), Duration.ofMinutes(15));
        ResponseCookie refreshCookie = createRefreshCookie(tokens.rawRefreshToken(), Duration.ofDays(7));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(tokens.sessionResponse());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthSessionResponse> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletRequest httpRequest
    ) {
        String sourceIp = IpUtil.extractIp(httpRequest);
        AuthTokens tokens = authService.refresh(refreshToken, sourceIp);

        ResponseCookie accessCookie = createAccessCookie(tokens.accessToken(), Duration.ofMinutes(15));
        ResponseCookie refreshCookie = createRefreshCookie(tokens.rawRefreshToken(), Duration.ofDays(7));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(tokens.sessionResponse());
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletRequest httpRequest
    ) {
        String sourceIp = IpUtil.extractIp(httpRequest);
        authService.logout(refreshToken, sourceIp);

        ResponseCookie clearAccessCookie = createAccessCookie("", Duration.ZERO);
        ResponseCookie clearRefreshCookie = createRefreshCookie("", Duration.ZERO);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearAccessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie.toString())
                .body(Map.of("mensaje", "Sesion cerrada exitosamente."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<RequestPasswordResetResponse> forgotPassword(
            @Valid @RequestBody RequestPasswordResetRequest request,
            HttpServletRequest httpRequest
    ) {
        String sourceIp = IpUtil.extractIp(httpRequest);
        RequestPasswordResetResponse response = authService.requestPasswordReset(request, sourceIp);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        String sourceIp = IpUtil.extractIp(httpRequest);
        ResetPasswordResponse response = authService.resetPassword(request, sourceIp);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mfa/setup")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MfaSetupResponse> setupMfa(
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = (String) authentication.getPrincipal();
        String sourceIp = IpUtil.extractIp(httpRequest);
        MfaSetupResponse response = authService.setupMfa(usuarioPublicId, sourceIp);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mfa/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MfaVerifyResponse> verifyMfa(
            @Valid @RequestBody MfaVerifyRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = (String) authentication.getPrincipal();
        String sourceIp = IpUtil.extractIp(httpRequest);
        MfaVerifyResponse response = authService.verifyMfa(usuarioPublicId, request, sourceIp);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mfa/authenticate")
    public ResponseEntity<AuthSessionResponse> authenticateMfa(
            @Valid @RequestBody MfaAuthenticateRequest request,
            HttpServletRequest httpRequest
    ) {
        String sourceIp = IpUtil.extractIp(httpRequest);
        AuthTokens tokens = authService.authenticateMfa(request, sourceIp);

        ResponseCookie accessCookie = createAccessCookie(tokens.accessToken(), Duration.ofMinutes(15));
        ResponseCookie refreshCookie = createRefreshCookie(tokens.rawRefreshToken(), Duration.ofDays(7));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(tokens.sessionResponse());
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = (String) authentication.getPrincipal();
        String sourceIp = IpUtil.extractIp(httpRequest);
        authService.changePassword(usuarioPublicId, request, sourceIp);
        return ResponseEntity.ok(Map.of("mensaje", "Contrasena actualizada exitosamente."));
    }

    private ResponseCookie createAccessCookie(String token, Duration maxAge) {
        return ResponseCookie.from("access_token", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSecure ? "None" : "Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie createRefreshCookie(String token, Duration maxAge) {
        return ResponseCookie.from("refresh_token", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSecure ? "None" : "Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
