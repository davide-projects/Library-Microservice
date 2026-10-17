package com.apulia.authservice.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity(name = "revoked_tokens")
public class RevokedToken {

    @Id
    @Column(nullable = false, name = "jti")
    private String jti;

    @Column(nullable = false, name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at", insertable = false, updatable = false)
    private LocalDateTime revokedAt;

    public RevokedToken() {}

    public RevokedToken(String jti, LocalDateTime expiresAt) {
        this.jti = jti;
        this.expiresAt = expiresAt;
    }

    public String getJti() {
        return jti;
    }

    public void setJti(String jti) {
        this.jti = jti;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RevokedToken that = (RevokedToken) o;
        return Objects.equals(jti, that.jti);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jti);
    }

    @Override
    public String toString() {
        return "RevokedToken{" +
                "jti='" + jti + '\'' +
                ", expiresAt=" + expiresAt +
                ", revokedAt=" + revokedAt +
                '}';
    }
}
