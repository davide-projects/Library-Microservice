package com.apulia.authservice.security;

import java.time.Instant;
import java.util.List;

/**
 * Contenuto di un token che ha superato la verifica di firma, scadenza e issuer.
 *
 * <p>È l'output del parse: chi la usa legge dati già validati e non tocca jjwt.</p>
 *
 * @param subject   username del proprietario (claim {@code sub})
 * @param roles     ruoli presenti nel token, vuoto per i refresh token
 * @param jti       identificativo univoco, è la chiave della revoca
 * @param type      tipo di token (claim {@code typ})
 * @param issuedAt  istante di emissione (claim {@code iat})
 * @param expiresAt istante di scadenza (claim {@code exp})
 */
public record TokenClaims(String subject,
                          List<String> roles,
                          String jti,
                          TokenType type,
                          Instant issuedAt,
                          Instant expiresAt) {
}
