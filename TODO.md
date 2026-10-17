# TODO — Prossime implementazioni

Piano di lavoro ordinato in un flusso pulito e modulare per completare il backend
(Apulia Library — microservices).

**Convenzioni**
- Ogni fase si chiude con test, aggiornamento della documentazione e commit + push su `dev` (vedi [VERSIONING.md](VERSIONING.md)).
- Nessuna fase inizia prima che le sue dipendenze siano concluse.
- Ordine: `A -> B -> C -> D -> E -> F -> G -> H`.

---

## Fase A — Identità (`auth-service`)

- [x] A1. Creare i moduli Maven `auth-service` (Spring Web, Security, Data JPA, Validation, Flyway, jjwt, Lombok) e `identity-common` (libreria condivisa: `IdentityResolver`, validazione JWT downstream, nessuna dipendenza dai servizi).
- [x] A2. Aggiungere entrambi i moduli al `pom.xml` parent; `auth-service` anche a `docker-compose.yml` (Dockerfile, porta interna, `library-network`) — `identity-common` è una libreria, senza container.
- [ ] A3. Aggiungere `auth_service_db` in `mysql/init.sql` + relativi privilegi.
- [ ] A4. Migrazioni Flyway: `users`, `roles`, `user_roles` + `revoked_tokens` (jti revocati) + `refresh_tokens` (revocabili, rotazione) — persistenza provvisoria su DB, sostituibile con Redis in Fase E.
- [ ] A5. Entity/repository per utenti e ruoli; password con BCrypt.
- [ ] A6. JWT con claim `roles` (`ROLE_ADMIN` / `ROLE_USER`), subject = username reale, `jti` per revoca; emissione di access + refresh token con `jti` distinti.
- [ ] A7. Endpoint `/auth/login`, `/auth/refresh`, `/auth/logout` (la dismissione del vecchio `GET /auth/token` slitta alla Fase B, vedi B1).
- [ ] A8. Propagazione identità ai servizi (header `X-User-Id` / `X-User-Role` dal gateway + parsing JWT downstream) consumando la libreria `identity-common`, senza logica duplicata nei servizi.
- [ ] A9. `config-repo/auth-service.yml` + secret JWT condiviso via `JWT_SECRET`.
- [ ] A10. **Unit test**: generazione/validazione token, encoding password, login valido/non valido, revoca (`jti`).

---

## Fase B — Autorizzazione (gateway + servizi)

- [ ] B1. Gateway: `SecurityWebFilterChain` + resource server JWT, rimozione dei `WebFilter` manuali (`BearerTokenFilter`, `AuthTokenFilter`) e dismissione dell'endpoint di sviluppo `GET /auth/token` (slittata dalla Fase A).
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
- [ ] C4. **Soft delete** (eliminazione logica) sul db relazionale: colonna `deleted_at` (nullable) su `book`, `members`, `loans` in una migrazione Flyway; le endpoint `DELETE` impostano `deleted_at` invece di rimuovere la riga.
- [ ] C5. Filtri e coerenza del soft delete: liste/ricerche di default escludono i record eliminati (`deleted_at IS NULL`), il ruolo ADMIN può richiederli, regole di business (es. libro con prestito attivo non eliminabile, record eliminato non selezionabile in nuovi prestiti).
- [ ] C6. Audit degli eventi di sicurezza (login ok/ko, accessi negati) come documenti su MongoDB.
- [ ] C7. Endpoint di consultazione audit riservati al ruolo ADMIN (incluse le revisioni Envers generate dalle eliminazioni logiche).
- [ ] C8. **Unit test**: interrogazione revisioni Envers, popolamento automatico dei campi di auditing, registrazione eventi, soft delete (record eliminato non compare nelle query e `deleted_at` è valorizzato).

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

## Fase F — Carrello & Pagamento simulato (`payment-service`)

- [ ] F1. Creare il modulo Maven `payment-service` (Spring Web, Validation, Data JPA, Flyway, Lombok): modulo nel `pom.xml` parent, `Dockerfile`, porta interna, `library-network` in `docker-compose.yml`.
- [ ] F2. `payment_service_db` in `mysql/init.sql` + relativi privilegi; migrazioni Flyway `carts`, `cart_items`, `payments` (storico pagamenti senza PAN completo).
- [ ] F3. Carrello per utente autenticato (identità da header `X-User-Id`, dipende da A): `add` / `remove` / `list` + calcolo del costo totale; persistenza su Redis con TTL di sessione (dipende da E1/E3).
- [ ] F4. Endpoint di checkout: riepilogo dei costi + inserimento della carta di credito (numero, scadenza, cvv); il PAN completo non viene persistito ed è mascherato nei log (dipende da D4).
- [ ] F5. Validazione della carta con **algoritmo di Luhn (mod10)** + vincoli di lunghezza (13–19 cifre) e scadenza: numero non valido → `400 Bad Request` con messaggio dedicato e nessun passaggio alla fase successiva.
- [ ] F6. Simulazione di approvazione/declino (carte test note approvate/declinate, nessuna chiamata a gateway di pagamento reali): solo un pagamento `APPROVED` passa alla fase successiva (→ Fase G).
- [ ] F7. Route gateway `/cart` e `/checkout` + regole `ROLE_USER` (dipende da B3).
- [ ] F8. **Unit test**: carte Luhn valide/invalidhe note (es. `4242…`, `4000…`), rifiuto di un numero corrotto (400), declino del simulatore, calcolo del totale del carrello, 401/403 sui nuovi endpoint.

---

## Fase G — Coda di elaborazione (RabbitMQ)

- [ ] G1. Container `rabbitmq` in `docker-compose.yml` (volumi, healthcheck, `library-network`, management UI) + dipendenze `spring-boot-starter-amqp` nei moduli coinvolti.
- [ ] G2. Producer in `payment-service`: pagamento `APPROVED` → messaggio `LoanRequested` (`memberId`, `bookIds`, `cartId`, `paymentId`) sulla queue `loans.created`.
- [ ] G3. Consumer in `loan-service`: riusa della logica esistente di `createLoans` (validazione libro/membro, controllo del doppio prestito) per consumare la coda.
- [ ] G4. Stati e idempotenza: esito dell'elaborazione salvato (`PENDING` / `COMPLETED` / `FAILED`), messaggi ripetuti non creano doppi prestiti, stato dell'ordine esposto all'utente.
- [ ] G5. Gestione degli errori: messaggio fallito (es. libro già prestato o eliminato con soft delete) → retry / DLQ, nessun rilascio silenzioso del messaggio.
- [ ] G6. `config-repo`: nomi delle queue, retry e concorrenza dei consumer (`payment-service.yml`, `loan-service.yml`).
- [ ] G7. **Unit test**: serializzazione/deserializzazione del messaggio, consumer happy path, messaggio fallito → stato `FAILED`, nessun double-loan.

---

## Fase H — Chiusura

- [ ] H1. Aggiornare `config-repo`, `docker-compose.yml`, `.env.example`, `README`.
- [ ] H2. Test end-to-end (Postman) dei flussi admin/user, audit, logging, carrello/pagamento e coda.
- [ ] H3. Esecuzione completa della suite: `.\mvnw.cmd test`.
- [ ] H4. Avvio integrato: `docker-compose up -d` e verifica health check.
- [ ] H5. Release git: merge `dev` → `prod`, tag `vX.Y.Z`, sincronizzazione `backup` — solo con H2–H4 OK (vedi [VERSIONING.md](VERSIONING.md)).

---

## Dipendenze principali

- `A -> B`: l'autorizzazione per ruolo richiede l'identità JWT.
- `A -> C`: l'audit "chi" richiede la propagazione dell'identità.
- `A -> E3`: blacklist/refresh su Redis richiedono `jti` e refresh token.
- `C1 -> E2`: gli eventi di audit alimentano `audit-service` (MongoDB).
- `A -> F`: carrello e checkout appartengono a un utente autenticato.
- `B3 -> F`: le route `/cart` e `/checkout` richiedono le regole di routing per ruolo.
- `E1/E3 -> F`: il carrello è persistito su Redis.
- `D4 -> F`: il PAN della carta di credito non deve comparire nei log (masking).
- `C4/C5 -> G`: la coda non deve creare prestiti su record eliminati (soft delete).
- `F -> G`: solo un pagamento approvato alimenta la coda RabbitMQ.
- `D` trasversale: può procedere dopo B, in parallelo a C.
- `F -> H` e `G -> H`: la chiusura comprende anche le nuove fasi.

---