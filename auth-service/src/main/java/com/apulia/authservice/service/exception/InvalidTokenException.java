package com.apulia.authservice.service.exception;

/**
 * Token assente, malformato, scaduto, contraffatto, di tipo sbagliato o mai registrato.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException() {
        super("Token non valido o scaduto");
    }
}