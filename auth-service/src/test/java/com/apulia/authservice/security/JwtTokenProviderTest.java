package com.apulia.authservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test di emissione e validazione dei token (A10).
 *
 * <p>Nessun contesto Spring: il provider e' un oggetto puro e le regole da
 * verificare sono claim, firma, issuer, scadenza e tipo.</p>
 */
class JwtTokenProviderTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String OTHER_SECRET = "abcdef0123456789abcdef0123456789";
    private static final String ISSUER = "auth-service";

    private static JwtTokenProvider provider() {
        return new JwtTokenProvider(new JwtProperties(
                ISSUER, SECRET, Duration.ofMinutes(15), Duration.ofDays(7)));
    }

    private static JwtTokenProvider providerWithAccessTtl(Duration ttl) {
        return new JwtTokenProvider(new JwtProperties(
                ISSUER, SECRET, ttl, Duration.ofDays(7)));
    }

    private static Claims rawClaims(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @SuppressWarnings("unchecked")
    private static List<String> rolesOf(Claims claims) {
        return (List<String>) claims.get("roles", List.class);
    }

    private static String craftedToken(String secret,
                                       String issuer,
                                       String subject,
                                       String type,
                                       Instant expiresAt,
                                       Object uid,
                                       List<?> roles) {
        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt));
        if (subject != null) {
            builder.subject(subject);
        }
        if (type != null) {
            builder.claim("typ", type);
        }
        if (roles != null) {
            builder.claim("roles", roles);
        }
        if (uid != null) {
            builder.claim("uid", uid);
        }
        return builder.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private static String craftedToken(String secret, String issuer, String subject, String type, Instant expiresAt) {
        return craftedToken(secret, issuer, subject, type, expiresAt, 1L, List.of("ROLE_USER"));
    }

    @Test
    void accessTokenContainsExpectedClaims() {
        JwtTokenProvider provider = provider();
        IssuedToken issued = provider.issueAccessToken("admin", 7L, List.of("ROLE_ADMIN", "ROLE_USER"));

        Claims raw = rawClaims(issued.value());

        assertThat(raw.getSubject()).isEqualTo("admin");
        assertThat(raw.getId()).isEqualTo(issued.jti());
        assertThat(raw.getIssuer()).isEqualTo(ISSUER);
        assertThat(raw.get("typ", String.class)).isEqualTo("ACCESS");
        assertThat(((Number) raw.get("uid")).longValue()).isEqualTo(7L);
        assertThat(rolesOf(raw)).containsExactly("ROLE_ADMIN", "ROLE_USER");
        assertThat(raw.getIssuedAt()).isNotNull();
        assertThat(raw.getExpiration()).isAfter(raw.getIssuedAt());
    }

    @Test
    void parseReturnsTheClaimsOfAValidAccessToken() {
        JwtTokenProvider provider = provider();
        IssuedToken issued = provider.issueAccessToken("admin", 7L, List.of("ROLE_ADMIN"));

        TokenClaims claims = provider.parse(issued.value(), TokenType.ACCESS).orElseThrow();

        assertThat(claims.subject()).isEqualTo("admin");
        assertThat(claims.jti()).isEqualTo(issued.jti());
        assertThat(claims.type()).isEqualTo(TokenType.ACCESS);
        assertThat(claims.roles()).containsExactly("ROLE_ADMIN");
        assertThat(Duration.between(claims.issuedAt(), claims.expiresAt()).toSeconds()).isBetween(899L, 900L);
    }

    @Test
    void everyIssuedTokenHasItsOwnJti() {
        JwtTokenProvider provider = provider();

        IssuedToken first = provider.issueAccessToken("admin", 1L, List.of("ROLE_USER"));
        IssuedToken second = provider.issueAccessToken("admin", 1L, List.of("ROLE_USER"));

        assertThat(first.jti()).isNotBlank().isNotEqualTo(second.jti());
    }

    @Test
    void refreshTokenCarriesNoRolesAndNoUserId() {
        IssuedToken issued = provider().issueRefreshToken("admin");

        Claims raw = rawClaims(issued.value());

        assertThat(raw.getSubject()).isEqualTo("admin");
        assertThat(raw.get("typ", String.class)).isEqualTo("REFRESH");
        assertThat(rolesOf(raw)).isEmpty();
        assertThat(raw.get("uid")).isNull();
    }

    @Test
    void refreshTokenIsNotAcceptedAsAccessToken() {
        JwtTokenProvider provider = provider();
        IssuedToken refresh = provider.issueRefreshToken("admin");

        assertThat(provider.parse(refresh.value(), TokenType.ACCESS)).isEmpty();
    }

    @Test
    void accessTokenIsNotAcceptedAsRefreshToken() {
        JwtTokenProvider provider = provider();
        IssuedToken access = provider.issueAccessToken("admin", 1L, List.of("ROLE_USER"));

        assertThat(provider.parse(access.value(), TokenType.REFRESH)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtTokenProvider provider = providerWithAccessTtl(Duration.ofSeconds(-1));
        IssuedToken expired = provider.issueAccessToken("admin", 1L, List.of("ROLE_USER"));

        assertThat(provider.parse(expired.value(), TokenType.ACCESS)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String forged = craftedToken(OTHER_SECRET, ISSUER, "admin", "ACCESS", Instant.now().plusSeconds(60));

        assertThat(provider().parse(forged, TokenType.ACCESS)).isEmpty();
    }

    @Test
    void tokenWithWrongIssuerIsRejected() {
        String token = craftedToken(SECRET, "evil-service", "admin", "ACCESS", Instant.now().plusSeconds(60));

        assertThat(provider().parse(token, TokenType.ACCESS)).isEmpty();
    }

    @Test
    void tokenWithoutTypeIsRejected() {
        String token = craftedToken(SECRET, ISSUER, "admin", null, Instant.now().plusSeconds(60));

        assertThat(provider().parse(token, TokenType.ACCESS)).isEmpty();
    }

    @Test
    void tokenWithoutExpirationIsRejected() {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject("admin")
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .issuedAt(Date.from(now))
                .claim("typ", "ACCESS")
                .claim("roles", List.of())
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(provider().parse(token, TokenType.ACCESS)).isEmpty();
    }

    @Test
    void malformedTokensAreRejected() {
        JwtTokenProvider provider = provider();

        assertThat(provider.parse(null, TokenType.ACCESS)).isEmpty();
        assertThat(provider.parse("", TokenType.ACCESS)).isEmpty();
        assertThat(provider.parse("   ", TokenType.ACCESS)).isEmpty();
        assertThat(provider.parse("abc.def.ghi", TokenType.ACCESS)).isEmpty();
        assertThat(provider.parse("not-a-jwt", TokenType.ACCESS)).isEmpty();
    }

    @Test
    void secretShorterThan32BytesFailsAtConstruction() {
        assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties(
                ISSUER, "too-short", Duration.ofMinutes(15), Duration.ofDays(7))))
                .isInstanceOf(RuntimeException.class);
    }
}