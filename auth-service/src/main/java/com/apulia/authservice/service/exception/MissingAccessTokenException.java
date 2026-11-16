package com.apulia.authservice.service.exception;

/**
 * Richiesta di logout senza header {@code Authorization: Bearer ...}.
 */
public class MissingAccessTokenException extends RuntimeException {

    public MissingAccessTokenException() {
        super("Access token mancante");
    }
}