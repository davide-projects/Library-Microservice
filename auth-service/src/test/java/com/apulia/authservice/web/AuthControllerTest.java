package com.apulia.authservice.web;

import com.apulia.authservice.model.Role;
import com.apulia.authservice.model.User;
import com.apulia.authservice.repository.UserRepository;
import com.apulia.authservice.security.IssuedToken;
import com.apulia.authservice.security.RefreshTokenState;
import com.apulia.authservice.security.TokenClaims;
import com.apulia.authservice.security.TokenProvider;
import com.apulia.authservice.security.TokenRevocationStore;
import com.apulia.authservice.security.TokenType;
import com.apulia.authservice.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test del contratto HTTP di {@link AuthController} (A10).
 *
 * <p>MockMvc standalone con {@link GlobalExceptionHandler} e {@link AuthService}
 * reale: le collaborazioni sono mock di interfacce, quindi il test resta veloce
 * e non dipende dalla possibilita' di instrumentare classi concrete.</p>
 */
class AuthControllerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant ACCESS_EXPIRY = NOW.plus(Duration.ofMinutes(15));
    private static final Instant REFRESH_EXPIRY = NOW.plus(Duration.ofDays(7));

    private static final IssuedToken ACCESS = new IssuedToken("access-value", "access-jti", NOW, ACCESS_EXPIRY);
    private static final IssuedToken REFRESH = new IssuedToken("refresh-value", "refresh-jti", NOW, REFRESH_EXPIRY);
    private static final IssuedToken NEW_REFRESH = new IssuedToken("new-refresh-value", "new-refresh-jti", NOW, REFRESH_EXPIRY);

    private UserRepository users;
    private TokenProvider tokenProvider;
    private TokenRevocationStore revocationStore;
    private PasswordEncoder passwordEncoder;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        tokenProvider = mock(TokenProvider.class);
        revocationStore = mock(TokenRevocationStore.class);
        passwordEncoder = mock(PasswordEncoder.class);

        AuthService authService = new AuthService(users, tokenProvider, revocationStore, passwordEncoder);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static User admin() {
        User user = new User("admin", "hashed-password");
        user.setId(1);
        Role role = new Role();
        role.setName("ROLE_ADMIN");
        user.getRoles().add(role);
        return user;
    }

    private static TokenClaims accessClaims() {
        return new TokenClaims("admin", List.of("ROLE_ADMIN"), "access-jti",
                TokenType.ACCESS, NOW, ACCESS_EXPIRY);
    }

    private static TokenClaims refreshClaims(String jti) {
        return new TokenClaims("admin", List.of(), jti, TokenType.REFRESH, NOW, REFRESH_EXPIRY);
    }

    private static RefreshTokenState activeRefresh(String jti) {
        return new RefreshTokenState(jti, 1, REFRESH_EXPIRY, null, null);
    }

    private void stubSuccessfulLogin() {
        when(users.findByUsername("admin")).thenReturn(Optional.of(admin()));
        when(passwordEncoder.matches("Admin123!", "hashed-password")).thenReturn(true);
        when(tokenProvider.issueAccessToken("admin", 1L, List.of("ROLE_ADMIN"))).thenReturn(ACCESS);
        when(tokenProvider.issueRefreshToken("admin")).thenReturn(REFRESH);
    }

    @Test
    void loginReturnsTheTokenPairWithoutCaching() throws Exception {
        stubSuccessfulLogin();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(jsonPath("$.accessToken").value("access-value"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-value"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));

        verify(revocationStore).registerRefreshToken("refresh-jti", 1, REFRESH_EXPIRY);
    }

    @Test
    void loginWithWrongCredentialsReturns401WithStableCode() throws Exception {
        when(users.findByUsername("admin")).thenReturn(Optional.of(admin()));
        when(passwordEncoder.matches("wrong", "hashed-password")).thenReturn(false);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Credenziali non valide"));

        verifyNoInteractions(tokenProvider, revocationStore);
    }

    @Test
    void loginWithUnknownUserReturnsTheSame401() throws Exception {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ghost\",\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        verifyNoInteractions(tokenProvider, revocationStore);
    }

    @Test
    void loginWithBlankFieldsReturns400AndDoesNotTouchTheDatabase() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(users);
    }

    @Test
    void loginWithMalformedBodyReturns400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(users);
    }

    @Test
    void refreshReturnsANewPairAndRotatesTheToken() throws Exception {
        when(tokenProvider.parse("refresh-value", TokenType.REFRESH))
                .thenReturn(Optional.of(refreshClaims("refresh-jti")));
        when(revocationStore.findRefreshToken("refresh-jti")).thenReturn(Optional.of(activeRefresh("refresh-jti")));
        when(users.findById(1)).thenReturn(Optional.of(admin()));
        when(tokenProvider.issueAccessToken("admin", 1L, List.of("ROLE_ADMIN"))).thenReturn(ACCESS);
        when(tokenProvider.issueRefreshToken("admin")).thenReturn(NEW_REFRESH);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-value\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-value"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-value"));

        verify(revocationStore).revokeRefreshToken(eq("refresh-jti"), any(Instant.class), eq("new-refresh-jti"));
        verify(revocationStore).registerRefreshToken("new-refresh-jti", 1, REFRESH_EXPIRY);
    }

    @Test
    void refreshWithAnInvalidTokenReturns401() throws Exception {
        when(tokenProvider.parse("bad", TokenType.REFRESH)).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"bad\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void logoutWithoutAuthorizationHeaderReturns401() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MISSING_ACCESS_TOKEN"));

        verifyNoInteractions(tokenProvider, revocationStore);
    }

    @Test
    void logoutAcceptsABearerSchemeCaseInsensitivelyAndWithoutBody() throws Exception {
        when(tokenProvider.parse("access-value", TokenType.ACCESS)).thenReturn(Optional.of(accessClaims()));

        mockMvc.perform(post("/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "bearer access-value"))
                .andExpect(status().isNoContent());

        verify(revocationStore).revokeAccessToken("access-jti", ACCESS_EXPIRY);
        verify(tokenProvider, never()).parse(anyString(), eq(TokenType.REFRESH));
    }

    @Test
    void logoutRevokesTheRefreshTokenFoundInTheBody() throws Exception {
        when(tokenProvider.parse("access-value", TokenType.ACCESS)).thenReturn(Optional.of(accessClaims()));
        when(tokenProvider.parse("refresh-value", TokenType.REFRESH))
                .thenReturn(Optional.of(refreshClaims("refresh-jti")));
        when(revocationStore.findRefreshToken("refresh-jti")).thenReturn(Optional.of(activeRefresh("refresh-jti")));

        mockMvc.perform(post("/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-value\"}"))
                .andExpect(status().isNoContent());

        verify(revocationStore).revokeAccessToken("access-jti", ACCESS_EXPIRY);
        verify(revocationStore).revokeRefreshToken(eq("refresh-jti"), any(Instant.class), isNull());
    }

    @Test
    void logoutWithAnInvalidAccessTokenReturns401() throws Exception {
        when(tokenProvider.parse("bad", TokenType.ACCESS)).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer bad"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        verifyNoInteractions(revocationStore);
    }
}