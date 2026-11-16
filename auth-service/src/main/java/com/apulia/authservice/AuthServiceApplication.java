package com.apulia.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * auth-service.
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} e' esclusa: l'autenticazione e'
 * manuale e stateless, senza {@code UserDetailsService}. Lasciandola attiva Boot
 * creerebbe un utente in memoria con password generata, rumore fuorviante in avvio.</p>
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}