package com.apulia.authservice.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Implementazione JWT dei token di auth-service.
 *
 * <p>Chiave HMAC e parametri vengono letti <strong>una sola volta</strong> nel costruttore:
 * se lo secret fosse assente o più corto di 32 byte l'app non parte, invece di fallire
 * a ogni singola richiesta.</p>
 */
@Component
public class JwtTokenProvider implements TokenProvider {

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtTokenProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.issuer();
        this.accessTokenTtl = properties.accessTokenTtl();
        this.refreshTokenTtl = properties.refreshTokenTtl();
    }

    @Override
    public IssuedToken issueAccessToken(String username, Collection<String> roles) {
        return issue(username, List.copyOf(roles), TokenType.ACCESS, accessTokenTtl);
    }

    @Override
    public IssuedToken issueRefreshToken(String username) {
        // Il refresh token non porta ruoli: claim minimi, come da best practice.
        return issue(username, List.of(), TokenType.REFRESH, refreshTokenTtl);
    }

    @Override
    public Optional<TokenClaims> parse(String token, TokenType expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (claims.getIssuedAt() == null || claims.getExpiration() == null) {
                return Optional.empty();
            }

            // Un refresh token firmato da noi ma senza typ non è utilizzabile: lo scartiamo.
            String type = claims.get("typ", String.class);
            if (type == null || !expectedType.name().equals(type)) {
                return Optional.empty();
            }

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);

            return Optional.of(new TokenClaims(
                    claims.getSubject(),
                    roles == null ? List.of() : roles,
                    claims.getId(),
                    TokenType.valueOf(type),
                    claims.getIssuedAt().toInstant(),
                    claims.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            // Token malformato, scaduto o firma non valida: la richiesta va rifiutata,
            // non è un errore applicativo.
            return Optional.empty();
        }
    }

    private IssuedToken issue(String username, List<String> roles, TokenType type, Duration ttl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        String jti = UUID.randomUUID().toString();

        // jjwt 0.12 espone un API fluente: ogni claim ha il suo metodo.
        String value = Jwts.builder()
                .subject(username)
                .id(jti)                    // claim "jti": è la chiave della revoca
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("typ", type.name())
                .claim("roles", roles)
                .signWith(key)
                .compact();

        return new IssuedToken(value, jti, expiresAt);
    }
}
