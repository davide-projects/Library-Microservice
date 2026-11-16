package com.apulia.authservice.web.dto;

/**
 * Formato unico degli errori HTTP (D-A7-7).
 *
 * <p>{@code code} e' stabile e pensato per il client e per i test; {@code message}
 * e' la descrizione leggibile. Nessun dettaglio interno, nessuno stack trace.</p>
 */
public record ApiError(String code, String message) {
}