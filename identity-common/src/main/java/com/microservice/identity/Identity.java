package com.microservice.identity;

import java.util.List;

/**
 * Identita' autenticata di chi sta chiamando il servizio.
 *
 * <p>Deriva esclusivamente dalla validazione del JWT: gli header {@code X-User-*}
 * propagati dal gateway sono metadati, non una fonte di verita'.</p>
 *
 * @param userId   id numerico dell'utente (claim {@code uid}) usato da audit e carrello
 * @param username username (claim {@code sub})
 * @param roles    ruoli gia' nel formato Spring Security ({@code ROLE_ADMIN}, {@code ROLE_USER})
 */
public record Identity(long userId, String username, List<String> roles) {

    public Identity {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}