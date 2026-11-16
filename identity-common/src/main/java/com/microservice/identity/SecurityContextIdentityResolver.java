package com.microservice.identity;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Legge l'identita' dal {@link SecurityContextHolder}, popolato da {@link IdentityFilter}.
 */
public final class SecurityContextIdentityResolver implements IdentityResolver {

    @Override
    public Optional<Identity> currentIdentity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Identity identity) {
            return Optional.of(identity);
        }
        return Optional.empty();
    }
}