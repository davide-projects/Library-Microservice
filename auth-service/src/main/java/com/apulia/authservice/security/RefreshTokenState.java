package com.apulia.authservice.security;

import java.time.Instant;

/**
 * Fotografia di un refresh token persistito, senza alcun tipo JPA.
 *
 * <p>Distingue in un colpo solo i tre casi che il service deve trattare:
 * sconosciuto ({@code Optional.empty()} di {@link TokenRevocationStore}),
 * revocato ({@code revokedAt != null}) e attivo ({@code revokedAt == null}).</p>
 *
 * @param jti           identificativo del refresh token
 * @param userId        proprietario del token
 * @param expiresAt     scadenza registrata al momento dell'emissione
 * @param revokedAt     istante di revoca, null se ancora attivo
 * @param replacedByJti jti del token che lo ha sostituito, null se mai ruotato
 */
public record RefreshTokenState(String jti,
                                Integer userId,
                                Instant expiresAt,
                                Instant revokedAt,
                                String replacedByJti) {
}