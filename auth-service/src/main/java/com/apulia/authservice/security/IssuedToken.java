package com.apulia.authservice.security;

import java.time.Instant;

/**
 * Token appena emesso.
 *
 * <p>Oltre alla stringa da restituire al client porta con sé {@code jti} e la
 * coppia emissione/scadenza: servono al chiamante per persistere il token nelle
 * tabelle {@code refresh_tokens} / {@code revoked_tokens} e per calcolare
 * {@code expiresIn} senza ri-parsare una stringa appena generata.</p>
 *
 * @param value     token compatto, l'unica parte che va al client
 * @param jti       identificativo univoco, chiave di revoca e rotazione
 * @param issuedAt  istante di emissione (UTC)
 * @param expiresAt istante di scadenza (UTC)
 */
public record IssuedToken(String value, String jti, Instant issuedAt, Instant expiresAt) {
}