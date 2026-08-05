# JWT Task - Sostituzione Bearer Token statico con JWT HS256

Obiettivo: implementare l'autenticazione **JWT HS256** al posto del Bearer Token statico
(`API_GATEWAY_TOKEN`) attualmente verificato dall'API Gateway.

## Decisioni di design

| Scelta | Valore |
|---|---|
| Tipo JWT | Self-contained **HS256** con secret condiviso (algoritmo pinnato in `JwtTokenService`) |
| Dove validare | **Solo al gateway** (i microservizi restano `permitAll` + CORS, gateway unico entry point) |
| Emissione token | Endpoint dev `GET /auth/token` sul gateway |
| Chiamate interne loan → book/member | Invariate, senza header auth |

## Stato attuale (punti 1-10 completati)

```
api-gateway/src/main/java/com/apulia/apigateway/
├── ApiGatewayApplication.java
├── ratelimit/
│   ├── RateLimiterConfig.java      (prefix "ratelimit")
│   └── RateLimiterFilter.java      (WebFilter @Order(-1))
└── security/
    ├── BearerTokenFilter.java      (WebFilter @Order(0), validazione JwtTokenService, skip /auth/token)
    ├── AuthTokenFilter.java        (WebFilter @Order(-1), GET /auth/token)
    ├── JwtProperties.java          (prefix "security.jwt": secret, expiration default 3600)
    └── JwtTokenService.java        (generateToken + isValid, HS256)

api-gateway/src/test/java/com/apulia/apigateway/security/
└── JwtTokenServiceTest.java        (5 unit test: roundtrip, manomesso, scaduto, 401 no-header, 401 non-Bearer)
```

Configurato da:
- `config-repo/api-gateway.yml` (blocco `security.jwt.secret` con `${JWT_SECRET}` / `expiration: 3600`)
- `.env` / `.env.example` (`JWT_SECRET`, ≥32 caratteri)
- `docker-compose.yml` (env `JWT_SECRET` su config-server e api-gateway)
- `api-gateway/pom.xml` (jjwt 0.12.6: api + impl + jackson)

---

## Passaggi rimanenti

### 7. Nuovo endpoint `GET /auth/token` — `api-gateway/security/AuthTokenFilter.java`

`WebFilter` con `@Order(-1)` (prima del `BearerTokenFilter`, stesso ordine del
`RateLimiterFilter`): intercetta `/auth/token`, emette il token e completa la risposta.
Nessuna modifica alle route del gateway necessaria.

```java
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
```

> Nota: rispetto alla bozza originale, qui `expiresAt` usa `properties.getExpiration()` invece
> di 3600 hardcodato, per coerenza con `JwtTokenService.generateToken()`.

> Opzionale: escludere `/auth/token` anche dal `RateLimiterFilter` se non si vuole
> limitare l'emissione (default: il rate limit resta applicato).

---

### 8. Rimozione codice obsoleto

Cancellare:
- `api-gateway/src/main/java/com/apulia/apigateway/security/SimpleTokenService.java`
- `api-gateway/src/main/java/com/apulia/apigateway/security/TokenProperties.java`

---

### 9. Test (consigliato)

Nuovo unit test `api-gateway/src/test/java/com/apulia/apigateway/security/JwtTokenServiceTest.java`:

- `generateToken()` → `isValid(token)` è `true`
- token manomesso (es. modificare l'ultimo carattere) → `isValid` è `false`
- token scaduto (`expiration=0` o nel passato) → `isValid` è `false`
- header mancante / non `Bearer ...` → il `BearerTokenFilter` risponde 401
  (si può usare `MockServerWebExchange` da `spring-test`, incluso in `spring-boot-starter-test`)

Eseguire con:

```bash
.\mvnw.cmd test -pl api-gateway
```

---

### 10. README + Postman

**README.MD**:
- Sezione "Autenticazione": descrivere JWT HS256, ottenimento del token via
  `GET /auth/token`, header `Authorization: Bearer <jwt>`, risposta 401 + `WWW-Authenticate: Bearer`.
- Aggiornare gli esempi Postman/curl che usavano `Bearer my-secret-token-123`.
- Spuntare la TO-DO: `[x] Autenticazione JWT / OAuth2 (Keycloak)`.

**`postman/Local-Environment.postman_environment.json`**:
- Aggiornare la variabile `token` (es. chiamare prima `/auth/token` oppure inserire un JWT).

---

## Verifica finale

```bash
# Compilazione + test
.\mvnw.cmd package -DskipTests

# Build immagini docker
docker-compose build

# Avvio
docker-compose up -d

# Ottenere un token
curl http://localhost:8080/auth/token

# Chiamata protetta
curl -H "Authorization: Bearer <jwt>" http://localhost:8080/book
```

Risposta attesa senza token o con token invalido:
`401 Unauthorized` + header `WWW-Authenticate: Bearer`.

---

## Checklist

- [x] 1. Aggiunte dipendenze jjwt a `api-gateway/pom.xml`
- [x] 2. Aggiornato `config-repo/api-gateway.yml` (`security.jwt.secret` / `expiration`)
- [x] 3. Aggiornati `.env`, `.env.example`, `docker-compose.yml` (`JWT_SECRET`)
- [x] 4. Creata `JwtProperties.java`
- [x] 5. Creato `JwtTokenService.java`
- [x] 6. Adattato `BearerTokenFilter.java`
- [x] 7. Creato `AuthTokenFilter.java` (`GET /auth/token`)
- [x] 8. Rimossi `SimpleTokenService.java` e `TokenProperties.java`
- [x] 9. Aggiunto unit test `JwtTokenServiceTest.java`
- [x] 10. Aggiornati `README.MD` e `postman/Local-Environment.postman_environment.json`
- [ ] Build + test verdi (`.\mvnw.cmd test -pl api-gateway`, `docker-compose build`)
