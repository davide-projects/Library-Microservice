package com.apulia.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtTokenService {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken() {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getExpiration());
        return Jwts.builder()
                .issuer("apulia-library")
                .subject("gateway-client")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public boolean isValid(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            boolean correctAlgorithm = "HS256".equals(jws.getHeader().getAlgorithm());
            return correctAlgorithm && "apulia-library".equals(jws.getPayload().getIssuer());
        } catch (Exception e) {
            return false;
        }
    }
}