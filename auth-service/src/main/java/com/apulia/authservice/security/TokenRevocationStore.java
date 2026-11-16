package com.apulia.authservice.security;

import java.time.Instant;
import java.util.Optional;

/**
 * Port di persistenza per lo stato dei token: blacklist degli access e ciclo
 * di vita dei refresh.
 *
 * <p>Nessun tipo JPA attraversa questo confine: solo {@code String}, {@code Integer}
 * e {@code Instant}. L'implementazione attuale e' su database; in Fase E potra'
 * diventare Redis (D2) senza toccare il service che lo usa.</p>
 */
public interface TokenRevocationStore {

    /**
     * Registra un refresh token appena emesso: senza registrazione il token
     * non e' ne' revocabile ne' ruotabile.
     */
    void registerRefreshToken(String jti, Integer userId, Instant expiresAt);

    /**
     * Legge lo stato di un refresh token.
     *
     * @return vuoto se il jti non e' mai stato registrato
     */
    Optional<RefreshTokenState> findRefreshToken(String jti);

    /**
     * Revoca un refresh token gia' registrato.
     *
     * @param replacedByJti jti del token che lo sostituisce (rotazione),
     *                      null quando la revoca arriva dal logout
     */
    void revokeRefreshToken(String jti, Instant revokedAt, String replacedByJti);

    /**
     * Revoca in blocco tutti i refresh ancora attivi di un utente.
     *
     * <p>E' la risposta al riuso di un refresh revocato (D-A7-4): la sessione
     * compromessa muore interamente, non solo il token presentato.</p>
     */
    void revokeAllActiveRefreshTokens(Integer userId, Instant revokedAt);

    /**
     * Inserisce un access token nella blacklist fino alla sua scadenza naturale.
     *
     * <p>Idempotente: un doppio logout non deve violare la chiave primaria.</p>
     */
    void revokeAccessToken(String jti, Instant expiresAt);

    /**
     * true se l'access token e' in blacklist.
     *
     * <p>In A7 nessuno lo invoca: sara' il resource server del gateway (B2) a
     * usarlo su ogni richiesta. Resta nel port perche' ne fa parte.</p>
     */
    boolean isAccessTokenRevoked(String jti);
}