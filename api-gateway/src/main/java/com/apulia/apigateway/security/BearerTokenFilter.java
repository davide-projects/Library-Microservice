package com.apulia.apigateway.security;

import com.microservice.identity.JwtIdentityParser;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Autentica le richieste in ingresso.
 *
 * <p>Durante A8 convivono i due formati: il token di sviluppo emesso da
 * {@code GET /auth/token} (HS256, issuer {@code apulia-library}) e l'access token
 * reale di auth-service (HS384, issuer {@code auth-service}, con {@code uid}).
 * B1 rimuovera' il primo ramo e i due filtri legacy.</p>
 */
@Component
@Order(0)
public class BearerTokenFilter implements WebFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService tokenService;
    private final JwtIdentityParser identityParser;

    public BearerTokenFilter(JwtTokenService tokenService, JwtIdentityParser identityParser) {
        this.tokenService = tokenService;
        this.identityParser = identityParser;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        String path = exchange.getRequest().getPath().value();
        if ("/auth/token".equals(path)) {
            return chain.filter(exchange);
        }

        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (auth == null || !auth.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange);
        }

        String token = auth.substring(BEARER_PREFIX.length()).trim();

        // Validazione transitoria: token legacy del gateway oppure access token di auth-service.
        if (!tokenService.isValid(token) && identityParser.parse(token).isEmpty()) {
            return unauthorized(exchange);
        }

        return chain.filter(exchange);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return exchange.getResponse().setComplete();
    }
}