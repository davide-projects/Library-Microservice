package com.microservice.identity;

import java.util.Optional;

/**
 * Punto di accesso applicativo all'identita' della richiesta in corso.
 *
 * <p>I controller e i service dei vari moduli usano questa interfaccia invece di
 * leggere header o token: la provenienza dell'identita' resta un dettaglio interno.</p>
 */
public interface IdentityResolver {

    /**
     * @return l'identita' autenticata, oppure {@link Optional#empty()} per una richiesta anonima.
     *         Non lancia eccezioni: i codici 401/403 sono responsabilita' dell'autorizzazione.
     */
    Optional<Identity> currentIdentity();
}