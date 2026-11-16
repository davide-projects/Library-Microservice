package com.apulia.authservice.service.exception;

/**
 * Credenziali di login non valide.
 *
 * <p>Copre sia l'utente inesistente sia la password errata: il chiamante non deve
 * poter distinguere i due casi (nessuna enumerazione degli account).</p>
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Credenziali non valide");
    }
}