package com.apulia.authservice.security;

import java.util.Collection;
import java.util.Optional;

/**
 * Emissione e lettura dei token di auth-service.
 *
 * <p>Resta un'interfaccia perché è il confine della fase di identity: i test di A10
 * possono sostituirla con un fake, e in futuro l'algoritmo o il backend di revoca
 * possono cambiare senza toccare chi la usa.</p>
 */
public interface TokenProvider {

    /**
     * Emette un access token.
     *
     * <p>I ruoli arrivano già pronti e non l'entità {@code User}: questo layer non deve
     * conoscere JPA, così resta testabile senza database.</p>
     */
    IssuedToken issueAccessToken(String username, Collection<String> roles);

    /**
     * Emette un refresh token. Non porta ruoli: serve solo a farsi rilasciare
     * un nuovo access token.
     */
    IssuedToken issueRefreshToken(String username);

    /**
     * Verifica firma, scadenza, issuer e tipo, restituendo i claim.
     *
     * @param expectedType tipo atteso: un refresh token non è valido come access token
     * @return vuoto se il token è malformato, scaduto, contraffatto o di tipo diverso
     */
    Optional<TokenClaims> parse(String token, TokenType expectedType);
}
