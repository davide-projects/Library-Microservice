package com.apulia.authservice.security;

import java.time.Instant;

/**
 * Token appena emesso.
 *
 * <p>Oltre alla stringa da restituire al client porta con sé {@code jti} e scadenza:
 * servono al chiamante per persistere il token nelle tabelle {@code refresh_tokens} /
 * {@code revoked_tokens} senza dover ri-parsare una stringa appena generata.</p>
 */
public record IssuedToken(String value, String jti, Instant expiresAt) {
}
