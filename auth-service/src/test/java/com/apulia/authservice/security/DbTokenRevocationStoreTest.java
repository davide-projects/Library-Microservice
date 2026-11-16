package com.apulia.authservice.security;

import com.apulia.authservice.model.RefreshToken;
import com.apulia.authservice.model.User;
import com.apulia.authservice.repository.RefreshTokenRepository;
import com.apulia.authservice.repository.RevokedTokenRepository;
import com.apulia.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test dell'adapter JPA del port di revoca (A10).
 *
 * <p>Slice JPA su H2: verifica che i jti finiscano davvero nelle tabelle
 * {@code refresh_tokens} e {@code revoked_tokens} e che la cascata di revoca
 * tocchi solo i token giusti.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
class DbTokenRevocationStoreTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant EXPIRES_AT = NOW.plus(Duration.ofDays(7));

    @Autowired
    private TestEntityManager em;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private RevokedTokenRepository revokedTokens;

    @Autowired
    private UserRepository users;

    private DbTokenRevocationStore store;
    private Integer adminId;
    private Integer otherId;

    @BeforeEach
    void setUp() {
        store = new DbTokenRevocationStore(refreshTokens, revokedTokens, users);
        adminId = persistUser("admin");
        otherId = persistUser("other");
    }

    private Integer persistUser(String username) {
        return em.persistAndFlush(new User(username, "hashed-password")).getId();
    }

    @Test
    void registerRefreshTokenPersistsOwnerAndExpiry() {
        store.registerRefreshToken("refresh-1", adminId, EXPIRES_AT);
        em.flush();
        em.clear();

        RefreshToken saved = refreshTokens.findById("refresh-1").orElseThrow();

        assertThat(saved.getUser().getId()).isEqualTo(adminId);
        assertThat(saved.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(saved.getRevokedAt()).isNull();
        assertThat(saved.getReplacedByJti()).isNull();
    }

    @Test
    void findRefreshTokenReturnsEmptyForAnUnknownJti() {
        assertThat(store.findRefreshToken("missing")).isEmpty();
    }

    @Test
    void findRefreshTokenMapsAnActiveState() {
        store.registerRefreshToken("refresh-1", adminId, EXPIRES_AT);
        em.flush();
        em.clear();

        RefreshTokenState state = store.findRefreshToken("refresh-1").orElseThrow();

        assertThat(state.jti()).isEqualTo("refresh-1");
        assertThat(state.userId()).isEqualTo(adminId);
        assertThat(state.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(state.revokedAt()).isNull();
        assertThat(state.replacedByJti()).isNull();
    }

    @Test
    void revokeRefreshTokenStoresTimestampAndReplacement() {
        store.registerRefreshToken("refresh-1", adminId, EXPIRES_AT);
        em.flush();
        em.clear();

        store.revokeRefreshToken("refresh-1", NOW, "refresh-2");
        em.flush();
        em.clear();

        RefreshTokenState state = store.findRefreshToken("refresh-1").orElseThrow();

        assertThat(state.revokedAt()).isEqualTo(NOW);
        assertThat(state.replacedByJti()).isEqualTo("refresh-2");
    }

    @Test
    void revokeAllActiveRefreshTokensTouchesOnlyActiveTokensOfThatUser() {
        store.registerRefreshToken("admin-active", adminId, EXPIRES_AT);
        store.registerRefreshToken("admin-revoked", adminId, EXPIRES_AT);
        store.registerRefreshToken("other-active", otherId, EXPIRES_AT);
        em.flush();
        store.revokeRefreshToken("admin-revoked", NOW, "admin-replacement");
        em.flush();

        Instant cascadeAt = NOW.plusSeconds(60);
        store.revokeAllActiveRefreshTokens(adminId, cascadeAt);
        em.flush();
        em.clear();

        assertThat(store.findRefreshToken("admin-active").orElseThrow().revokedAt()).isEqualTo(cascadeAt);
        assertThat(store.findRefreshToken("admin-revoked").orElseThrow().revokedAt()).isEqualTo(NOW);
        assertThat(store.findRefreshToken("other-active").orElseThrow().revokedAt()).isNull();
    }

    @Test
    void revokeAccessTokenInsertsABlacklistEntry() {
        store.revokeAccessToken("access-1", EXPIRES_AT);
        em.flush();
        em.clear();

        assertThat(revokedTokens.findById("access-1")).isPresent();
        assertThat(store.isAccessTokenRevoked("access-1")).isTrue();
    }

    @Test
    void revokeAccessTokenIsIdempotent() {
        store.revokeAccessToken("access-1", EXPIRES_AT);
        store.revokeAccessToken("access-1", EXPIRES_AT);
        em.flush();

        assertThat(revokedTokens.count()).isEqualTo(1);
    }

    @Test
    void unknownAccessTokenIsNotRevoked() {
        assertThat(store.isAccessTokenRevoked("missing")).isFalse();
    }
}