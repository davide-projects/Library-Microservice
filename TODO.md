# TODO — Prossime implementazioni

Piano di lavoro ordinato in un flusso pulito e modulare per completare il backend
(Apulia Library — microservices).

**Convenzioni**
- Ogni fase si chiude con test e aggiornamento della documentazione.
- Nessuna fase inizia prima che le sue dipendenze siano concluse.
- Ordine: `A -> B -> C -> D -> E -> F`.

---

## Fase A — Identità (`auth-service`)

- [ ] A1. Creare il modulo Maven `auth-service` (Spring Web, Security, Data JPA, Validation, Flyway, jjwt, Lombok).
- [ ] A2. Aggiungere il modulo al `pom.xml` parent e a `docker-compose.yml` (Dockerfile, porta interna, `library-network`).
- [ ] A3. Aggiungere `auth_service_db` in `mysql/init.sql` + relativi privilegi.
- [ ] A4. Migrazioni Flyway: `users`, `roles`, `user_roles`.
- [ ] A5. Entity/repository per utenti e ruoli; password con BCrypt.
- [ ] A6. JWT con claim `roles` (`ROLE_ADMIN` / `ROLE_USER`), subject = username reale, `jti` per revoca.
- [ ] A7. Endpoint `/auth/login`, `/auth/refresh`, `/auth/logout`; dismissione del vecchio `GET /auth/token`.
- [ ] A8. Propagazione identità ai servizi (header `X-User-Id` / `X-User-Role` dal gateway + parsing JWT downstream).
- [ ] A9. `config-repo/auth-service.yml` + secret JWT condiviso via `JWT_SECRET`.
- [ ] A10. **Unit test**: generazione/validazione token, encoding password, login valido/non valido, revoca (`jti`).

---

## Fase B — Autorizzazione (gateway + servizi)

- [ ] B1. Gateway: `SecurityWebFilterChain` + resource server JWT, rimozione dei `WebFilter` manuali (`BearerTokenFilter`, `AuthTokenFilter`).
- [ ] B2. Validazione firma/scadenza/issuer/ruoli sul gateway.
- [ ] B3. Regole di route: `ROLE_ADMIN` = CRUD completo + lettura audit; `ROLE_USER` = GET catalogo + prestiti propri.
- [ ] B4. Servizi: `@EnableMethodSecurity` + `@PreAuthorize`, rimozione del `permitAll`.
- [ ] B5. Verifica CORS, health check e aspetti di sicurezza residui.
- [ ] B6. **Unit test**: 401 senza token, 403 ruolo insufficiente, 200 con ruolo corretto, scadenza token.

---

## Fase C — Audit

- [ ] C1. Hibernate Envers (`@Audited`) su `book`, `members`, `loans` (tabelle `*_AUD` + `REVINFO`).
- [ ] C2. `MappedSuperclass` con `created_at` / `updated_at` / `created_by` / `updated_by` + `AuditorAware` basato sull'identità (dipende da A).
- [ ] C3. Migrazioni Flyway per le colonne temporali e di autore.
- [ ] C4. Audit degli eventi di sicurezza (login ok/ko, accessi negati) come documenti su MongoDB.
- [ ] C5. Endpoint di consultazione audit riservati al ruolo ADMIN.
- [ ] C6. **Unit test**: interrogazione revisioni Envers, popolamento automatico dei campi di auditing, registrazione eventi.

---

## Fase D — Logging (JSON nei container)

- [ ] D1. `logstash-logback-encoder` in tutti i moduli + `logback-spring.xml` con pattern JSON.
- [ ] D2. Micrometer Tracing + bridge; propagazione `traceId` / `spanId` dal gateway ai servizi (WebClient/RestTemplate).
- [ ] D3. Filtro di request-logging (metodo, path, status, latenza, `userId`, `traceId`).
- [ ] D4. Masking dei dati sensibili (phone, secret, credenziali) nei log.
- [ ] D5. Livelli di log per ambiente via `config-repo` (attuale `root: WARN`).
- [ ] D6. **Unit test**: presenza dei campi strutturati, masking applicato, propagazione del `traceId`.

---

## Fase E — NoSQL (MongoDB + Redis)

- [ ] E1. `docker-compose.yml`: container `mongo` e `redis` (volumi, healthcheck, `library-network`).
- [ ] E2. Nuovo `audit-service` su MongoDB per documenti audit/eventi append-only + endpoint ADMIN di ricerca.
- [ ] E3. Redis in `auth-service`/gateway: blacklist JWT, refresh token, sessioni.
- [ ] E4. Redis per cache catalogo (`book-service`, opzionale) e valutazione migrazione Bucket4j a store Redis (`bucket4j-redis`).
- [ ] E5. `config-repo`: `spring.data.mongodb.*` e `spring.data.redis.*`.
- [ ] E6. **Unit test**: persistenza/lettura documenti Mongo, blacklist/refresh su Redis, fallback se Redis non disponibile.

---

## Fase F — Chiusura

- [ ] F1. Aggiornare `config-repo`, `docker-compose.yml`, `.env.example`, `README`.
- [ ] F2. Test end-to-end (Postman) dei flussi admin/user, audit e logging.
- [ ] F3. Esecuzione completa della suite: `.\mvnw.cmd test`.
- [ ] F4. Avvio integrato: `docker-compose up -d` e verifica health check.

---

## Dipendenze principali

- `A -> B`: l'autorizzazione per ruolo richiede l'identità JWT.
- `A -> C`: l'audit "chi" richiede la propagazione dell'identità.
- `A -> E3`: blacklist/refresh su Redis richiedono `jti` e refresh token.
- `C1 -> E2`: gli eventi di audit alimentano `audit-service` (MongoDB).
- `D` trasversale: può procedere dopo B, in parallelo a C.
