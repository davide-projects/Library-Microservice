package com.apulia.apigateway.security;

import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
@Order(-1)
public class AuthTokenFilter implements WebFilter {

    private final JwtTokenService tokenService;
    private final JwtProperties properties;

    public AuthTokenFilter(JwtTokenService tokenService, JwtProperties properties) {
        this.tokenService = tokenService;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (!"/auth/token".equals(path) || !"GET".equals(exchange.getRequest().getMethod().name())) {
            return chain.filter(exchange);
        }

        String token = tokenService.generateToken();
        String body = String.format(
                "{\"token\":\"%s\",\"expiresAt\":\"%s\"}",
                token,
                Instant.now().plusSeconds(properties.getExpiration()).toString()
        );

        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.OK);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }
}