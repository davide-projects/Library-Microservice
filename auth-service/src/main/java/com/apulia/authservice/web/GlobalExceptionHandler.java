package com.apulia.authservice.web;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.apulia.authservice.service.exception.InvalidCredentialsException;
import com.apulia.authservice.service.exception.InvalidTokenException;
import com.apulia.authservice.service.exception.MissingAccessTokenException;
import com.apulia.authservice.service.exception.TokenReuseException;
import com.apulia.authservice.web.dto.ApiError;

/**
 * Traduce le eccezioni in risposte {@code {code, message}} coerenti (D-A7-7).
 *
 * <p>Solo qui il dominio diventa HTTP: il service lancia eccezioni semantiche e
 * non conosce status code. Il 500 non espone mai lo stack, che finisce solo nel log.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> onInvalidCredentials(InvalidCredentialsException ex) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Credenziali non valide");
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiError> onInvalidToken(InvalidTokenException ex) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Token non valido o scaduto");
    }

    @ExceptionHandler(TokenReuseException.class)
    public ResponseEntity<ApiError> onTokenReuse(TokenReuseException ex) {
        return error(HttpStatus.UNAUTHORIZED, "TOKEN_REUSE_DETECTED",
                "Riuso di un refresh token revocato: sessione chiusa");
    }

    @ExceptionHandler(MissingAccessTokenException.class)
    public ResponseEntity<ApiError> onMissingAccessToken(MissingAccessTokenException ex) {
        return error(HttpStatus.UNAUTHORIZED, "MISSING_ACCESS_TOKEN",
                "Header Authorization con schema Bearer mancante");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                detail.isBlank() ? "Richiesta non valida" : detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> onUnreadableBody(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Corpo della richiesta mancante o malformato");
    }

    /**
     * Rete di sicurezza per tutto il resto.
     *
     * <p>Se l'eccezione e' gia' un {@link ErrorResponse} di Spring (405, 415, 404...)
     * ne conserviamo lo status: senza questo controllo il gestore generico
     * trasformerebbe ogni errore del framework in 500.</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            return error(status, "REQUEST_ERROR", "Richiesta non valida");
        }
        LOG.error("Errore inatteso durante una richiesta di autenticazione", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Errore interno del servizio");
    }

    private static ResponseEntity<ApiError> error(HttpStatusCode status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message));
    }
}