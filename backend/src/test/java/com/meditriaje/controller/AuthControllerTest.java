package com.meditriaje.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meditriaje.config.CorsConfig;
import com.meditriaje.config.SecurityConfig;
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
import com.meditriaje.exception.InvalidCredentialsException;
import com.meditriaje.exception.GlobalExceptionHandler;
import com.meditriaje.exception.InvalidTokenException;
import com.meditriaje.security.CsrfHeaderFilter;
import com.meditriaje.security.CustomAccessDeniedHandler;
import com.meditriaje.security.CustomAuthenticationEntryPoint;
import com.meditriaje.security.JwtAuthenticationFilter;
import com.meditriaje.security.JwtService;
import com.meditriaje.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private com.meditriaje.security.JwtService jwtService;

    @Test
    void register_withPayloadValid_returns201Created() throws Exception {
        RegisterPatientRequest request = new RegisterPatientRequest(
                "CC",
                "1234567890",
                "Maria",
                "Gomez",
                LocalDate.of(1990, 1, 15),
                "3109876543",
                "maria.gomez@example.com",
                "PasswordSegura123*",
                "v1.0",
                true
        );

        RegisterPatientResponse mockResponse = new RegisterPatientResponse(
                "f81d4fae-7dec-11d0-a765-00a0c91e6bf6",
                "maria.gomez@example.com",
                "Maria",
                "Gomez",
                "Registro completado exitosamente."
        );

        when(authService.registerPatient(any(), anyString())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pacientePublicId").value("f81d4fae-7dec-11d0-a765-00a0c91e6bf6"))
                .andExpect(jsonPath("$.email").value("maria.gomez@example.com"))
                .andExpect(jsonPath("$.nombres").value("Maria"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register_conContrasenaCorta_retorna400BadRequest() throws Exception {
        RegisterPatientRequest request = new RegisterPatientRequest(
                "CC",
                "1234567890",
                "Maria",
                "Gomez",
                LocalDate.of(1990, 1, 15),
                "3109876543",
                "maria.gomez@example.com",
                "corta9", // < 10 caracteres
                "v1.0",
                true
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.mensaje").exists())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void register_withoutConsentAccepted_returns400BadRequest() throws Exception {
        RegisterPatientRequest request = new RegisterPatientRequest(
                "CC",
                "1234567890",
                "Maria",
                "Gomez",
                LocalDate.of(1990, 1, 15),
                "3109876543",
                "maria.gomez@example.com",
                "PasswordSegura123*",
                "v1.0",
                false
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.mensaje").exists());
    }

    // -------------------------------------------------------------------------
    // LOGIN
    // -------------------------------------------------------------------------

    @Test
    void login_credentialsValidas_returns200AndSetsCookiesHttpOnly() throws Exception {
        LoginRequest request = new LoginRequest("maria.gomez@example.com", "PasswordSegura123*");

        AuthSessionResponse session = new AuthSessionResponse(
                "f81d4fae-7dec-11d0-a765-00a0c91e6bf6",
                "maria.gomez@example.com",
                List.of("ROLE_PACIENTE"),
                "Inicio de sesion exitoso."
        );
        AuthTokens tokens = new AuthTokens("mock.jwt.token", "mock-refresh-token", session);

        when(authService.login(any(), anyString())).thenReturn(tokens);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("f81d4fae-7dec-11d0-a765-00a0c91e6bf6"))
                .andExpect(jsonPath("$.email").value("maria.gomez@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_PACIENTE"))
                .andExpect(jsonPath("$.token").doesNotExist()) // Tokens NO en body
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("access_token=mock.jwt.token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
    }

    @Test
    void login_credentialsInvalidas_returns401Unauthorized() throws Exception {
        LoginRequest request = new LoginRequest("maria.gomez@example.com", "ClaveErronea123*");

        when(authService.login(any(), anyString())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.mensaje").value("Credenciales invalidas."));
    }

    // -------------------------------------------------------------------------
    // REFRESH
    // -------------------------------------------------------------------------

    @Test
    void refresh_conCookieValida_retorna200YNuevasCookies() throws Exception {
        AuthSessionResponse session = new AuthSessionResponse(
                "f81d4fae-7dec-11d0-a765-00a0c91e6bf6",
                "maria.gomez@example.com",
                List.of("ROLE_PACIENTE"),
                "Sesion actualizada exitosamente."
        );
        AuthTokens tokens = new AuthTokens("new.jwt.token", "new-refresh-token", session);

        when(authService.refresh(anyString(), anyString())).thenReturn(tokens);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("refresh_token", "existing-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("f81d4fae-7dec-11d0-a765-00a0c91e6bf6"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("access_token=new.jwt.token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
    }

    @Test
    void refresh_sinCabeceraCsrf_retorna403Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", "existing-refresh-token")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CSRF_REQUERIDO"));
    }

    // -------------------------------------------------------------------------
    // LOGOUT
    // -------------------------------------------------------------------------

    @Test
    void logout_limpiaCookiesYRetorna200() throws Exception {
        doNothing().when(authService).logout(any(), anyString());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("refresh_token", "existing-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Sesion cerrada exitosamente."))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    // -------------------------------------------------------------------------
    // CHANGE PASSWORD
    // -------------------------------------------------------------------------

    @Test
    void changePassword_sinAutenticacion_retorna401Unauthorized() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("PasswordActual123*", "PasswordNuevo123*");

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void changePassword_withTokenValid_returns200OK() throws Exception {
        String tokenValido = "jwt.usuario.valido";
        String userPubId = "user-pub-id-1";
        ChangePasswordRequest request = new ChangePasswordRequest("PasswordActual123*", "PasswordNuevo123*");

        when(jwtService.isValid(tokenValido)).thenReturn(true);
        when(jwtService.isAccessToken(tokenValido)).thenReturn(true);
        when(jwtService.extractPublicId(tokenValido)).thenReturn(userPubId);
        when(jwtService.extractRoles(tokenValido)).thenReturn(List.of("ROLE_PACIENTE"));
        doNothing().when(authService).changePassword(eq(userPubId), any(), anyString());

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .cookie(new Cookie("access_token", tokenValido))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Contrasena actualizada exitosamente."));
    }

    @Test
    void changePassword_conContrasenaCorta_retorna400BadRequest() throws Exception {
        String tokenValido = "jwt.usuario.valido";
        when(jwtService.isValid(tokenValido)).thenReturn(true);
        when(jwtService.isAccessToken(tokenValido)).thenReturn(true);
        when(jwtService.extractPublicId(tokenValido)).thenReturn("user-pub-id-1");
        when(jwtService.extractRoles(tokenValido)).thenReturn(List.of("ROLE_PACIENTE"));

        ChangePasswordRequest request = new ChangePasswordRequest("PasswordActual123*", "corta");

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .cookie(new Cookie("access_token", tokenValido))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    void forgotPassword_validData_returns200OkWithMessageGenericWithoutAutenticacion() throws Exception {
        RequestPasswordResetRequest request = new RequestPasswordResetRequest("paciente@hospital.com");
        when(authService.requestPasswordReset(any(RequestPasswordResetRequest.class), anyString()))
                .thenReturn(RequestPasswordResetResponse.defaultResponse());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(RequestPasswordResetResponse.DEFAULT_MESSAGE));
    }

    @Test
    void forgotPassword_emailInvalid_returns400BadRequest() throws Exception {
        RequestPasswordResetRequest request = new RequestPasswordResetRequest("correo-no-valido");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    void resetPassword_validData_returns200OkWithoutAutenticacion() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest(
                "paciente@hospital.com",
                "123456",
                "NuevaClaveSegura123*"
        );
        when(authService.resetPassword(any(ResetPasswordRequest.class), anyString()))
                .thenReturn(ResetPasswordResponse.defaultResponse());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(ResetPasswordResponse.DEFAULT_MESSAGE));
    }

    @Test
    void resetPassword_codeInvalid_returns400BadRequest() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest(
                "paciente@hospital.com",
                "1234", // menos de 6 dígitos
                "NuevaClaveSegura123*"
        );

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    void resetPassword_passwordCorta_retorna400BadRequest() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest(
                "paciente@hospital.com",
                "123456",
                "corta" // menos de 10 caracteres
        );

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    // -------------------------------------------------------------------------
    // AUTENTICACIÓN MULTIFACTOR (MFA)
    // -------------------------------------------------------------------------

    @Test
    void login_withMfaEnabled_returns200WithMfaRequiredAndWithoutCookiesOfSession() throws Exception {
        LoginRequest request = new LoginRequest("dr.garcia@hospital.com", "PasswordSeguro123*");
        AuthSessionResponse session = AuthSessionResponse.mfaRequired(
                "usr-medico-1",
                "dr.garcia@hospital.com",
                List.of("ROLE_PROFESIONAL"),
                "challenge-jwt-123"
        );
        AuthTokens tokens = new AuthTokens(null, null, session);

        when(authService.login(any(LoginRequest.class), anyString())).thenReturn(tokens);

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequerido").value(true))
                .andExpect(jsonPath("$.mfaChallengeToken").value("challenge-jwt-123"))
                .andExpect(jsonPath("$.publicId").value("usr-medico-1"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void setupMfa_withTokenValid_returns200WithSecretAndQrUri() throws Exception {
        String tokenValido = "jwt.usuario.valido";
        String userPubId = "usr-medico-1";

        when(jwtService.isValid(tokenValido)).thenReturn(true);
        when(jwtService.isAccessToken(tokenValido)).thenReturn(true);
        when(jwtService.extractPublicId(tokenValido)).thenReturn(userPubId);
        when(jwtService.extractRoles(tokenValido)).thenReturn(List.of("ROLE_PROFESIONAL"));

        MfaSetupResponse mockResponse = new MfaSetupResponse(
                "JBSWY3DPEHPK3PXP",
                "otpauth://totp/MediTriaje:dr.garcia@hospital.com?secret=JBSWY3DPEHPK3PXP&issuer=MediTriaje",
                "MediTriaje",
                "dr.garcia@hospital.com"
        );

        when(authService.setupMfa(eq(userPubId), anyString())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/auth/mfa/setup")
                        .cookie(new Cookie("access_token", tokenValido))
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value("JBSWY3DPEHPK3PXP"))
                .andExpect(jsonPath("$.qrUri").value(containsString("otpauth://totp/")));
    }

    @Test
    void setupMfa_sinAutenticacion_retorna401Unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/mfa/setup")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void verifyMfa_withCodeValid_returns200WithCodesBackup() throws Exception {
        String tokenValido = "jwt.usuario.valido";
        String userPubId = "usr-medico-1";
        MfaVerifyRequest request = new MfaVerifyRequest("123456");

        when(jwtService.isValid(tokenValido)).thenReturn(true);
        when(jwtService.isAccessToken(tokenValido)).thenReturn(true);
        when(jwtService.extractPublicId(tokenValido)).thenReturn(userPubId);
        when(jwtService.extractRoles(tokenValido)).thenReturn(List.of("ROLE_PROFESIONAL"));

        MfaVerifyResponse mockResponse = new MfaVerifyResponse(
                true,
                List.of("1111-2222", "3333-4444"),
                "MFA activado con exito."
        );

        when(authService.verifyMfa(eq(userPubId), any(MfaVerifyRequest.class), anyString())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .cookie(new Cookie("access_token", tokenValido))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaHabilitado").value(true))
                .andExpect(jsonPath("$.backupCodes").isArray())
                .andExpect(jsonPath("$.backupCodes[0]").value("1111-2222"));
    }

    @Test
    void verifyMfa_withCodeInvalid_returns400BadRequest() throws Exception {
        String tokenValido = "jwt.usuario.valido";
        when(jwtService.isValid(tokenValido)).thenReturn(true);
        when(jwtService.isAccessToken(tokenValido)).thenReturn(true);

        MfaVerifyRequest request = new MfaVerifyRequest("123"); // longitud inválida

        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .cookie(new Cookie("access_token", tokenValido))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    void authenticateMfa_withDataValid_returns200AndCookiesOfSession() throws Exception {
        MfaAuthenticateRequest request = new MfaAuthenticateRequest("challenge-jwt-valido", "123456");
        AuthSessionResponse session = new AuthSessionResponse(
                "usr-medico-1",
                "dr.garcia@hospital.com",
                List.of("ROLE_PROFESIONAL"),
                "Inicio de sesion completado exitosamente con MFA.",
                false
        );
        AuthTokens tokens = new AuthTokens("access.jwt.valido", "refresh.raw.valido", session);

        when(authService.authenticateMfa(any(MfaAuthenticateRequest.class), anyString())).thenReturn(tokens);

        mockMvc.perform(post("/api/v1/auth/mfa/authenticate")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("usr-medico-1"))
                .andExpect(jsonPath("$.email").value("dr.garcia@hospital.com"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("access_token=access.jwt.valido")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
    }

    @Test
    void authenticateMfa_challengeExpiredOrInvalid_returns401Unauthorized() throws Exception {
        MfaAuthenticateRequest request = new MfaAuthenticateRequest("challenge-jwt-invalido", "123456");

        when(authService.authenticateMfa(any(MfaAuthenticateRequest.class), anyString()))
                .thenThrow(new InvalidTokenException("El desafio de autenticacion MFA ha expirado o es invalido."));

        mockMvc.perform(post("/api/v1/auth/mfa/authenticate")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"));
    }
}
