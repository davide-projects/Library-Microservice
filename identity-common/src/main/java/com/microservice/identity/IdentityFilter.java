package com.microservice.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Popola il {@link SecurityContextHolder} con l'identita' contenuta nel bearer token.
 *
 * <p>Non blocca nulla: se il token manca o non e' valido la richiesta prosegue anonima.
 * I 401/403 restano all'autorizzazione, cosi' un servizio senza regole non cambia
 * comportamento in questa fase.</p>
 *
 * <p>Va inserito nella SecurityFilterChain prima di {@code AnonymousAuthenticationFilter};
 * la registrazione automatica nel container servlet e' disattivata apposta.</p>
 */
public class IdentityFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(IdentityFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtIdentityParser parser;

    public IdentityFilter(JwtIdentityParser parser) {
        this.parser = parser;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Se un altro meccanismo ha gia' autenticato la richiesta, non lo sovrascriviamo.
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            bearerToken(request)
                    .flatMap(parser::parse)
                    .ifPresent(this::setAuthentication);
        }

        filterChain.doFilter(request, response);
    }

    private void setAuthentication(Identity identity) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication(identity));
        SecurityContextHolder.setContext(context);

        // Utile in collaudo e diagnosi: mai il token, solo cio' che e' gia' pubblico nel JWT.
        if (LOG.isDebugEnabled()) {
            LOG.debug("Identita' risolta: userId={}, roles={}", identity.userId(), identity.roles());
        }
    }

    private static Authentication authentication(Identity identity) {
        return new UsernamePasswordAuthenticationToken(
                identity,
                null,
                identity.roles().stream().map(SimpleGrantedAuthority::new).toList());
    }

    private static Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}