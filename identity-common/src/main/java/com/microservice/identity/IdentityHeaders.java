package com.microservice.identity;

/**
 * Nomi degli header con cui il gateway propaga l'identita' ai servizi.
 *
 * <p>Sono costanti condivise per evitare che ogni modulo scriva la stringa a mano:
 * la fonte di verita' resta comunque il token, che i servizi rivalidano.</p>
 */
public final class IdentityHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLES = "X-User-Roles";

    private IdentityHeaders() {
    }
}