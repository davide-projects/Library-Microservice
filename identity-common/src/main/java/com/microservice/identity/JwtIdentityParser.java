package com.microservice.identity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Valida un access token emesso da auth-service e ne estrae l'{@link Identity}.
 *
 * <p>Stesse regole del servizio di identita': firma HMAC, issuer atteso, tipo
 * {@code ACCESS}. In piu' richiede il claim numerico {@code uid}; un token privo di
 * id non e' utilizzabile downstream e viene scartato.</p>
 *
 * <p>Nessun token viene loggato: un errore di validazione e' un semplice
 * {@link Optional#empty()} per il chiamante.</p>
 */
public final class JwtIdentityParser {

    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String TYPE_CLAIM = "typ";
    private static final String ROLES_CLAIM = "roles";
    private static final String USER_ID_CLAIM = "uid";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final String issuer;

    public JwtIdentityParser(String secret, String issuer) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("security.jwt.secret mancante o piu' corto di 32 byte");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("security.jwt.issuer mancante");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
    }

    /**
     * @return l'identita' contenuta nel token, oppure vuoto se il token e' assente,
     *         malformato, scaduto, firmato con un'altra chiave, di un altro issuer,
     *         di tipo diverso da {@code ACCESS} o privo di {@code uid}.
     */
    public Optional<Identity> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .require(TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String username = claims.getSubject();
            Number userId = claims.get(USER_ID_CLAIM, Number.class);
            if (username == null || username.isBlank() || userId == null) {
                return Optional.empty();
            }

            return Optional.of(new Identity(userId.longValue(), username, roles(claims)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static List<String> roles(Claims claims) {
        @SuppressWarnings("unchecked")
        List<Object> raw = claims.get(ROLES_CLAIM, List.class);
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }
}