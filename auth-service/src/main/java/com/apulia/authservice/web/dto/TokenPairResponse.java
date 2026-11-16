package com.apulia.authservice.web.dto;

import java.time.Duration;

import com.apulia.authservice.security.IssuedToken;
import com.apulia.authservice.security.TokenPair;

/**
 * Risposta di login e refresh.
 *
 * @param accessToken  token breve da inviare come {@code Authorization: Bearer}
 * @param refreshToken token lungo, destinato solo a {@code /auth/refresh}
 * @param tokenType    sempre {@code Bearer}
 * @param expiresIn    durata dell'access token in secondi
 */
public record TokenPairResponse(String accessToken,
                                String refreshToken,
                                String tokenType,
                                long expiresIn) {

    private static final String BEARER = "Bearer";

    /** Traduce la coppia di dominio nel contratto HTTP. */
    public static TokenPairResponse from(TokenPair pair) {
        IssuedToken access = pair.accessToken();
        return new TokenPairResponse(
                access.value(),
                pair.refreshToken().value(),
                BEARER,
                Duration.between(access.issuedAt(), access.expiresAt()).toSeconds());
    }
}