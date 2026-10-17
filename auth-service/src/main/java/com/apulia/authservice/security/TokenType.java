package com.apulia.authservice.security;

/**
 * Tipo di token emesso da auth-service.
 *
 * <p>Il valore viene salvato nel claim {@code typ} e ricontrollato a ogni lettura:
 * così un refresh token non può mai essere usato al posto di un access token
 * (\"token confusion\").</p>
 */
public enum TokenType {

    /** Token breve, autorizza le richieste verso i servizi. */
    ACCESS,

    /** Token lungo, serve solo a farsi rilasciare un nuovo access token. */
    REFRESH

}
