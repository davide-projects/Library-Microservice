package com.microservice.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurazione necessaria a validare i token di auth-service.
 *
 * <p>Stesso prefisso usato dal servizio di identita': il secret e' condiviso, l'issuer
 * atteso e' {@code auth-service}. Senza questi valori il servizio non deve partire,
 * perche' accetterebbe richieste senza saperle autenticare.</p>
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtIdentityProperties(String issuer, String secret) {
}