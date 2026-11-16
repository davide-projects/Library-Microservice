package com.apulia.authservice.service.exception;

/**
 * Presentato un refresh token gia' revocato: possibile furto (D-A7-4).
 *
 * <p>Prima di lanciarla il service revoca tutti i refresh attivi dell'utente:
 * l'eccezione serve solo a produrre il 401, non a innescare la cascata.</p>
 */
public class TokenReuseException extends RuntimeException {

    public TokenReuseException() {
        super("Riuso di un refresh token revocato");
    }
}