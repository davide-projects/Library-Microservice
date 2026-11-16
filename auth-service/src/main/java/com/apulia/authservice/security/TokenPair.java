package com.apulia.authservice.security;

import java.util.Objects;

/**
 * Coppia di token emessa insieme su login o su refresh.
 *
 * <p>Access e refresh hanno jti e durate distinti, ma nascono nello stesso istante:
 * tenerli in un unico valore impedisce di restituirne uno senza l'altro.</p>
 */
public record TokenPair(IssuedToken accessToken, IssuedToken refreshToken) {

    /** Una coppia incompleta non ha senso: fallisce subito, non a valle. */
    public TokenPair {
        Objects.requireNonNull(accessToken, "accessToken non puo essere null");
        Objects.requireNonNull(refreshToken, "refreshToken non puo essere null");
    }
}