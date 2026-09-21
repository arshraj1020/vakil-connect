# Deployment

Running VakilConnect locally, and what has to be true before it runs in
production.

**Status:** a Dockerfile per service, a root `docker-compose.yml`, and a GitHub
Actions pipeline running both suites on every push now exist. The manual
procedure below still works and is the one to follow for local development;
`docker compose up --build` is the fastest way to run the whole stack without
installing Postgres, Node or a JDK by hand. See the
[Release audit](#release-status) for what is still open.

---

## Contents

- [Prerequisites](#prerequisites)
- [Local development](#local-development)
- [Configuration reference](#configuration-reference)
- [Running the tests](#running-the-tests)
- [Production deployment](#production-deployment)
- [Observability](#observability)
- [Troubleshooting](#troubleshooting)
- [Release status](#release-status)

---

## Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| JDK | 21+ | Backend. The build targets release 21 |
| PostgreSQL | 16 | Both. The schema uses `gen_random_uuid()` and `pg_trgm` |
| Node.js | 20+ | Frontend (Next 15, React 19) |
| Docker | any recent | **Backend tests only** — Testcontainers starts a real Postgres |

Docker is not required to *run* the app, only to test it.

---

## Local development

### Quickest path: Docker Compose

```bash
cp backend/.env.example backend/.env        # then set JWT_SECRET at minimum
cp frontend/.env.example frontend/.env.local
docker compose up --build
```

Backend on `http://localhost:8080`, frontend on `http://localhost:3000`,
Postgres (pgvector) on `5432`. This is for local development and demos, not a
production manifest — no TLS termination, no orchestration, no secret
management beyond the `.env` files above.

The sections below are the manual, no-Docker path — useful for running the
backend or frontend on their own, or for understanding what the Compose file
is automating.

### 1. Database

```bash
createdb vakilconnect
```

That is the whole step. **Do not create tables by hand.** Flyway owns the
schema and applies every migration in `backend/src/main/resources/db/migration`
on first startup. (`database/schema.sql`, a historical pre-Flyway artefact
that no longer matched the migrations, has been removed.)

### 2. Backend

```bash
cd backend
cp .env.example .env
```

Edit `.env`. At minimum set `JWT_SECRET` — the application **will not start
without it**, deliberately, so that no committed fallback secret can reach
production:

```bash
openssl rand -base64 32
```

Also set `DB_USERNAME` / `DB_PASSWORD` to match your local Postgres, and
`ADMIN_PASSWORD` if you want the bootstrap admin account.

Spring Boot does not read `.env` files natively, so export them:

```bash
set -a && source .env && set +a
./mvnw spring-boot:run
```

The API is on `http://localhost:8080`, Swagger UI on
`http://localhost:8080/swagger-ui.html`, and actuator on `http://localhost:9091`.

On first start Flyway applies every migration in `db/migration` (V1–V9) and
`AdminBootstrapRunner` creates
an ADMIN account if `ADMIN_EMAIL` does not already exist. Admin accounts cannot
be created through the public API, so this is the only route to the first one.

### 3. Frontend

```bash
cd frontend
cp .env.example .env.local     # default points at localhost:8080
npm install
npm run dev
```

`http://localhost:3000`.

`NEXT_PUBLIC_API_BASE_URL` must match an origin in the backend's
`APP_CORS_ALLOWED_ORIGINS`, and it is baked into the Content-Security-Policy
`connect-src` at build time. A mismatch appears as a CORS error while the API
itself looks perfectly healthy.

### Everyday commands

```bash
# backend
./mvnw spring-boot:run          # run
./mvnw clean test               # 837 tests (needs Docker for Testcontainers)
./mvnw clean package            # build the jar

# frontend
npm run dev                     # dev server
npm run build                   # production build (fails on type or lint errors)
npm test                        # 81 unit tests
npm run typecheck               # tsc --noEmit
npm run lint                    # next lint
```

---

## Configuration reference

Full annotated list: [`backend/.env.example`](backend/.env.example).

### Backend

| Variable | Default | Notes |
|---|---|---|
| `JWT_SECRET` | **none** | **Required.** No default by design; app fails to start |
| `DB_URL` | `jdbc:postgresql://localhost:5432/vakilconnect` | |
| `DB_USERNAME` | `arshraj` | Change this |
| `DB_PASSWORD` | *(empty)* | |
| `JWT_EXPIRATION` | `86400000` | 24h. No refresh token — this is the whole session |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Exact origin match |
| `ADMIN_EMAIL` | — | Bootstrap admin; idempotent |
| `ADMIN_PASSWORD` | — | |
| `ADMIN_FULL_NAME` / `ADMIN_PHONE_NUMBER` | — | |
| `MANAGEMENT_PORT` | `9091` | Actuator, separate port |
| `MANAGEMENT_ADDRESS` | `127.0.0.1` | **See the production warning below** |
| `MIGRATION_RECONCILIATION_TTL` | `PT5M` | Metrics cache; leave alone |
| `SECURITY_LOG_LEVEL` | `WARN` | Spring Security's own logger. Never run above `WARN` in production |

### Frontend

| Variable | Default | Notes |
|---|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | Build-time; also used for CSP `connect-src` |

---

## Running the tests

**Backend — 837 tests.**

```bash
cd backend && ./mvnw clean test
```

These run against a real PostgreSQL 16 in Docker via Testcontainers, not an
in-memory substitute. Every run applies the actual Flyway migrations with
Hibernate in `ddl-auto: validate`, so the tests exercise the production schema.
The container is started once per JVM and torn down by Ryuk on exit.

Test classes share one database and create data continuously, so no assertion
depends on an absolute row count.

**Frontend — 81 unit tests.**

```bash
cd frontend && npm test
```

Pure logic only: routing and redirect safety, token storage, shared validation,
display formatting. No component or snapshot tests yet.

---

## Production deployment

### Target architecture (Render, or an equivalent PaaS)

This project targets a small, unglamorous split across four pieces. Nothing
below is aspirational — it is what the codebase's own configuration already
assumes (see `application-prod.yaml` and the comments on `StubLlmClient`).

| Component | Where it runs | Notes |
|---|---|---|
| **PostgreSQL 16 + pgvector** | A managed Postgres instance (e.g. Render's Managed PostgreSQL, which supports enabling `pgvector` via `CREATE EXTENSION`) | Flyway (V1–V9) owns the schema; point `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` at it and let the app build the schema on first boot |
| **Spring Boot backend** | A container-based web service, built from `backend/Dockerfile` | Stateless — safe to run one instance behind the platform's own load balancing. Actuator (`MANAGEMENT_PORT`, default `9091`) must not be publicly routable; see the actuator warning below |
| **Next.js frontend** | A container-based web service, built from `frontend/Dockerfile` | `NEXT_PUBLIC_API_BASE_URL` is a **build-time** value baked into the client bundle and the CSP `connect-src` — it must be set as a build argument/environment variable on the platform's build step, not only at runtime. Confirm your platform actually threads a Docker `ARG` through at build time; some PaaS build UIs only expose env vars to the running container, not the build |
| **Ollama / real AI inference** | **Cannot run on Render** — see below | Production runs `StubLlmClient` (the default) and needs no AI environment variables at all |

**Why Ollama specifically does not belong on Render:** Ollama is a local
inference server that expects a persistent, several-gigabyte model file and
benefits heavily from a GPU. As of this writing, Render does not offer GPU
compute at all, so inference would run on CPU — slow, and still requires a
persistent volume for the model weights, which turns a stateless web service
into something closer to a stateful, single-instance node. This is not a
temporary gap to code around; it is a hosting decision that has not been made
and is out of scope for this repository. If real inference is ever wanted
behind the deployed demo, the actual options are: a separate GPU host (a VPS
or GPU cloud provider) that the backend calls over the network as a new
`LlmClient` implementation plus one new `AI_PROVIDER` value, or a hosted
inference API. Either is a deliberate, separate decision — not a Dockerfile
tweak. **Do not deploy Ollama to Render and call it production-ready; it is
not, and the codebase does not pretend otherwise.**

### Before you deploy

- [ ] `JWT_SECRET` generated fresh — never reuse the development value
- [ ] `DB_*` point at the production database, with a non-superuser role
- [ ] `APP_CORS_ALLOWED_ORIGINS` set to the real frontend origin, https (comma-separate for more than one)
- [ ] `ADMIN_PASSWORD` set to something strong; rotate after first login
- [ ] `RESEND_API_KEY` and `EMAIL_FROM` set — `application-prod.yaml` pins the email provider to `resend`, and `ResendEmailSender` refuses to construct without both, so a missing value is a startup failure, not a silent downgrade
- [ ] `MANAGEMENT_ADDRESS` decided (below)
- [ ] `SECURITY_LOG_LEVEL` left at its `WARN` default, or set explicitly if your platform requires it (below)
- [ ] `NEXT_PUBLIC_API_BASE_URL` set **before** `npm run build` — it is inlined

### Build

```bash
cd backend  && ./mvnw clean package -DskipTests   # target/*.jar
cd frontend && npm ci && npm run build            # .next/
```

Skip tests only if they ran in CI. The frontend build fails on type or lint
errors by design (`ignoreBuildErrors: false`).

### Run

```bash
java -jar backend/target/vakilconnect-0.0.1-SNAPSHOT.jar
cd frontend && npm start
```

Put both behind a reverse proxy terminating TLS. The application port serves
the API; the management port must not be routable from outside (below).

### Database migrations

Flyway runs automatically at startup, and `ddl-auto: validate` means a schema
mismatch fails the boot rather than silently corrupting data.

**Never edit an applied migration.** Flyway checksums each file; changing one
fails validation on every database that already ran it. Fix forward with a new
`V7__`.

Migrations are not automatically reversible. Take a backup before deploying a
release that adds one.

### ⚠️ One-time forced sign-out when the `cca` JWT claim ships

**Deploying the credential-change invalidation phase signs out every logged-in
user, once.** Plan for it; do not be surprised by it.

**Why.** JWTs now carry a `cca` claim holding the account's
`credentials_changed_at` as epoch milliseconds, and `JwtAuthenticationFilter`
rejects any token whose claim is older than the stored value. **Every token
issued before this change has no `cca` at all, and an absent claim is treated
as stale.**

Admitting claimless tokens instead was considered and rejected: it would leave
a window of up to `JWT_EXPIRATION` (24 h by default) in which the mechanism
does nothing — for precisely the sessions most likely to be compromised.

**What users see.** One `401`, then the normal redirect to the login page. The
frontend already handles 401 by clearing the session, so no UI change is
needed. Users log in again and continue.

**What to do.**

- Deploy off-peak.
- Tell users in advance if you have a channel for it.
- Expect a burst of `POST /api/auth/login` immediately after the deploy.
- **No database migration.** `credentials_changed_at` has existed since V7.
- **No new environment variables.**
- **Do NOT bulk-update `credentials_changed_at`** as part of the deploy. It is
  unnecessary — absent claims are rejected regardless — and it would destroy
  the audit value of the existing per-user timestamps.

### ⚠️ Rollback caveat — reverting re-admits invalidated sessions

Rolling this phase back is `git revert` with **no database action**: the column
stays and is simply no longer read.

**But the rollback is not security-neutral.** With the check gone, the filter
again accepts any correctly-signed, unexpired token — **including tokens that
were deliberately invalidated while the phase was live**, by a password change
or an account takeover. If you revert *after* responding to a compromise, you
un-revoke the attacker's session.

If you must revert following a credential-related incident, force those
sessions dead another way: deactivate the affected accounts (`users.active =
false`, enforced per request), or rotate `JWT_SECRET`, which invalidates every
token everywhere at the cost of signing out all users again.

### Two settings that are wrong by default for production

**1. The actuator port has no authentication.**

`MANAGEMENT_ADDRESS` defaults to `127.0.0.1`, which is correct for a sidecar
scraper sharing the network namespace. If Prometheus scrapes the pod IP
directly you must set `0.0.0.0` **and** add a NetworkPolicy allowing ingress on
that port only from the monitoring namespace. There is no auth on this port —
the network is the entire access control. Never expose it through a public load
balancer or Ingress.

Only `health`, `info` and `prometheus` are exposed. `env`, `beans`, `heapdump`
and `threaddump` are off, and the exposure list is the security boundary once
the port is reachable — do not widen it.

**2. Spring Security logging level.**

`application.yaml` now defaults `logging.level.org.springframework.security`
to `WARN` (it used to be `DEBUG` unconditionally, relying on
`application-prod.yaml` to override it back to `INFO` - a deployment that
forgot to activate the `prod` profile would have silently logged
authentication internals on every request). Override with `SECURITY_LOG_LEVEL`
if you need more detail while debugging a specific environment:

```bash
SECURITY_LOG_LEVEL=DEBUG
```

### Security posture

Already handled:

- Passwords stored as BCrypt hashes
- JWT validated per request; deactivated and deleted users are rejected with 401
- Role authorisation enforced server-side, not merely hidden in the UI
- Double-booking prevented by a partial unique index, not application logic
- Frontend sets `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`
  and `Permissions-Policy` on every response

Known and deliberate:

- **CSP is Report-Only.** It reports violations without enforcing them, so
  `frame-ancestors` is currently inert and `X-Frame-Options` is the real
  clickjacking defence. Watch the console across a full browse of every role,
  then rename the header to `Content-Security-Policy`.
- **The auth cookie is readable by JavaScript.** `js-cookie` cannot set
  `httpOnly`, so any XSS can read the token. `secure` and `sameSite=lax` are
  set. Moving to `httpOnly` needs a Next route-handler proxy — a real
  architectural change, not a config flag.

---

## Observability

`GET http://<host>:9091/actuator/prometheus`

Beyond the standard JVM, HTTP and Hikari meters, four metrics track the
reference-data migration and gate the Phase 2H cleanup:

| Metric | Meaning |
|---|---|
| `vakilconnect_reference_city_reads_total{source="legacy"}` | City served from the legacy column |
| `vakilconnect_reference_city_reads_total{source="reference"}` | City served from the reference model |
| `vakilconnect_reference_lawyers_missing_primary_city` | Unmigrated lawyers (display axis) |
| `vakilconnect_reference_lawyers_missing_practice_cities` | Unmigrated lawyers (search axis) |
| `vakilconnect_reference_unresolved_cities` | Legacy city strings resolving to nothing |
| `vakilconnect_reference_reconciliation_age_seconds` | How stale the four gauges above are |

Configure this alert before the observation window:

```yaml
- alert: ReferenceReconciliationStale
  expr: vakilconnect_reference_reconciliation_age_seconds > 900
  for: 10m
```

A stale gauge reading zero is indistinguishable from a live one, and a stale
zero is exactly the reading that would wrongly unblock the cleanup.

Full detail: [`backend/docs/MIGRATION-OBSERVABILITY.md`](backend/docs/MIGRATION-OBSERVABILITY.md)
and [`backend/docs/MIGRATION-OPERATIONS.md`](backend/docs/MIGRATION-OPERATIONS.md).

---

## Troubleshooting

**App won't start: `Could not resolve placeholder 'JWT_SECRET'`**
Working as designed. Set it — see local setup.

**App won't start: schema validation failed**
Hibernate runs `ddl-auto: validate`. Either the database was created by hand
(drop it and let Flyway build it), or a migration was edited after being
applied. Check `flyway_schema_history`.

**Flyway checksum mismatch**
An applied migration was modified. Restore the original and fix forward with a
new version. `flyway repair` only rewrites the checksum — it does not reconcile
what the altered SQL actually did.

**CORS errors in the browser, API healthy in curl**
`APP_CORS_ALLOWED_ORIGINS` must match the browser origin exactly, including
scheme and port.

**Backend tests fail immediately**
Testcontainers needs a running Docker daemon.

**Frontend build fails on a type or lint error**
Intentional — `ignoreBuildErrors` and `ignoreDuringBuilds` are both `false`.

**Prometheus target down**
`MANAGEMENT_ADDRESS` is `127.0.0.1` by default, unreachable from another pod.
See the production section.

---

## Release status

Formerly blocking a 1.0 tag, now resolved:

- [x] Dockerfile per service (`backend/Dockerfile`, `frontend/Dockerfile`) and
      a root `docker-compose.yml`
- [x] CI pipeline (`.github/workflows/ci.yml`) running both suites on every
      push and pull request to `main`
- [x] `database/schema.sql` removed — it predated Flyway and no longer matched
      the migrations
- [x] Security logging is `WARN` by default, not `DEBUG`

Deliberate limitations, documented rather than hidden: no payment processing,
no token refresh, manual lawyer verification. See the README for the full
list.
