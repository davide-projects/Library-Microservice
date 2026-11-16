package com.apulia.apigateway.security;

import com.microservice.identity.JwtIdentityParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Il gateway e' reactive: l'auto-configurazione servlet di identity-common non si attiva,
 * quindi il parser condiviso viene costruito qui dalla stessa configurazione JWT.
 */
@Configuration
public class IdentityConfig {

    @Bean
    public JwtIdentityParser jwtIdentityParser(JwtProperties properties) {
        return new JwtIdentityParser(properties.getSecret(), properties.getIssuer());
    }
}