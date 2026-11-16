package com.apulia.authservice.security;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.apulia.authservice.model.RefreshToken;
import com.apulia.authservice.model.RevokedToken;
import com.apulia.authservice.model.User;
import com.apulia.authservice.repository.RefreshTokenRepository;
import com.apulia.authservice.repository.RevokedTokenRepository;
import com.apulia.authservice.repository.UserRepository;

/**
 * Adapter su database del port {@link TokenRevocationStore} (D-A7-1).
 *
 * <p>E' l'unico punto in cui il ciclo di vita dei token tocca JPA: il service
 * vede solo {@link RefreshTokenState} e {@code Instant}. Quando in Fase E la
 * revoca passera' su Redis bastera' un secondo adapter, senza cambiare i
 * chiamanti.</p>
 *
 * <p>Tutti i metodi lavorano dentro la transazione aperta da {@code AuthService}
 * e sfruttano il dirty checking: nessun {@code save} superfluo sulle entity
 * gia' caricate.</p>
 */
@Component
public class DbTokenRevocationStore implements TokenRevocationStore {

    private final RefreshTokenRepository refreshTokens;
    private final RevokedTokenRepository revokedTokens;
    private final UserRepository users;

    public DbTokenRevocationStore(RefreshTokenRepository refreshTokens,
                                  RevokedTokenRepository revokedTokens,
                                  UserRepository users) {
        this.refreshTokens = refreshTokens;
        this.revokedTokens = revokedTokens;
        this.users = users;
    }

    @Override
    public void registerRefreshToken(String jti, Integer userId, Instant expiresAt) {
        User owner = users.getReferenceById(userId);
        refreshTokens.save(new RefreshToken(jti, owner, expiresAt));
    }

    @Override
    public Optional<RefreshTokenState> findRefreshToken(String jti) {
        return refreshTokens.findById(jti)
                .map(rt -> new RefreshTokenState(
                        rt.getJti(),
                        rt.getUser().getId(),
                        rt.getExpiresAt(),
                        rt.getRevokedAt(),
                        rt.getReplacedByJti()));
    }

    @Override
    public void revokeRefreshToken(String jti, Instant revokedAt, String replacedByJti) {
        refreshTokens.findById(jti).ifPresent(rt -> {
            rt.setRevokedAt(revokedAt);
            rt.setReplacedByJti(replacedByJti);
        });
    }

    @Override
    public void revokeAllActiveRefreshTokens(Integer userId, Instant revokedAt) {
        List<RefreshToken> active = refreshTokens.findByUser_IdAndRevokedAtIsNull(userId);
        active.forEach(rt -> rt.setRevokedAt(revokedAt));
    }

    @Override
    public void revokeAccessToken(String jti, Instant expiresAt) {
        if (revokedTokens.existsById(jti)) {
            return;
        }
        revokedTokens.save(new RevokedToken(jti, expiresAt));
    }

    @Override
    public boolean isAccessTokenRevoked(String jti) {
        return revokedTokens.existsById(jti);
    }
}