# Implementation Roadmap — Email Verification & Password Reset

**Design of record:** [`IDENTITY-TDD.md`](IDENTITY-TDD.md) — frozen 2026-07-31.
This document is *how*, not *what*. Where the two disagree, the TDD wins and
this document is wrong.

**Nine phases.** Each one compiles, passes the full suite, and is a single
commit. No phase leaves the application in a state where a rollback needs a
database rollback.

---

## Phase index

| # | Phase | Risk | Reviewable alone | Depends on | Est. |
|---|---|---|---|---|---|
| **0** | Rename + login normalisation fix | Very low | **Yes** | — | S |
| **1** | V7 migration + configuration properties | Low | **Yes** | 0 | S |
| **2** | Token core (`identity` package) | Low | **Yes** | 1 | M |
| **3** | Email infrastructure (`email` package) | Low | **Yes** | — | M |
| **4** | Verification flow | Medium | No | 2, 3 | L |
| **5** | `credentials_changed_at` + JWT `cca` | **Highest** | **Yes** | 1 | M |
| **6** | Password reset flow | Medium | No | 2, 3, 5 | M |
| **7** | Rate limiting | Medium | **Yes** | — | M |
| **8** | Frontend + login gate + cut-over | Medium | No | 4, 6, 7 | L |

### Dependency graph

```
0 ──► 1 ──┬──► 2 ──┬──► 4 ──┐
          │        │        │
          └──► 5 ──┴──► 6 ──┼──► 8
                            │
     3 ─────────────────────┤
     7 ─────────────────────┘
```

Phases **3** and **7** have no dependency on the migration and can be built in
parallel with 1–2 by a second person. Phase **5** only needs the column from
Phase 1, so it does not have to wait for the token work.

### Merge-conflict surface

Ordered deliberately so the files that everything touches are stabilised first.

| File | Touched by | Mitigation |
|---|---|---|
| `AuthServiceImpl` | 0, 4, 6, 7, 8 | **Highest-contention file.** Phase 0 lands its refactor before anything else needs to edit it |
| `SecurityConfig` | 4, 6, 7 | Each phase appends one matcher line; keep them on separate lines, never a shared array literal |
| `application.yaml` | 1, 3, 5, 7 | Phase 1 creates the whole `vakilconnect.identity` block with every key, so later phases only fill in values |
| `.env.example` | 1, 3 | Phase 1 adds all identity keys; Phase 3 adds only the email block |
| `JwtService` / `JwtAuthenticationFilter` | 5 only | Isolated on purpose — Phase 5 owns the auth path exclusively |
| `User.java` | 0, 1 | Phase 0 renames, Phase 1 adds one field. Never concurrent |

---

## Phase 0 — Rename `enabled` → `emailVerified`, fix login normalisation

**Reviewable independently. Ship this before anything else, including before
the design is fully agreed.**

### Objective

Remove the `isEnabled()` naming collision and fix a live login bug. No new
behaviour, no new dependency, no migration.

### Why it is separate

`JwtAuthenticationFilter:72` calls `userDetails.isEnabled()` — Spring's
`UserDetails`, backed by `active`. `User.isEnabled()` is backed by
`is_email_verified`. Two identically-named methods with opposite meanings in
one request path. Building five phases on top of that ambiguity is how a
production lockout happens.

### Files to modify

| File | Change |
|---|---|
| `user/entity/User.java` | Field `enabled` → `emailVerified`; `isEnabled()`/`setEnabled()` → `isEmailVerified()`/`setEmailVerified()`. `@Column(name = "is_email_verified")` unchanged |
| `config/AdminBootstrapRunner.java` | `setEnabled(true)` → `setEmailVerified(true)` (line 71) |
| `auth/service/AuthServiceImpl.java` | `login()`: normalise the email **before** `authenticationManager.authenticate()` |
| `test/support/AbstractIntegrationTest.java` | `setEnabled(true)` → `setEmailVerified(true)` (line 195) |

### The login bug

`AuthServiceImpl:131-139` passes `request.getEmail()` raw to `authenticate()`,
then uses `request.getEmail().trim().toLowerCase()` for `findByEmail`.
`User.setEmail()` lowercases on write, so the database only ever holds
lowercase. A user typing `Foo@Bar.com` fails authentication before the
normalised lookup is ever reached.

Extract the normalisation to one place and use it for both.

### Flyway / APIs / entities / frontend

None. None. `User` only. None — verified by grep: `enabled` appears in no DTO,
no admin response, and no frontend type, so this is invisible outside the JVM.

### Tests

- `AuthControllerIT`: new case — register as `user@x.com`, log in as
  `USER@X.com`, expect 200. **Fails before this phase.**
- Existing 224 tests must pass unchanged. No test should need editing beyond
  the two `setEnabled` call sites.

### Acceptance criteria

- [ ] Zero occurrences of `setEnabled`/`isEnabled` on `User` (`grep`)
- [ ] `userDetails.isEnabled()` in `JwtAuthenticationFilter` is the only
      `isEnabled` left, and its comment says it means `active`
- [ ] Mixed-case login test passes
- [ ] 225 tests green
- [ ] `git diff` shows no change to any `.sql`, DTO, or frontend file

### Rollback

`git revert`. No schema, no config, no API surface.

---

## Phase 1 — V7 migration and configuration

**Reviewable independently.**

### Objective

Land the schema and every configuration key the feature will need. Nothing
reads either yet.

### Why schema before code

The migration is the only irreversible step in the feature. Landing it alone
means it can be applied, verified against a restored production snapshot, and
left running for days before any code depends on it. It also means Phases 2 and
5 do not each carry a migration.

### Files to modify

| File | Change |
|---|---|
| `db/migration/V7__email_verification_and_password_reset.sql` | **New.** TDD §6 verbatim |
| `user/entity/User.java` | Add `credentialsChangedAt` (`Instant`), mapped to the new column |
| `identity/config/IdentityProperties.java` | **New.** `@ConfigurationProperties("vakilconnect.identity")` |
| `resources/application.yaml` | Full `vakilconnect.identity` block, all keys, defaults from TDD §19 |
| `.env.example` | All identity variables, annotated in the existing style |

### Flyway

V7, additive only:

1. `ALTER TABLE users ADD COLUMN credentials_changed_at timestamptz NOT NULL DEFAULT now()`
2. `UPDATE users SET is_email_verified = true WHERE is_email_verified = false` — grandfathers pre-policy accounts
3. `CREATE TABLE email_tokens` + four indexes (TDD §5.2)

`TOKEN_PEPPER` is declared in config but **not yet required** — it becomes
fail-fast in Phase 2, when something actually uses it. Making it required here
would break every developer's app before the feature exists.

### APIs / frontend

None. None.

### Entities

`User` gains one field. `EmailToken` does **not** exist yet — the table is
present with no entity mapped to it, which Hibernate `validate` permits.

### Tests

- `V7MigrationIT` (new): `email_tokens` exists with the expected columns;
  `uq_email_tokens_live` exists and is partial; the CHECK constraint rejects a
  row with both `used_at` and `invalidated_at`; every pre-existing user reads
  `is_email_verified = true`.
- Existing suite green — proves `validate` tolerates the unmapped table.

### Acceptance criteria

- [ ] `./mvnw clean test` green on a fresh container
- [ ] Applying V7 to a V6 database restored from a production snapshot succeeds
- [ ] **The pre-V7 jar starts against the V7 schema** — this is the property
      that makes every later rollback cheap. Verify by hand, once.
- [ ] `flyway_schema_history` shows V7 success
- [ ] Inserting two live tokens for one `(user_id, type)` raises a constraint
      violation

### Rollback

Revert the code; **leave V7 applied**. The previous jar runs against it
unmodified (Hibernate `validate` ignores extra tables and extra columns). There
is deliberately no `V8__undo`.

If V7 itself fails mid-flight, Postgres DDL is transactional — the schema is
untouched and `flyway_schema_history` records the failure.

---

## Phase 2 — Token core

**Reviewable independently.**

### Objective

The `identity` package: issue, consume, invalidate, purge. No HTTP, no email.

### Files to modify

| File | Change |
|---|---|
| `identity/entity/EmailToken.java`, `EmailTokenType.java` | **New** |
| `identity/repository/EmailTokenRepository.java` | **New.** Includes the atomic-consume `@Modifying` query |
| `identity/service/TokenHasher.java` | **New.** `SecureRandom` 32 bytes, Base64-URL, HMAC-SHA256 with `TOKEN_PEPPER` |
| `identity/service/VerificationTokenService.java` | **New.** `issue` · `consume` · `invalidateAll` · `lastRequestedAt` · `purgeExpired` |
| `common/exception/` | New typed exceptions for `TOKEN_INVALID` / `TOKEN_EXPIRED` / `TOKEN_ALREADY_USED` |
| `common/exception/ErrorResponse.java` | Add nullable `code` |
| `common/exception/GlobalExceptionHandler.java` | Map the new exceptions |
| `VakilconnectApplication.java` | `@EnableScheduling` |

### Flyway

None — Phase 1 shipped the schema.

### APIs

None yet. `ErrorResponse` gains a nullable `code`, which is **not** a contract
change: Jackson omits null fields, so every existing error body is
byte-identical. This must be proven by test, not assumed.

### Entities / services

`EmailToken` is new. `VerificationTokenService` is the shared primitive both
flows will call — it must not know that email exists.

### Frontend

None.

### Tests

- `TokenConcurrencyIT` (new) — **the most important test in the feature.** Two
  threads, one `CountDownLatch`, one token: exactly one success, one
  already-used. Non-negotiable and never merged into another class.
- `EmailVerificationIT` (new, partial) — issue/consume/invalidate/expire at the
  service level; `uq_email_tokens_live` violation surfaces as a typed exception
  rather than a 500; purge deletes only terminal rows.
- `TokenHasherTest` (unit) — determinism; different peppers give different
  hashes; 64 hex chars out.
- **C10 guard** — assert no update path writes `users.email`.
- **Compatibility** — every existing error response serialises without a `code`
  key.

### Acceptance criteria

- [ ] `TokenConcurrencyIT` passes 20 consecutive runs (race conditions do not
      fail reliably; one green run proves nothing)
- [ ] Consume is a single conditional `UPDATE` — no read-then-write anywhere in
      the diff
- [ ] Raw tokens appear in no `toString()`, no log statement, no entity field
- [ ] App fails to start without `TOKEN_PEPPER`
- [ ] Existing error bodies unchanged

### Rollback

`git revert`. Table stays; unmapped tables are inert.

---

## Phase 3 — Email infrastructure

**Reviewable independently. No dependency on Phases 1–2 — parallelisable.**

### Objective

Send email asynchronously, after commit, with retry and metrics. Nothing calls
it yet.

### Files to modify

| File | Change |
|---|---|
| `email/EmailService.java`, `EmailMessage.java` | **New** |
| `email/EmailDispatchListener.java` | **New.** `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` |
| `email/ResendEmailSender.java` | **New.** `RestClient`; `@Profile("!dev & !test")` |
| `email/LoggingEmailSender.java` | **New.** `@Profile("dev")` — prints the full link |
| `email/EmailProperties.java` | **New** |
| `config/AsyncConfig.java` | **New.** `@EnableAsync`; executor core 2 / max 5 / queue 100 / **`AbortPolicy`** (D10) |
| `identity/metrics/IdentityMetrics.java` | **New.** `MeterBinder`, mirrors `ReferenceMigrationMetrics` |
| `pom.xml` | `spring-retry`, `spring-boot-starter-aop` |
| `application.yaml`, `.env.example` | Email block |

### Flyway / APIs / entities / frontend

None. None. None. None.

### Tests

- `EmailDispatchIT` content (lands inside `PasswordResetIT` per C11, or
  temporarily standalone and merged in Phase 6): listener fires **only** after
  commit; a rolled-back transaction sends nothing; retry exhaustion increments
  `email_send_total{outcome="failure"}`; a full queue increments
  `{outcome="rejected"}`.
- `RecordingEmailSender` test double — the only legitimate place a raw token is
  readable after issuance.
- Test profile binds a **synchronous** executor. Without this the assertions are
  flaky in CI and pass locally, which is the worst failure mode available.

### Acceptance criteria

- [ ] A rolled-back transaction publishes an event and sends **nothing**
- [ ] `dev` profile prints a working link; `ResendEmailSender` is not
      instantiable in `dev` or `test`
- [ ] Executor is bounded — no `SimpleAsyncTaskExecutor` anywhere
- [ ] Rejected tasks are counted, never silently dropped
- [ ] Four meters visible on `/actuator/prometheus`

### Rollback

`git revert`. Nothing calls it, so removal is inert.

---

## Phase 4 — Verification flow

Not independently reviewable — needs 2 and 3.

### Objective

Registration issues a token and sends an email; users can verify and resend.
**Login is not yet gated.**

### Files to modify

| File | Change |
|---|---|
| `identity/service/EmailVerificationService.java` | **New.** `verify` · `resend` · cooldown |
| `identity/event/EmailVerificationRequestedEvent.java` | **New.** Redacting `toString()` |
| `auth/service/AuthServiceImpl.java` | `register()` issues a token, publishes the event, implements 7-day takeover (D8) |
| `auth/controller/AuthController.java` | `+ POST /verify-email`, `+ POST /resend-verification` |
| `auth/dto/` | `VerifyEmailRequest`, `ResendVerificationRequest`, `VerificationResponse`, `AcknowledgementResponse` |
| `config/SecurityConfig.java` | Two `permitAll` matchers |
| `identity/service/UnverifiedAccountPurgeJob.java` | **New.** `@Scheduled`, disableable |

### APIs added

| Method | Path | Success | Notes |
|---|---|---|---|
| POST | `/api/auth/verify-email` | 200 | `{token}` in the body — never a GET (TDD §10) |
| POST | `/api/auth/resend-verification` | 202 | Constant response regardless of outcome |

`POST /api/auth/register` is unchanged in shape; only the `message` string
mentions the email.

### Flyway / entities

**None.** Phase 1 shipped the schema and Phase 2 the entity. This phase is
service and controller code only — deliberately, so the highest-logic phase
carries no migration risk.

### Takeover rules (D8)

Re-registration claims an existing row only when **all** hold: not verified;
`created_at` older than `TAKEOVER_THRESHOLD` (7 days); role `CLIENT`; no
dependent rows. `LAWYER` is excluded — a `lawyer_profiles` row with a unique
`bar_council_number` already exists.

### Frontend

None yet. Deliberate: the API is proven with `curl` and the `dev` sender before
any UI is built on it.

### Tests

- `EmailVerificationIT` — completed from Phase 2: full HTTP happy path;
  expired → 410; reused → 409; unknown → 400; wrong token type at the wrong
  endpoint → 400.
- `SquatTakeoverIT` — succeeds past 7 days; refused for verified, for an
  account inside the window, and for a LAWYER.
- Resend cooldown returns 429 with `Retry-After`, and **survives a context
  restart** (the durability property from §8.4).

### Acceptance criteria

- [ ] Register → email in the `dev` console → POST the token → verified
- [ ] Login still succeeds for unverified users (gate is Phase 8)
- [ ] Resend twice inside 60 s → second is 429
- [ ] Unknown email to resend → 202, identical body to the success case
- [ ] Takeover refused at 6 days, allowed at 8

### Rollback

`git revert` restores registration exactly. Issued tokens become orphans and
expire on their own.

---

## Phase 5 — `credentials_changed_at` and the `cca` claim

**Reviewable independently — and it must be reviewed on its own.**
**Highest risk in the feature.**

### Objective

Make a credential change invalidate every outstanding JWT.

### Why it is isolated

This is the only phase that touches the filter running on **every
authenticated request**. A mistake here does not degrade a feature — it logs
out or fails to log out every user. It gets its own commit, its own review, and
its own verification pass.

### Files to modify

| File | Change |
|---|---|
| `security/jwt/AuthenticatedUser.java` | **New** `UserDetails` carrying `credentialsChangedAt` — Spring's builder cannot |
| `security/jwt/CustomUserDetailsService.java` | Return `AuthenticatedUser`; keep `.disabled(!active)` semantics **exactly** as-is |
| `security/jwt/JwtService.java` | Emit `cca` (epoch **millis**); accessor for it |
| `security/jwt/JwtAuthenticationFilter.java` | Reject when `cca` is absent **or** `< credentialsChangedAt` |
| `auth/service/AuthServiceImpl.java` | Pass `credentialsChangedAt` into `generateToken` |

### The two failure modes to review for

**Missing claim must be treated as stale.** Every JWT issued before this deploy
has no `cca`. Admitting them leaves a 24-hour window where the mechanism does
nothing — for exactly the sessions most likely to be compromised. Consequence:
a one-time sign-out of all users at deploy (TDD §18.3). Intentional, and it
must be announced.

**Millisecond precision, and `<` not `<=`.** Second granularity would let a
token minted in the same second as a reset survive. D9 (no auto-login after
reset) removes the only realistic path to a tie.

### Flyway / entities / APIs / frontend

**No Flyway** — Phase 1 added the column and mapped it. **No entity change** —
`AuthenticatedUser` is a `UserDetails` adapter, not a JPA entity. **No contract
change.** **No frontend work:** the client already redirects to login on 401,
so the one-time sign-out needs no UI at all.

### Tests

- `PasswordResetIT` (partial): issue a JWT, bump `credentials_changed_at`
  directly, assert the next request is 401.
- A JWT **without** a `cca` claim is rejected.
- A JWT issued *after* the bump is accepted.
- **Full existing suite green** — this is the real acceptance test. 224 tests
  exercise the authenticated path; if the filter is wrong they fail loudly.

### Acceptance criteria

- [ ] Full suite green, no test modified to accommodate the change
- [ ] `SecurityAuthorizationIT` passes untouched
- [ ] Login → request → bump → same request now 401
- [ ] Missing-claim token rejected
- [ ] No extra query per request (the filter already loads the user —
      confirm with `show-sql` that the count is unchanged)

### Rollback

`git revert`. The column stays and is simply unread. **Reverting re-admits
tokens issued while it was live** — acceptable, since those are legitimate
sessions.

---

## Phase 6 — Password reset flow

Not independently reviewable — needs 2, 3 and 5.

### Objective

Forgot-password and reset-password, using the Phase 2 primitive and the Phase 5
invalidation.

### Files to modify

| File | Change |
|---|---|
| `identity/service/PasswordResetService.java` | **New.** `requestReset` · `resetPassword` |
| `identity/event/PasswordResetRequestedEvent.java`, `PasswordChangedEvent.java` | **New** |
| `auth/controller/AuthController.java` | `+ POST /forgot-password`, `+ POST /reset-password` |
| `auth/dto/` | `ForgotPasswordRequest`, `ResetPasswordRequest`, `PasswordResetResponse` |
| `auth/dto/RegisterRequest.java` | Extract the password constraint so reset and register cannot drift |
| `config/SecurityConfig.java` | Two `permitAll` matchers |

### APIs added

| Method | Path | Success | Notes |
|---|---|---|---|
| POST | `/api/auth/forgot-password` | 202 | Constant response. **No artificial delay** (D12) |
| POST | `/api/auth/reset-password` | 200 | No JWT issued (D9) |

### Flyway / entities

**None.** `credentials_changed_at` came in Phase 1 and is mapped in Phase 5;
`EmailToken` came in Phase 2. This phase writes to both but adds neither.

### The one transaction

`resetPassword` does all of this or none of it: consume the token; set the new
hash; set `credentials_changed_at = now()`; set `emailVerified = true`;
invalidate **all** live tokens of **both** types for that user.

`emailVerified = true` because reaching a reset link proves mailbox control just
as well as a verification link does — and without it an unverified user who
resets is stranded in a loop.

### Frontend

None yet — Phase 8.

### Tests

- `PasswordResetIT` — completed: happy path; expired; reused; **a JWT issued
  before the reset is rejected after it**; reset sets `emailVerified`; all
  forgot-password branches return byte-identical bodies.
- Merged `EmailDispatchIT` content lands here (C11).

### Acceptance criteria

- [ ] Forgot → email → reset → old password fails, new works
- [ ] A JWT captured before the reset returns 401 after it — **the point of the
      feature**
- [ ] Unknown email → 202, byte-identical to the success body
- [ ] Password rules identical between register and reset (one shared constant)
- [ ] Reset issues no JWT

### Rollback

`git revert`. Outstanding reset tokens expire in 30 minutes.

---

## Phase 7 — Rate limiting

**Reviewable independently. No dependency on 1–6 — parallelisable.**

### Objective

Per-IP limits in a filter, per-email limits in the services (D11).

### Files to modify

| File | Change |
|---|---|
| `security/ratelimit/RateLimitFilter.java` | **New.** `OncePerRequestFilter`, per-IP only, before `JwtAuthenticationFilter` |
| `security/ratelimit/RateLimitService.java` | **New.** Bucket4j `ProxyManager` over Caffeine; shared by filter and services |
| `common/exception/` | `RateLimitedException` + handler mapping to 429 + `Retry-After` |
| `identity/service/*`, `auth/service/AuthServiceImpl.java` | Per-email checks at the points listed in TDD §15.2 |
| `application.yaml` | `server.forward-headers-strategy: framework` |
| `pom.xml` | `bucket4j-core` |

### The prerequisite that is easy to miss

`server.forward-headers-strategy: framework` is **not currently set**. Without
it, behind the reverse proxy in `DEPLOYMENT.md` every request reports the
proxy's IP, so the per-IP limit becomes a global limit and the first abuser
locks out everyone. This is correctness, not tuning.

Corollary for the deployment: the proxy must **overwrite** `X-Forwarded-For`,
not append. Otherwise per-IP limits are bypassed by spoofing the header.

### Flyway / APIs / entities / frontend

**No Flyway** — buckets are in-memory by design (D6), so nothing is persisted.
No new endpoints; existing ones can now return 429. No entities. Frontend
handling of 429 lands in Phase 8.

### Tests

- `RateLimitIT` — filter exhaustion → 429 + `Retry-After`; service exhaustion →
  429 via `GlobalExceptionHandler`; the two dimensions are independent;
  **case-variant emails share one bucket** (the bypass D11 closes).
- Limiter must not break request-body reading — the regression `ProxyManager`
  in the service layer exists to avoid, tested explicitly.

### Acceptance criteria

- [ ] Exceeding each documented limit returns 429 with a correct `Retry-After`
- [ ] `Foo@Bar.com` and `foo@bar.com` share a bucket
- [ ] `X-Forwarded-For` is honoured
- [ ] Full suite green — **no existing test trips a limit.** If one does, the
      limit is too low for realistic use
- [ ] Buckets are evicted (no unbounded memory growth)

### Rollback

`git revert`. Limits vanish; nothing else changes.

---

## Phase 8 — Frontend, login gate, cut-over

Not independently reviewable — needs 4, 6 and 7.

### Objective

The user-facing surface, then close the gate.

### Files to modify

**Backend**

| File | Change |
|---|---|
| `auth/service/AuthServiceImpl.java` | `login()` checks `emailVerified` **after** `authenticate()`, behind `verification.enforced` |
| `application.yaml` | `enforced` default `false` |

**Frontend**

| File | Change |
|---|---|
| `app/(public)/verify-email/page.tsx` | **New.** Read `?token`, `history.replaceState` it away, POST, render outcome |
| `app/(public)/forgot-password/page.tsx` | **New** |
| `app/(public)/reset-password/page.tsx` | **New** |
| `features/auth/api/identity-api.ts` | **New.** Four calls |
| `features/auth/components/unverified-notice.tsx` | **New.** Shown on `EMAIL_NOT_VERIFIED` |
| `features/auth/components/resend-verification-button.tsx` | **New.** Countdown from `Retry-After` |
| `features/auth/schemas/` | Zod schemas |
| `lib/routes.ts` | Three new routes |
| `middleware.ts` | Allow the three public paths |
| `app/(public)/*/loading.tsx`, `error.tsx` | Match the Phase D convention |

### Flyway / entities / APIs

**No Flyway. No entities. No new endpoints.** Phase 8 is the frontend plus a
single conditional in `login()`. Every backend surface it consumes already
exists and has been proven by `curl` in Phases 4 and 6 — which is why the
frontend was deliberately held back until now.

### Frontend detail that matters

`TOKEN_ALREADY_USED` (409) **renders as success** when the account is already
verified. The user's mental model is "did it work", not "was mine the first
click" — and double-clicking a link is the normal case, not an error (R4).

### Tests

- `LoginGateIT` — unverified → 403 `EMAIL_NOT_VERIFIED`; inactive → 401
  `ACCOUNT_DISABLED`; **both distinguishable**; verified + active → 200; gate
  inert when `enforced: false`.
- Vitest (pure logic only, per Phase F convention): token extraction from the
  query string including missing/malformed; `Retry-After` → countdown;
  error-code → UI-state mapping including the R4 case.

### Acceptance criteria

- [ ] End-to-end in a browser: register → email → verify → login
- [ ] End-to-end: forgot → email → reset → login with the new password
- [ ] Unverified login shows the notice and a working resend button
- [ ] Token is gone from the URL bar after the page loads
- [ ] `enforced: false` → unverified users still log in
- [ ] `enforced: true` → they do not
- [ ] `npm run build` clean; `npm test` green

### Rollback

Set `EMAIL_VERIFICATION_ENFORCED=false` — configuration, no deploy, seconds.
That is the entire point of the flag. Reverting the frontend is a separate,
independent action.

---

## Cut-over, after Phase 8

Per TDD §18.1 — this is an operational sequence, not a phase:

1. Deploy with `enforced: false`.
2. Watch `email_send_total{outcome}` for 48 h. Delivery must be near-100%
   before anything is gated on it.
3. Watch `token_consumed_total{outcome="ok"}` — proves the whole loop, not just
   the send.
4. **Second grandfather pass — do not skip.** V7 backfilled everyone who
   existed when it ran. Anyone who registered between the V7 deploy and the
   Phase 4 deploy has `email_verified = false` and **never received a
   verification email**, because nothing sent one yet. Flipping the gate would
   lock them out of an account they have no idea is unverified.

   Before step 5, verify no such cohort remains:

   ```sql
   SELECT count(*) FROM users
   WHERE is_email_verified = false
     AND created_at < '<the Phase 4 deploy timestamp>';
   ```

   If it is non-zero, either mark those rows verified — they predate the policy
   exactly as the V7 backfill's population did — or send them a verification
   email and wait. Do not flip the gate with the count above zero.

   This is a checklist step rather than a migration because the cutoff is a
   deploy timestamp, which no migration authored in advance can know.
5. Flip `enforced: true`.
6. Watch support volume. Flipping back takes seconds.

**Pre-launch, and not automatable:** SPF, DKIM and DMARC verified on the
sending domain. This is the single most common cause of "verification is
broken", and no test in this roadmap can catch it.

---

## Recommended first phase

### Phase 0. Start here, and consider shipping it independently of this feature.

**It is not really part of the feature.** Phase 0 contains no tokens, no email,
no migration, no new dependency, and no new endpoint. It is a rename plus a
one-line normalisation fix. It could merge today and be justified on its own
merits even if email verification were cancelled tomorrow.

**It removes a live bug.** `AuthServiceImpl` passes the raw email to
`authenticate()` while the database stores only lowercase, so
`Foo@Bar.com` cannot log in **right now**. That is a real user-facing defect
sitting in the RC, independent of anything designed here.

**It defuses the ambiguity everything else is built on.** `userDetails.isEnabled()`
means `active`; `user.isEnabled()` means `is_email_verified`. Phase 5 rewrites
that filter, and Phase 8 adds a second account-state check beside the first. If
the rename lands after those, every reviewer of Phases 5 and 8 has to hold two
contradictory meanings of `isEnabled` in their head while reviewing the most
security-sensitive code in the application. That is exactly when a mistake gets
approved.

**It is the highest-contention file, stabilised first.** `AuthServiceImpl` is
touched by Phases 0, 4, 6, 7 and 8. Landing the mechanical refactor before any
behavioural change means later diffs are pure additions and every subsequent
review reads cleanly. Doing the rename last would mean a rename diff tangled
with five phases of new logic.

**Its risk is as close to zero as a change gets.** Verified by grep: `enabled`
appears in no DTO, no admin response, and no frontend type. Three call sites
total, all internal. The compiler catches any miss — there is no reflective or
string-based access to the field. The existing 224 tests are the safety net,
and exactly two of them need editing.

The ordering principle for everything after: **schema before code, primitives
before flows, and the auth filter alone in its own commit.**
