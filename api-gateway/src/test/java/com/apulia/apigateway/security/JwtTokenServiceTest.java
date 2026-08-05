package com.apulia.apigateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    private JwtProperties properties() {
        JwtProperties p = new JwtProperties();
        p.setSecret("0123456789abcdef0123456789abcdef");
        return p;
    }

    @Test
    void generatedTokenIsValid() {
        JwtTokenService service = new JwtTokenService(properties());
        String token = service.generateToken();

        assertThat(service.isValid(token)).isTrue();
    }

    @Test
    void tamperedTokenIsInvalid() {
        JwtTokenService service = new JwtTokenService(properties());
        String token = service.generateToken();
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("a") ? "b" : "a");

        assertThat(service.isValid(tampered)).isFalse();
    }

    @Test
    void expiredTokenIsInvalid() {
        JwtProperties props = properties();
        props.setExpiration(-1);
        JwtTokenService service = new JwtTokenService(props);
        String token = service.generateToken();

        assertThat(service.isValid(token)).isFalse();
    }

    @Test
    void nonHs256TokenIsInvalid() {
        JwtProperties props = properties();
        props.setSecret("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        JwtTokenService service = new JwtTokenService(props);
        SecretKey key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));

        String token = Jwts.builder()
                .issuer("apulia-library")
                .subject("gateway-client")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key, Jwts.SIG.HS512)
                .compact();

        assertThat(service.isValid(token)).isFalse();
    }

    @Test
    void missingHeaderReturns401() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/book"));

        new BearerTokenFilter(new JwtTokenService(properties()))
                .filter(exchange, e -> Mono.empty())
                .block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("WWW-Authenticate"))
                .isEqualTo("Bearer");
    }

    @Test
    void nonBearerHeaderReturns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/book").header("Authorization", "Basic abc"));

        new BearerTokenFilter(new JwtTokenService(properties()))
                .filter(exchange, e -> Mono.empty())
                .block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("WWW-Authenticate"))
                .isEqualTo("Bearer");
    }
}
