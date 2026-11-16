package com.apulia.authservice.service;

import com.apulia.authservice.model.Role;
import com.apulia.authservice.model.User;
import com.apulia.authservice.repository.UserRepository;
import com.apulia.authservice.security.IssuedToken;
import com.apulia.authservice.security.RefreshTokenState;
import com.apulia.authservice.security.TokenClaims;
import com.apulia.authservice.security.TokenPair;
import com.apulia.authservice.security.TokenProvider;
import com.apulia.authservice.security.TokenRevocationStore;
import com.apulia.authservice.security.TokenType;
import com.apulia.authservice.service.exception.InvalidCredentialsException;
import com.apulia.authservice.service.exception.InvalidTokenException;
import com.apulia.authservice.service.exception.TokenReuseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Test delle regole di login, refresh e logout (A10).
 *
 * <p>Il service e' isolato: repository, provider e port di revoca sono mock,
 * l'entity {@link User} si costruisce in memoria. Nessun database, nessun HTTP.</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant ACCESS_EXPIRY = NOW.plus(Duration.ofMinutes(15));
    private static final Instant REFRESH_EXPIRY = NOW.plus(Duration.ofDays(7));

    private static final IssuedToken ACCESS = new IssuedToken("access-value", "access-jti", NOW, ACCESS_EXPIRY);
    private static final IssuedToken REFRESH = new IssuedToken("refresh-value", "refresh-jti", NOW, REFRESH_EXPIRY);
    private static final IssuedToken NEW_ACCESS = new IssuedToken("new-access-value", "new-access-jti", NOW, ACCESS_EXPIRY);
    private static final IssuedToken NEW_REFRESH = new IssuedToken("new-refresh-value", "new-refresh-jti", NOW, REFRESH_EXPIRY);

    @Mock
    private UserRepository users;

    @Mock
    private TokenProvider tokenProvider;

    @Mock
    private TokenRevocationStore revocationStore;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(users, tokenProvider, revocationStore, passwordEncoder);
    }

    private static User user(int id, String username, String... roles) {
        User user = new User(username, "hashed-password");
        user.setId(id);
        Set<Role> assigned = new HashSet<>();
        for (String name : roles) {
            Role role = new Role();
            role.setName(name);
            assigned.add(role);
        }
        user.getRoles().addAll(assigned);
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

    private static RefreshTokenState revokedRefresh(String jti) {
        return new RefreshTokenState(jti, 1, REFRESH_EXPIRY, NOW.minusSeconds(60), "new-refresh-jti");
    }

    @Test
    void loginIssuesPairAndRegistersTheRefreshToken() {
        User admin = user(1, "admin", "ROLE_USER", "ROLE_ADMIN");
        when(users.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Secret123!", "hashed-password")).thenReturn(true);
        when(tokenProvider.issueAccessToken("admin", 1L, List.of("ROLE_ADMIN", "ROLE_USER"))).thenReturn(ACCESS);
        when(tokenProvider.issueRefreshToken("admin")).thenReturn(REFRESH);

        TokenPair pair = authService.login("admin", "Secret123!");

        assertThat(pair.accessToken()).isSameAs(ACCESS);
        assertThat(pair.refreshToken()).isSameAs(REFRESH);
        verify(revocationStore).registerRefreshToken("refresh-jti", 1, REFRESH_EXPIRY);
    }

    @Test
    void loginFailsForUnknownUserWithoutIssuingTokens() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("ghost", "whatever"))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(tokenProvider, revocationStore);
    }

    @Test
    void loginFailsForWrongPasswordWithoutIssuingTokens() {
        User admin = user(1, "admin", "ROLE_ADMIN");
        when(users.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("admin", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(tokenProvider, revocationStore);
    }

    @Test
    void unknownUserAndWrongPasswordProduceTheSameError() {
        User admin = user(1, "admin", "ROLE_ADMIN");
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());
        when(users.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong", "hashed-password")).thenReturn(false);

        Throwable unknown = catchThrowable(() -> authService.login("ghost", "whatever"));
        Throwable wrong = catchThrowable(() -> authService.login("admin", "wrong"));

        assertThat(unknown).isInstanceOf(InvalidCredentialsException.class);
        assertThat(wrong).isInstanceOf(InvalidCredentialsException.class);
        assertThat(unknown.getMessage()).isEqualTo(wrong.getMessage());
    }

    @Test
    void refreshRotatesTheTokenAndRegistersTheNewOne() {
        User admin = user(1, "admin", "ROLE_ADMIN");
        when(tokenProvider.parse("refresh-value", TokenType.REFRESH))
                .thenReturn(Optional.of(refreshClaims("refresh-jti")));
        when(revocationStore.findRefreshToken("refresh-jti")).thenReturn(Optional.of(activeRefresh("refresh-jti")));
        when(users.findById(1)).thenReturn(Optional.of(admin));
        when(tokenProvider.issueAccessToken("admin", 1L, List.of("ROLE_ADMIN"))).thenReturn(NEW_ACCESS);
        when(tokenProvider.issueRefreshToken("admin")).thenReturn(NEW_REFRESH);

        TokenPair pair = authService.refresh("refresh-value");

        assertThat(pair.accessToken()).isSameAs(NEW_ACCESS);
        assertThat(pair.refreshToken()).isSameAs(NEW_REFRESH);
        verify(revocationStore).revokeRefreshToken(eq("refresh-jti"), any(Instant.class), eq("new-refresh-jti"));
        verify(revocationStore).registerRefreshToken("new-refresh-jti", 1, REFRESH_EXPIRY);
    }

    @Test
    void refreshFailsForATokenThatCannotBeParsed() {
        when(tokenProvider.parse("bad", TokenType.REFRESH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("bad"))
                .isInstanceOf(InvalidTokenException.class);

        verifyNoInteractions(revocationStore, users);
    }

    @Test
    void refreshFailsForAnUnknownJti() {
        when(tokenProvider.parse("refresh-value", TokenType.REFRESH))
                .thenReturn(Optional.of(refreshClaims("refresh-jti")));
        when(revocationStore.findRefreshToken("refresh-jti")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("refresh-value"))
                .isInstanceOf(InvalidTokenException.class);

        verifyNoInteractions(users);
    }

    @Test
    void reuseOfARevokedRefreshTokenClosesEveryActiveSession() {
        when(tokenProvider.parse("refresh-value", TokenType.REFRESH))
                .thenReturn(Optional.of(refreshClaims("refresh-jti")));
        when(revocationStore.findRefreshToken("refresh-jti")).thenReturn(Optional.of(revokedRefresh("refresh-jti")));

        assertThatThrownBy(() -> authService.refresh("refresh-value"))
                .isInstanceOf(TokenReuseException.class);

        verify(revocationStore).revokeAllActiveRefreshTokens(eq(1), any(Instant.class));
        verify(tokenProvider, never()).issueAccessToken(anyString(), anyLong(), any());
        verify(tokenProvider, never()).issueRefreshToken(anyString());
        verify(revocationStore, never()).revokeRefreshToken(anyString(), any(), any());
        verifyNoInteractions(users);
    }

    @Test
    void refreshFailsWhenTheOwnerNoLongerExists() {
        when(tokenProvider.parse("refresh-value", TokenType.REFRESH))
                .thenReturn(Optional.of(refreshClaims("refresh-jti")));
        when(revocationStore.findRefreshToken("refresh-jti")).thenReturn(Optional.of(activeRefresh("refresh-jti")));
        when(users.findById(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("refresh-value"))
                .isInstanceOf(InvalidTokenException.class);

        verify(tokenProvider, never()).issueAccessToken(anyString(), anyLong(), any());
    }

    @Test
    void logoutBlacklistsTheAccessTokenAndRevokesTheRefreshToken() {
        when(tokenProvider.parse("access-value", TokenType.ACCESS)).thenReturn(Optional.of(accessClaims()));
        when(tokenProvider.parse("refresh-value", TokenType.REFRESH))
                .thenReturn(Optional.of(refreshClaims("refresh-jti")));
        when(revocationStore.findRefreshToken("refresh-jti")).thenReturn(Optional.of(activeRefresh("refresh-jti")));

        authService.logout("access-value", "refresh-value");

        verify(revocationStore).revokeAccessToken("access-jti", ACCESS_EXPIRY);
        verify(revocationStore).revokeRefreshToken(eq("refresh-jti"), any(Instant.class), isNull());
    }

    @Test
    void logoutWorksWithoutARefreshToken() {
        when(tokenProvider.parse("access-value", TokenType.ACCESS)).thenReturn(Optional.of(accessClaims()));

        authService.logout("access-value", null);
        authService.logout("access-value", "   ");

        verify(revocationStore, org.mockito.Mockito.times(2)).revokeAccessToken("access-jti", ACCESS_EXPIRY);
        verify(tokenProvider, never()).parse(anyString(), eq(TokenType.REFRESH));
        verify(revocationStore, never()).revokeRefreshToken(anyString(), any(), any());
    }

    @Test
    void logoutFailsWhenTheAccessTokenIsInvalid() {
        when(tokenProvider.parse("bad", TokenType.ACCESS)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.logout("bad", "refresh-value"))
                .isInstanceOf(InvalidTokenException.class);

        verifyNoInteractions(revocationStore);
    }

    @Test
    void logoutIgnoresAnInvalidRefreshToken() {
        when(tokenProvider.parse("access-value", TokenType.ACCESS)).thenReturn(Optional.of(accessClaims()));
        when(tokenProvider.parse("bad-refresh", TokenType.REFRESH)).thenReturn(Optional.empty());

        authService.logout("access-value", "bad-refresh");

        verify(revocationStore).revokeAccessToken("access-jti", ACCESS_EXPIRY);
        verify(revocationStore, never()).revokeRefreshToken(anyString(), any(), any());
    }

    @Test
    void logoutDoesNotRevokeAnAlreadyRevokedRefreshTokenAgain() {
        when(tokenProvider.parse("access-value", TokenType.ACCESS)).thenReturn(Optional.of(accessClaims()));
        when(tokenProvider.parse("refresh-value", TokenType.REFRESH))
                .thenReturn(Optional.of(refreshClaims("refresh-jti")));
        when(revocationStore.findRefreshToken("refresh-jti")).thenReturn(Optional.of(revokedRefresh("refresh-jti")));

        authService.logout("access-value", "refresh-value");

        verify(revocationStore).revokeAccessToken("access-jti", ACCESS_EXPIRY);
        verify(revocationStore, never()).revokeRefreshToken(anyString(), any(), any());
    }

    @Test
    void logoutIgnoresAnUnknownRefreshToken() {
        when(tokenProvider.parse("access-value", TokenType.ACCESS)).thenReturn(Optional.of(accessClaims()));
        when(tokenProvider.parse("refresh-value", TokenType.REFRESH))
                .thenReturn(Optional.of(refreshClaims("refresh-jti")));
        when(revocationStore.findRefreshToken("refresh-jti")).thenReturn(Optional.empty());

        authService.logout("access-value", "refresh-value");

        verify(revocationStore).revokeAccessToken("access-jti", ACCESS_EXPIRY);
        verify(revocationStore, never()).revokeRefreshToken(anyString(), any(), any());
    }
}