package com.apulia.authservice.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

/**
 * Orchestrazione dei flussi di identita': login, rotazione del refresh, logout.
 *
 * <p>Nessun dettaglio HTTP (il controller non entra qui) e nessun dettaglio JPA
 * oltre a {@link UserRepository}: lo stato dei token passa dal port
 * {@link TokenRevocationStore}, l'emissione da {@link TokenProvider}.</p>
 *
 * <p>Nessun metodo logga token o password: al massimo un identificativo utente
 * quando serve a spiegare una revoca di sicurezza.</p>
 */
@Service
@Transactional
public class AuthService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final TokenProvider tokenProvider;
    private final TokenRevocationStore revocationStore;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository users,
                       TokenProvider tokenProvider,
                       TokenRevocationStore revocationStore,
                       PasswordEncoder passwordEncoder) {
        this.users = users;
        this.tokenProvider = tokenProvider;
        this.revocationStore = revocationStore;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Autentica utente e password e rilascia una coppia di token.
     *
     * <p>Il refresh viene registrato subito: senza riga su database non sarebbe
     * ne' ruotabile ne' revocabile.</p>
     */
    public TokenPair login(String username, String rawPassword) {
        User user = users.findByUsername(username).orElse(null);

        // Utente assente e password errata devono produrre lo stesso 401:
        // altrimenti chi prova password a caso scopre quali account esistono.
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        TokenPair pair = issuePair(user);
        revocationStore.registerRefreshToken(
                pair.refreshToken().jti(), user.getId(), pair.refreshToken().expiresAt());
        return pair;
    }

    /**
     * Ruota il refresh token: il vecchio viene revocato, il nuovo registrato.
     *
     * <p>Rotazione stretta (D-A7-3): non esiste tolleranza per un secondo uso del
     * token appena ruotato. Il riuso segue D-A7-4 e chiude tutti i refresh attivi
     * dell'utente.</p>
     *
     * <p>{@code noRollbackFor} e' indispensabile: la cascata di revoca deve essere
     * committata anche se subito dopo lanciamo {@link TokenReuseException} per
     * produrre il 401.</p>
     */
    @Transactional(noRollbackFor = TokenReuseException.class)
    public TokenPair refresh(String refreshToken) {
        TokenClaims claims = tokenProvider.parse(refreshToken, TokenType.REFRESH)
                .orElseThrow(InvalidTokenException::new);

        RefreshTokenState current = revocationStore.findRefreshToken(claims.jti())
                .orElseThrow(InvalidTokenException::new);

        if (current.revokedAt() != null) {
            revocationStore.revokeAllActiveRefreshTokens(current.userId(), Instant.now());
            LOG.warn("Refresh token gia' revocato: revocati tutti i refresh attivi dell'utente {}",
                    current.userId());
            throw new TokenReuseException();
        }

        User user = users.findById(current.userId())
                .orElseThrow(InvalidTokenException::new);

        TokenPair pair = issuePair(user);
        revocationStore.revokeRefreshToken(current.jti(), Instant.now(), pair.refreshToken().jti());
        revocationStore.registerRefreshToken(
                pair.refreshToken().jti(), user.getId(), pair.refreshToken().expiresAt());
        return pair;
    }

    /**
     * Chiude la sessione: blacklist dell'access token e, se presente, revoca del
     * refresh (D-A7-5).
     *
     * <p>Idempotente: ripetere la chiamata con gli stessi token non produce errori.
     * Un refresh non valido nel body viene ignorato di proposito, perche' il logout
     * deve riuscire anche quando il client ha gia' perso il refresh.</p>
     */
    public void logout(String accessToken, String refreshToken) {
        TokenClaims access = tokenProvider.parse(accessToken, TokenType.ACCESS)
                .orElseThrow(InvalidTokenException::new);

        revocationStore.revokeAccessToken(access.jti(), access.expiresAt());

        if (refreshToken != null && !refreshToken.isBlank()) {
            tokenProvider.parse(refreshToken, TokenType.REFRESH)
                    .flatMap(claims -> revocationStore.findRefreshToken(claims.jti()))
                    .filter(state -> state.revokedAt() == null)
                    .ifPresent(state -> revocationStore.revokeRefreshToken(state.jti(), Instant.now(), null));
        }
    }

    /** Emette la coppia; i ruoli viaggiano solo nell'access token. */
    private TokenPair issuePair(User user) {
        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .sorted()
                .toList();

        IssuedToken access = tokenProvider.issueAccessToken(user.getUsername(), user.getId(), roles);
        IssuedToken refresh = tokenProvider.issueRefreshToken(user.getUsername());
        return new TokenPair(access, refresh);
    }
}