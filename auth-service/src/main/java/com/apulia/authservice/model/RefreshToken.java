package com.apulia.authservice.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

@Entity(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @Column(nullable = false, name = "jti")
    private String jti;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_jti")
    private String replacedByJti;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public RefreshToken() {}

    public RefreshToken(String jti, User user, Instant expiresAt) {
        this.jti = jti;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    public String getJti() {
        return jti;
    }

    public void setJti(String jti) {
        this.jti = jti;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getReplacedByJti() {
        return replacedByJti;
    }

    public void setReplacedByJti(String replacedByJti) {
        this.replacedByJti = replacedByJti;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RefreshToken that = (RefreshToken) o;
        return Objects.equals(jti, that.jti);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jti);
    }

    @Override
    public String toString() {
        return "RefreshToken{" +
                "jti='" + jti + '\'' +
                ", expiresAt=" + expiresAt +
                ", revokedAt=" + revokedAt +
                ", replacedByJti='" + replacedByJti + '\'' +
                '}';
    }
}
