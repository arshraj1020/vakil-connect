# Production Readiness & Security Audit

**Scope:** full stack — Spring Security, JWT, authz, CORS, exception handling,
logging, configuration, secrets, database, Flyway, OpenAPI, profiles, Docker,
frontend env/build.
**Method:** every finding below was read out of the tree, with a file and line.
Nothing here is inferred from memory.
**Status:** audit only. No code changed.

---

## Verdict

**Do not deploy publicly yet.** Five blockers, of which three are one-line
configuration changes and one is missing deployment infrastructure.

The application's *authorization* model is sound — default-deny, explicit
origins, BCrypt, server-side role checks, a database-enforced booking
invariant, 242 passing integration tests against a real PostgreSQL. What is not
ready is everything around it: the API documents itself to the public, the
security framework logs its internals on every request, and nothing stops an
attacker making a million login attempts.

Realistic effort to green: **2–3 focused days**, most of it the Dockerfile and
CI pipeline rather than the security fixes.

| Severity | Count | Meaning |
|---|---|---|
| **Critical** | 5 | Must be fixed before a public URL exists |
| **High** | 10 | Fix before or immediately after launch; each is exploitable or blinding |
| **Medium** | 12 | Fix in the first weeks; performance, operability, hygiene |
| **Low** | 9 | Track and clean up |

---

## CRITICAL

### C1 — The entire API documents itself to the public

`SecurityConfig.java:62-68` grants `permitAll` to `/v3/api-docs/**`,
`/swagger-ui/**` and `/swagger-ui.html`, and `application.yaml:20-24` enables
springdoc unconditionally with no profile guard.

Anyone who finds the host gets a complete, machine-readable map of every
endpoint — including `/api/admin/**` — with request schemas, field
constraints and a working "Try it out" console. This is the single highest-value
artefact you can hand an attacker: it removes the entire reconnaissance phase.

**Fix:** disable springdoc outside dev via profile, and remove the matchers
from the production filter chain. If the docs are needed in staging, put them
behind the same network boundary as actuator.

### C2 — Spring Security logs its internals on every request

`application.yaml:93-95` sets `logging.level.org.springframework.security: DEBUG`.

At DEBUG this framework emits the filter chain, authentication decisions,
principal details and authorization outcomes for **every single request**. In
production that is: sensitive material in a log aggregator that has a different
access-control model from the application, an enormous volume increase, and a
measurable per-request cost.

`DEPLOYMENT.md` already documents an override, but a default that is wrong and
a document telling you to change it is not a control — it is a trap for whoever
deploys without reading it.

**Fix:** default to `WARN` in `application.yaml`; keep DEBUG in a dev profile.

### C3 — No rate limiting anywhere

Verified absent: no Bucket4j, no Resilience4j, no filter, no gateway.
`/api/auth/login` accepts unlimited attempts from one IP against one account.

Three separate consequences:

- **Credential stuffing** is unthrottled.
- **CPU denial of service.** BCrypt at strength 10 costs ~100 ms per attempt
  *by design*. A few hundred concurrent login requests saturate the Tomcat
  thread pool with hashing work. This makes the login endpoint a DoS amplifier
  against an unauthenticated attacker's trivial cost.
- **Unbounded account creation** via `/api/auth/register`.

**Fix:** per-IP and per-email token buckets on `/api/auth/**`. Already designed
in `backend/docs/IDENTITY-TDD.md` §15 — bring that work forward.

### C4 — Logging out does not log you out

`auth-storage.ts:clearStoredToken` deletes the cookie. That is the entire
logout implementation. `JwtService` issues a 24-hour token
(`JWT_EXPIRATION:86400000`) carrying only `sub`, `iat`, `exp` — no `jti`, no
version claim — and there is no denylist and no refresh/revocation path.

So a token captured from a shared computer, a proxy log, or an XSS payload
stays valid for up to 24 hours **after the user has logged out**, and there is
no action the user or an administrator can take to stop it. Deactivating the
account works (`JwtAuthenticationFilter:80` rechecks `active` per request) but
that is a blunt instrument and not available to the user.

**Fix:** the `credentials_changed_at` mechanism designed in the identity TDD
covers password change; add a server-side logout that bumps the same value.
The column already exists in V7.

### C5 — There is no deployable artefact

No Dockerfile, no compose file, no CI pipeline. The `docker/` directory exists
and is empty. Deployment today is: build a jar by hand, `npm run build` by
hand, run both by hand, on a host configured by hand.

This is a blocker in its own right, not a nicety. It means no reproducible
build, no verified test run before deploy, no rollback to a known image, no
record of what is actually running, and a hand-typed environment where a single
missing variable is a security incident (see H9).

**Fix:** multi-stage Dockerfiles for both apps, a compose file for local
parity, and a CI pipeline that runs `./mvnw clean test` and `npm run build` on
every push.

---

## HIGH

### H1 — Every SQL statement is logged

`application.yaml:14` sets `show-sql: true`. In production this logs every
query, doubles or triples log volume, and puts query shapes — with values
adjacent in the parameter log — into the aggregator. **Fix:** `false`, with a
dev-profile override.

### H2 — `server.forward-headers-strategy` is not set

No `server:` block exists at the application level. Behind the TLS-terminating
reverse proxy that `DEPLOYMENT.md` assumes, this causes four failures at once:

- `request.isSecure()` is false, so **Spring Security never emits HSTS**.
- `getRemoteAddr()` returns the proxy's IP, so all logs attribute every request
  to one address — and any future rate limiter would throttle globally.
- Generated absolute URLs use the wrong scheme and host.
- Anything conditioned on HTTPS silently takes the plaintext branch.

**Fix:** `server.forward-headers-strategy: framework`, and ensure the proxy
*overwrites* rather than appends `X-Forwarded-For`.

### H3 — The JWT is readable by JavaScript

`auth-storage.ts` uses `js-cookie`, which cannot set `httpOnly`. `secure` and
`sameSite=lax` are set, but any XSS anywhere on the origin reads the token and
exfiltrates it — and per C4 that token cannot then be revoked.

**Fix:** move to an `httpOnly` cookie set by a Next route-handler proxy. This is
an architectural change, already scoped as a later phase; until then C4's
revocation is the mitigating control.

### H4 — Content-Security-Policy is not enforced

`next.config.ts` sends `Content-Security-Policy-Report-Only`. The browser
evaluates and reports but blocks nothing, so `frame-ancestors`, `form-action`
and `object-src` are all inert. `X-Frame-Options: DENY` is currently the only
real clickjacking defence.

The file says this is deliberate and one header name away. It has been in
Report-Only long enough — **fix:** watch the console across every role, then
rename the header.

### H5 — Login responses disclose account state

`GlobalExceptionHandler.java:86-90` returns `ex.getMessage()` verbatim for
`AuthenticationException`. Spring Security hides
`UsernameNotFoundException` behind "Bad credentials", but **not**
`DisabledException` — a deactivated account answers `"User is disabled"`.

That distinguishes *"this account exists and was deactivated"* from *"wrong
password"* to an unauthenticated caller, which is account enumeration plus a
disclosure of moderation actions.

**Fix:** return a constant message for every authentication failure; carry any
distinction the frontend needs in a machine-readable `code`, decided
deliberately, not leaked from a framework string.

### H6 — Unexpected 500s are never logged

`GlobalExceptionHandler.java:100-106` catches `RuntimeException`, returns a
generic body — correctly — and **logs nothing**. Because the handler consumes
the exception, Spring never logs it either.

Every unexpected production failure is therefore invisible: no stack trace, no
message, no class name, anywhere. You would learn about outages from users.
This is the finding most likely to turn a small incident into a long one.

**Fix:** `log.error("Unhandled exception at {}", request.getRequestURI(), ex)`
in the fallback handler. Keep the response body generic.

### H7 — Actuator has no authentication

`application.yaml:45-56` puts actuator on port 9091 bound to `127.0.0.1`,
exposing `health`, `info`, `prometheus`. The exposure list is tight and
`show-details: never` is correct. But **the network is the only access
control** — there is no auth on that port at all.

The default binding is safe for a sidecar scraper and wrong for most managed
platforms, where you must bind `0.0.0.0` and then the port is reachable from
anything that can route to the container.

**Fix:** keep the binding decision explicit per platform, and add a
NetworkPolicy or security-group rule. Never expose 9091 through a load balancer.

### H8 — No account lockout

Independent of C3: nothing tracks consecutive failures per account. Even a
rate-limited attacker gets steady, indefinite guesses.

**Fix:** progressive delay or temporary lock after N failures, with the counter
keyed on the account, not the IP.

### H9 — Database credentials have permissive defaults

`application.yaml:9-11`: `DB_USERNAME` defaults to `arshraj` — a real developer
account name, leaked in the repo — and `DB_PASSWORD` defaults to **empty**.

If either variable is missing in production the application does not fail; it
attempts a connection with a guessable username and no password. A
misconfiguration should be a startup failure, exactly as `JWT_SECRET` already
is.

**Fix:** remove both defaults so absence is fail-fast.

### H10 — No backup or restore procedure

Nothing in the repo describes taking a backup, and no restore has been
rehearsed. V7 contains an irreversible `UPDATE` over `users`, and future
migrations will contain more.

**Fix:** automated daily `pg_dump`, off-host retention, and **one rehearsed
restore** before launch. An untested backup is not a backup.

---

## MEDIUM

| ID | Finding | Where | Why it matters |
|---|---|---|---|
| M1 | Lawyer keyword search is a leading-wildcard `LIKE` on `full_name` and `bio` with no supporting index | `LawyerRepository:119-121` | Full table scan on every public search. `pg_trgm` exists (V3) but indexes only `cities`. Degrades non-linearly with lawyer count |
| M2 | No connection-pool, thread-pool or graceful-shutdown configuration | no `server:`/`hikari` block | Hikari defaults to 10 connections; a slow query stalls the app with no tuning lever. No graceful shutdown means in-flight requests are killed on every deploy |
| M3 | No response compression | — | JSON search results ship uncompressed; the cheapest available latency win |
| M4 | Spring Security's default `Cache-Control: no-store` applies to public GETs | framework default | Public lawyer search and reference data cannot be cached by browsers or a CDN, so every visitor hits the database |
| M5 | `handleIllegalArgument` echoes `ex.getMessage()` | `GlobalExceptionHandler:94-98` | Reflects internal messages and raw user input (e.g. `Role.valueOf` → `No enum constant Role.HACKER`) |
| M6 | No structured logging, no correlation ID, no rotation | — | Plain console output. Cannot trace one request across a stack trace and an access log; disks fill |
| M7 | `.gitignore` covers `.env` but not `.env.local` or `.env.production` | `.gitignore:23` | Verified: `git check-ignore` says `.env.local` and `.env.production` are **not ignored** at root or in `backend/`. One `git add -A` from committing production secrets |
| M8 | No `robots.txt` | `frontend/public/` is empty | Swagger (C1), login and dashboard routes are all indexable |
| M9 | Production build depends on devDependencies | `query-provider.tsx:4` | `ReactQueryDevtools` is a static import (render is correctly guarded, but resolution is not). `npm ci --omit=dev && npm run build` fails module-not-found |
| M10 | No health endpoint on the application port | actuator is on 9091 | Most platforms probe the app port. There is nothing there to probe |
| M11 | `is_email_verified` is now `true` for every user | V7 step 2 | The V7 backfill grandfathered everyone, and no verification flow exists yet. The column currently asserts something untrue — a real risk on a marketplace selling professional trust |
| M12 | No password reset | — | A locked-out user has no self-service recovery, and support has no mechanism either. On a public launch this generates immediate load |

---

## LOW

| ID | Finding | Note |
|---|---|---|
| L1 | `jwt-decode` is an unused dependency | Verified zero imports. Remove — supply-chain surface for no benefit |
| L2 | `ErrorResponse.timestamp` is `LocalDateTime` | Zone-less timestamps in API responses and logs |
| L3 | No `@EnableMethodSecurity` | All authz is URL-pattern; ownership checks live in service code with no annotation-level backstop |
| L4 | `GET /api/lawyers/{id}` returns unverified profiles | `LawyerServiceImpl:203-208` is a bare `findById`. Search correctly filters on `verified`; the detail endpoint does not. Low severity — UUIDs are not enumerable |
| L5 | No request-size or multipart limits | Defaults apply; explicit bounds are cheap |
| L6 | `database/schema.sql` still present | Clearly marked "DO NOT RUN", but a stale schema in the repo is a footgun |
| L7 | No `SECURITY.md` | No disclosure contact for a public legal-services site |
| L8 | No dependency scanning | No Dependabot, no OWASP plugin. `next`, `axios`, `zod` age quickly |
| L9 | BCrypt left at strength 10 | Defensible in 2026, but decide it deliberately, and note it interacts with C3 |

---

## What is already right

Worth stating, because the checklist below is long and the foundation is not
the problem:

- **Default-deny authorization** — `anyRequest().authenticated()`, with public
  routes enumerated and GET-scoped
- **CORS is explicit** — named origins, never `*`, credentials off, and the
  allowed-header list is narrow
- **BCrypt** password hashing; no plaintext or reversible storage anywhere
- **`JWT_SECRET` fails fast** with no default — the pattern H9 should copy
- **Flyway owns the schema** with `ddl-auto: validate`; migrations are immutable
  and checksummed
- **`open-in-view: false`** — no accidental lazy loading in the view layer
- **Double-booking is a database constraint**, not application logic
- **The generic 500 body leaks nothing**; no `printStackTrace`, no `System.out`
  anywhere in `main`
- **No secrets committed** — swept the full tracked file list and history-facing
  patterns; `.env.local` on disk contains only a localhost URL
- **Frontend security headers** are present and well-reasoned
- **Deactivated and deleted users are rejected per request**, not just at login
- **242 integration tests** against real PostgreSQL via Testcontainers

---

## Deployment checklist

Ordered so that each block is independently shippable and the cheapest, highest-value
fixes come first. Tick nothing you have not verified.

### Block 1 — Configuration hardening *(hours, no architectural change)*

- [ ] **C2** `logging.level.org.springframework.security` → `WARN`; DEBUG moves to a dev profile
- [ ] **H1** `spring.jpa.show-sql` → `false`; dev-profile override
- [ ] **C1** Introduce a `prod` profile; disable springdoc in it and drop the swagger matchers from the filter chain
- [ ] **H2** `server.forward-headers-strategy: framework`; confirm the proxy overwrites `X-Forwarded-For`
- [ ] **H9** Remove the `DB_USERNAME` and `DB_PASSWORD` defaults so absence fails startup
- [ ] **H6** Log the exception in the 500 fallback handler; keep the response body generic
- [ ] **H5** Constant message for all authentication failures
- [ ] **M5** Stop echoing `IllegalArgumentException` messages
- [ ] **M7** Extend `.gitignore` to `.env.local`, `.env.*.local`, `.env.production`
- [ ] **M8** Add `robots.txt`
- [ ] **L1** Remove `jwt-decode`
- [ ] Re-run the full backend suite; confirm 242 green

### Block 2 — Deployment infrastructure *(the long pole)*

- [ ] **C5** Multi-stage `Dockerfile` for the backend (JRE 21, non-root user, no build tools in the final layer)
- [ ] **C5** Multi-stage `Dockerfile` for the frontend (Next standalone output, non-root)
- [ ] **C5** `docker-compose.yml` with Postgres 16 for local parity
- [ ] **M9** Verify the frontend image builds with production-only dependencies, or move `@tanstack/react-query-devtools` to `dependencies` deliberately
- [ ] **C5** CI pipeline: `./mvnw clean test` + `npm run build` + `npm test` on every push
- [ ] **M10** Health endpoint reachable on the application port for the platform probe
- [ ] **M2** Graceful shutdown; Hikari pool sized to the database's `max_connections`
- [ ] **H7** Decide the actuator binding for the target platform; add the network rule; confirm 9091 is not routable externally

### Block 3 — Abuse resistance *(the real attack surface)*

- [ ] **C3** Per-IP and per-email rate limits on `/api/auth/**` — design already exists in `IDENTITY-TDD.md` §15
- [ ] **H8** Account lockout / progressive delay on repeated failures
- [ ] **C4** Server-side logout that bumps `credentials_changed_at`; enforce the claim in `JwtAuthenticationFilter`
- [ ] **L5** Request-size limits
- [ ] Load-test login specifically, confirming BCrypt cannot exhaust the thread pool

### Block 4 — Operational safety *(do not launch without these)*

- [ ] **H10** Automated `pg_dump`, off-host retention, **and one rehearsed restore**
- [ ] **M6** Structured JSON logging with a correlation ID; rotation configured
- [ ] Prometheus scraping the existing meters; alert on 5xx rate and `ReferenceReconciliationStale`
- [ ] TLS certificate with auto-renewal; verify HSTS actually appears after H2
- [ ] Verify the previous jar starts against the current schema — the rollback path
- [ ] Document the incident runbook: rotate `JWT_SECRET`, revoke a session, restore a backup

### Block 5 — Product-truth gaps *(decide before launch, fix soon after)*

- [ ] **M11** Decide: ship with `is_email_verified` universally true, or complete the verification flow first
- [ ] **M12** Password reset — currently no recovery path for any locked-out user
- [ ] **H4** Move CSP from Report-Only to enforcing
- [ ] **H3** `httpOnly` cookie via a Next route-handler proxy
- [ ] **M1** Trigram index for lawyer keyword search, or move to full-text
- [ ] **M3**/**M4** Compression; cache headers on public GETs
- [ ] **L7** `SECURITY.md` with a disclosure contact
- [ ] **L8** Dependabot and a dependency-scanning step in CI

---

## Suggested launch gate

**Blocks 1, 2 and 4 are non-negotiable.** Block 3 without at least C3 means the
login endpoint is both a credential oracle and a DoS lever, so I would not treat
it as optional either.

Block 5 is a judgement call. The one item there I would not defer quietly is
**M11** — shipping with every account marked email-verified when none has been
verified is a claim the product is making to its users that is not true.
