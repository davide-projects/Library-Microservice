package com.apulia.apigateway.security;

import com.microservice.identity.Identity;
import com.microservice.identity.IdentityHeaders;
import com.microservice.identity.JwtIdentityParser;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Propaga l'identita' ai servizi tramite gli header {@code X-User-*}.
 *
 * <p>Gli header inviati dal client vengono sempre rimossi: se il bearer non e' un access
 * token valido la richiesta prosegue senza identita'. Gli header sono metadati, la fonte
 * di verita' resta il token che ogni servizio rivalida.</p>
 *
 * <p>Gira dopo {@link BearerTokenFilter}, che ha gia' respinto i token non validi.</p>
 */
@Component
@Order(10)
public class IdentityPropagationFilter implements WebFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtIdentityParser parser;

    public IdentityPropagationFilter(JwtIdentityParser parser) {
        this.parser = parser;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        Optional<Identity> identity = bearerToken(exchange).flatMap(parser::parse);

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(IdentityHeaders.USER_ID);
                    headers.remove(IdentityHeaders.USER_ROLES);

                    identity.ifPresent(value -> {
                        headers.set(IdentityHeaders.USER_ID, Long.toString(value.userId()));
                        if (!value.roles().isEmpty()) {
                            headers.set(IdentityHeaders.USER_ROLES, String.join(",", value.roles()));
                        }
                    });
                })
                .build();

        return chain.filter(exchange.mutate().request(request).build());
    }

    private static Optional<String> bearerToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}