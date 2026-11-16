package com.microservice.identity;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test del parser condiviso introdotto in A8 (parte di A10).
 *
 * <p>Il parser e' la difesa dei servizi downstream: deve accettare solo access
 * token firmati da auth-service, con issuer e {@code uid} corretti.</p>
 */
class JwtIdentityParserTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String OTHER_SECRET = "abcdef0123456789abcdef0123456789";
    private static final String ISSUER = "auth-service";

    private static JwtIdentityParser parser() {
        return new JwtIdentityParser(SECRET, ISSUER);
    }

    private static String token(String secret,
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

    private static String validToken() {
        return token(SECRET, ISSUER, "admin", "ACCESS", Instant.now().plusSeconds(900), 7L,
                List.of("ROLE_ADMIN", "ROLE_USER"));
    }

    @Test
    void validAccessTokenProducesTheIdentity() {
        Identity identity = parser().parse(validToken()).orElseThrow();

        assertThat(identity.userId()).isEqualTo(7L);
        assertThat(identity.username()).isEqualTo("admin");
        assertThat(identity.roles()).containsExactly("ROLE_ADMIN", "ROLE_USER");
        assertThat(identity.hasRole("ROLE_ADMIN")).isTrue();
        assertThat(identity.hasRole("ROLE_USER")).isTrue();
        assertThat(identity.hasRole("ROLE_GUEST")).isFalse();
    }

    @Test
    void missingOrBlankTokenIsRejected() {
        assertThat(parser().parse(null)).isEmpty();
        assertThat(parser().parse("")).isEmpty();
        assertThat(parser().parse("   ")).isEmpty();
    }

    @Test
    void malformedTokenIsRejected() {
        assertThat(parser().parse("abc.def.ghi")).isEmpty();
        assertThat(parser().parse("not-a-jwt")).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        String expired = token(SECRET, ISSUER, "admin", "ACCESS", Instant.now().minusSeconds(60), 7L, List.of());

        assertThat(parser().parse(expired)).isEmpty();
    }

    @Test
    void wrongIssuerIsRejected() {
        String token = token(SECRET, "evil-service", "admin", "ACCESS", Instant.now().plusSeconds(60), 7L, List.of());

        assertThat(parser().parse(token)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = token(OTHER_SECRET, ISSUER, "admin", "ACCESS", Instant.now().plusSeconds(60), 7L, List.of());

        assertThat(parser().parse(token)).isEmpty();
    }

    @Test
    void refreshTokenIsRejected() {
        String token = token(SECRET, ISSUER, "admin", "REFRESH", Instant.now().plusSeconds(60), 7L, List.of());

        assertThat(parser().parse(token)).isEmpty();
    }

    @Test
    void tokenWithoutUserIdIsRejected() {
        String token = token(SECRET, ISSUER, "admin", "ACCESS", Instant.now().plusSeconds(60), null,
                List.of("ROLE_USER"));

        assertThat(parser().parse(token)).isEmpty();
    }

    @Test
    void tokenWithNonNumericUserIdIsRejected() {
        String token = token(SECRET, ISSUER, "admin", "ACCESS", Instant.now().plusSeconds(60), "seven",
                List.of("ROLE_USER"));

        assertThat(parser().parse(token)).isEmpty();
    }

    @Test
    void tokenWithoutSubjectIsRejected() {
        String token = token(SECRET, ISSUER, null, "ACCESS", Instant.now().plusSeconds(60), 7L,
                List.of("ROLE_USER"));

        assertThat(parser().parse(token)).isEmpty();
    }

    @Test
    void rolesOfUnexpectedTypeAreFilteredOut() {
        String token = token(SECRET, ISSUER, "admin", "ACCESS", Instant.now().plusSeconds(60), 7L,
                List.of("ROLE_USER", 42));

        Identity identity = parser().parse(token).orElseThrow();

        assertThat(identity.roles()).containsExactly("ROLE_USER");
    }

    @Test
    void missingRolesMeansNoRoles() {
        String token = token(SECRET, ISSUER, "admin", "ACCESS", Instant.now().plusSeconds(60), 7L, null);

        Identity identity = parser().parse(token).orElseThrow();

        assertThat(identity.roles()).isEmpty();
    }

    @Test
    void shorterThan32BytesSecretFailsAtConstruction() {
        assertThatThrownBy(() -> new JwtIdentityParser("too-short", ISSUER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtIdentityParser(null, ISSUER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankIssuerFailsAtConstruction() {
        assertThatThrownBy(() -> new JwtIdentityParser(SECRET, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtIdentityParser(SECRET, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}