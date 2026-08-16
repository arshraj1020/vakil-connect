# VakilConnect Backend — Handoff Report

**Generated:** read-only audit of the working tree. No files modified, nothing committed.
**Every claim below is from the current source tree or from `target/surefire-reports/`.**
Where something could not be verified from the repository, it says so explicitly.

---

## 1. Current project state

| Item | Value |
|---|---|
| Branch | `main`, tracking `origin/main` |
| HEAD | `3f0c7b22710811de0d54100f6d05a379435781dd` |
| HEAD subject | `chore(deploy): add Dockerfile for Render deployment` (2026-08-03 23:35 +0530) |
| Working tree | **DIRTY** — 7 modified, 10 untracked (§12) |
| Spring Boot | 3.5.15 (parent POM) |
| Java | 21 (`<java.version>`) |
| Maven | wrapper `apache-maven-3.9.11` |
| Database | PostgreSQL; Flyway `V1`–`V7`, `ddl-auto: validate` |
| Container | `backend/Dockerfile` present. No compose file, no CI pipeline |

### Recent commits relevant to identity

```
3f0c7b2  chore(deploy): add Dockerfile for Render deployment
cbc4530  chore(deploy): add Dockerfile for Render deployment   ← duplicate subject
f49fc04  chore(config): configure production profile for deployment
72b36b3  feat(security): disable Swagger/OpenAPI in production
fc15ce7  feat(identity): complete phase 0 email verification preparation
```

⚠️ **`fc15ce7` is mislabelled.** Its subject says "phase 0" but it contains **Phase 0 AND Phase 1**:
`V7__email_verification_and_password_reset.sql`, `IdentityProperties.java`,
`User.credentialsChangedAt`, and `V7MigrationIT`. Anyone reading commit
subjects alone will mis-model the project state.

### Production deployment status (inferred, not verified)

- HEAD is `3f0c7b2`, which **includes V7**. `User` maps `credentials_changed_at`
  and the app runs with `ddl-auto: validate`, so the application could not boot
  unless V7 had been applied. The live API responding is therefore strong
  evidence that **V7 has run against the production Neon database**.
- **Phase 2 is NOT deployed** — it is entirely uncommitted.
- Render/Neon/Vercel environment variables **cannot be verified from the
  repository.** Every statement about what is configured there is unverified.

---

## 2. Phase status — verified against the source tree, not the roadmap

| Phase | Status | Evidence |
|---|---|---|
| **0** — rename + login normalisation | **DONE, committed** | `User.emailVerified` present; `AuthServiceImpl.normalizeEmail`; commit `fc15ce7` |
| **1** — V7 + config | **DONE, committed** | `V7__email_verification_and_password_reset.sql`; `User.credentialsChangedAt`; `IdentityProperties`; commit `fc15ce7` |
| **2** — token core | **DONE, UNCOMMITTED** | 11 new files + 5 modified, all in the working tree only |
| **3** — email infrastructure | **NOT STARTED** | No `com.arshraj.vakilconnect.email` package exists |
| **4** — verification flow | **NOT STARTED** | Zero matches for `verify-email` / `resend-verification` in `src/main/java` |
| **5** — JWT `cca` claim | **NOT STARTED** | `JwtService` emits only `sub`/`iat`/`exp`; no `cca`; `JwtAuthenticationFilter` unchanged |
| **6** — password reset | **NOT STARTED** | Zero matches for `forgot-password` / `reset-password` |
| **7** — frontend + gate cut-over | **NOT STARTED** | `IdentityProperties.verificationEnforced` exists but **is never read anywhere** |

**Phase 2 is complete in the tree and unshipped. Everything after it is untouched.**

---

## 3. Phase 2 file-by-file audit

### `identity/entity/EmailTokenType.java`
Enum: `VERIFY_EMAIL`, `RESET_PASSWORD`. Persisted as strings and mirrored by the
DB `CHECK` constraint `ck_email_tokens_type`. **Renaming a constant without a
migration breaks every insert.**

### `identity/entity/EmailToken.java`
Maps 8 of the 11 columns. `@Id` = `GenerationType.UUID`. `@ManyToOne(LAZY)` to
`User`. `isLive(Instant)` helper. `toString()` deliberately omits the hash.

- **Does NOT extend `BaseEntity`** — verified by grep. Critical: `BaseEntity`
  declares `updated_at NOT NULL`, which does not exist in `email_tokens`.
- `createdAt` initialised at construction because Hibernate always includes a
  mapped column in the INSERT, so the DB `DEFAULT now()` would never apply.
- **Risk:** none identified. Mapping proven correct by the passing suite (§4).

### `identity/repository/EmailTokenRepository.java`
Four queries.

| Method | Type | Notes |
|---|---|---|
| `consume(hash, type, now)` | `@Modifying` UPDATE, `clearAutomatically=true, flushAutomatically=true` | **The atomicity guarantee.** Returns affected-row count |
| `invalidateLive(userId, type, now)` | `@Modifying` UPDATE, `flushAutomatically=true` only | **`clearAutomatically` deliberately omitted** — clearing would detach the caller's `User` before it is attached to the replacement token |
| `findByTokenHashWithUser(hash)` | `JOIN FETCH` | Initialises the LAZY user inside the transaction |
| `deleteTerminalBefore(cutoff)` | `@Modifying` DELETE | Purge |

### `identity/service/TokenHasher.java`
`SecureRandom` (static, thread-safe) → 32 bytes → Base64-URL unpadded (43 chars).
`hash()` builds a **new `Mac` per call** — `Mac` is stateful and not thread-safe.
Output: `HexFormat.of().formatHex(...)` = 64 lowercase hex chars.
Pepper from `IdentityProperties.tokenPepper()`, no hardcoded fallback.

### `identity/service/VerificationTokenService.java`
`@Transactional` (Spring's, not Jakarta's — verified).

- `issue(User, EmailTokenType)` → invalidate live, generate, hash, save, return raw.
  **Does not catch `DataIntegrityViolationException`** (see K1, §6).
- `consume(String, EmailTokenType)` → conditional UPDATE; row count is the decision;
  `classifyFailure` runs only on a loss.
- `invalidateAll(UUID, EmailTokenType)` → returns affected count. **No caller yet.**
- `purgeExpired()` → deletes terminal rows older than `tokenPurgeRetention`.
- TTL chosen per type via a `switch` over the enum.

### `identity/service/EmailTokenPurgeJob.java`
`@Scheduled(cron = "0 30 3 * * *", zone = "UTC")`, gated by
`@ConditionalOnProperty("vakilconnect.identity.purge-enabled", matchIfMissing=true)`.
Delegates to the service; catches `RuntimeException` and logs at ERROR.

### The three token exceptions
`TokenInvalidException` (400 / `TOKEN_INVALID`), `TokenExpiredException`
(410 / `TOKEN_EXPIRED`), `TokenAlreadyUsedException` (409 / `TOKEN_ALREADY_USED`).
Each exposes a `public static final String CODE`. Messages are fixed at the
exception, never caller-supplied.

### `identity/config/IdentityProperties.java` (modified)
`tokenPepper` gained **`@NotBlank`**. Combined with the record's `@Validated`,
a blank pepper is now a **context-startup failure**.

### `common/exception/ErrorResponse.java` (modified)
New field `String code` with **`@JsonInclude(NON_NULL)` at FIELD level**.
Field-level, not class-level: at class level it would also strip the existing
`"fieldErrors": null` from current responses.

### `common/exception/GlobalExceptionHandler.java` (modified)
Adds a `Logger`, three token handlers, a `DataIntegrityViolationException`
handler (409, code `RESOURCE_CONFLICT`, logged at WARN), and an overloaded
`build(..., String code)`. **The `RuntimeException` fallback now logs at ERROR** —
previously it consumed the exception and logged nothing, making every unexpected
500 invisible.

### `VakilconnectApplication.java` (modified)
Adds `@EnableScheduling`.

### `src/test/resources/application-test.yaml` (modified)
Adds `vakilconnect.identity.token-pepper: test-only-pepper-not-a-real-secret`.
**Mandatory:** without it every integration test fails at context load, because
`application.yaml` resolves the pepper from `${TOKEN_PEPPER:}` (empty).

### `backend/.env.example` (modified)
Comment corrected from "NOT YET REQUIRED" to "REQUIRED". Value stays empty.

---

## 4. Database verification

### `email_tokens` as defined in the applied V7

| Column | Type | Null | Default |
|---|---|---|---|
| `id` | `uuid` | NOT NULL | — |
| `user_id` | `uuid` | NOT NULL | — |
| `type` | `varchar(32)` | NOT NULL | — |
| `token_hash` | `varchar(64)` | NOT NULL | — |
| `expires_at` | `timestamptz` | NOT NULL | — |
| `used_at` | `timestamptz` | NULL | — |
| `invalidated_at` | `timestamptz` | NULL | — |
| `created_at` | `timestamptz` | NOT NULL | `now()` |
| `requested_ip` | `varchar(45)` | NULL | — |
| `requested_user_agent` | `text` | NULL | — |
| `consumed_ip` | `varchar(45)` | NULL | — |

**Constraints:** `pk_email_tokens` (id); `fk_email_tokens_user` → `users(id)`
**ON DELETE CASCADE**; `ck_email_tokens_type` CHECK type IN
(`VERIFY_EMAIL`,`RESET_PASSWORD`); `ck_email_tokens_terminal_state` CHECK
`used_at IS NULL OR invalidated_at IS NULL`.

**Indexes:** `uq_email_tokens_hash` UNIQUE(token_hash);
`uq_email_tokens_live` UNIQUE(user_id,type) **WHERE used_at IS NULL AND
invalidated_at IS NULL**; `ix_email_tokens_user_type_created`
(user_id,type,created_at DESC); `ix_email_tokens_expires` (expires_at) partial.

### Mapping verification

| Check | Result |
|---|---|
| No `BaseEntity` inheritance | ✅ grep-confirmed |
| UUID mapping | ✅ `GenerationType.UUID`, consistent with `BaseEntity` |
| `type` length | ✅ `@Column(length = 32)` — without it, enum defaults to 255 and validate fails |
| `token_hash` length | ✅ `@Column(length = 64)` |
| Timestamps | ✅ all four are `Instant` → `timestamptz` |
| `created_at` handling | ✅ initialised in Java, not reliant on the DB default |
| Unmapped columns | ✅ `requested_ip`, `requested_user_agent`, `consumed_ip` intentionally unmapped; `validate` ignores extra columns |
| `ddl-auto: validate` compatibility | ✅ **empirically proven** — 295 tests ran against real PostgreSQL 16 with `validate`; the context could not have started otherwise |

**Startup-failure risk from the mapping: none identified.** The whole suite
booting against the real migrated schema is direct evidence.

---

## 5. Token security audit

| Property | Finding |
|---|---|
| RNG | `SecureRandom`, static final (thread-safe) |
| Entropy | 32 bytes = **256 bits** |
| Raw format | Base64-URL, unpadded, 43 chars, `^[A-Za-z0-9_-]{43}$` |
| HMAC | `HmacSHA256` via `Mac`, `SecretKeySpec` |
| Pepper source | `IdentityProperties.tokenPepper()` only — **no hardcoded value** (grep-confirmed) |
| Encoding | `HexFormat.formatHex` → 64 lowercase hex |
| Raw token persisted | **No** — no field on the entity, no setter, grep-confirmed |
| Raw token logged | **No** — `log.debug` uses `token.getId()` only |
| `Mac` thread safety | New instance per call ✅ |
| Expiry | Evaluated **in the database** (`expires_at > :now`), so app/DB clock skew cannot extend a token |
| Invalidation | `invalidated_at`, distinct from `used_at` |
| Consumption | Single-use enforced by the conditional UPDATE |

**Weaknesses / notes:**
- Pepper rotation invalidates every outstanding link. Documented, not automated.
- `TokenHasher` reads the pepper once at construction; a runtime change requires a restart. Intended.
- No constant-time comparison — correctly unnecessary: the hash is a unique-index lookup key, not an application-side comparison.
- **Unverified:** whether `TOKEN_PEPPER` in Render has adequate entropy. Not inspectable from the repo.

---

## 6. Concurrency audit

### Consumption

```sql
UPDATE EmailToken t SET t.usedAt = :now
 WHERE t.tokenHash = :tokenHash AND t.type = :type
   AND t.usedAt IS NULL AND t.invalidatedAt IS NULL
   AND t.expiresAt > :now
```

**Truly atomic.** The predicate and the write are one statement, so there is no
window between test and set. Under two concurrent requests PostgreSQL takes a
row-level lock; the second transaction blocks, re-evaluates the predicate after
the first commits, finds `used_at` non-null, and matches **zero rows**. Exactly
one caller sees `1`.

`clearAutomatically = true` prevents the follow-up `findByTokenHashWithUser`
from returning a stale first-level-cache entity that still shows
`usedAt == null`. `flushAutomatically = true` ensures a token issued earlier in
the same transaction is visible. **Stale persistence-context state is not
possible on this path.**

Transaction boundary: `@Transactional` on `VerificationTokenService.consume`,
default READ COMMITTED. Isolation is not load-bearing — the row lock is.

### Issue/replacement race and the K1 decision

`issue()` calls `invalidateLive` then `save`. Two concurrent issues both
invalidate, then both insert; `uq_email_tokens_live` rejects one with
`DataIntegrityViolationException`, which **propagates** and is mapped to 409 in
`GlobalExceptionHandler`.

**Assessment: this is the correct choice.** Catching a constraint violation
inside the same transaction would leave it in an aborted state — every
subsequent statement fails with `current transaction is aborted`. Letting it
propagate keeps the database as the final authority and the service free of
transaction-state bookkeeping.

⚠️ **Unintended side effect, real and worth reviewing.** The handler is global,
so **any previously-uncaught `DataIntegrityViolationException` anywhere in the
application now returns 409 instead of 500.** This is an API behaviour change
beyond Phase 2's nominal surface. Mitigating facts: it is a more accurate
status; `AppointmentServiceImpl` already catches its own violation and throws
`BusinessRuleException`, so double-booking is unaffected; all 295 tests pass
with the change. **Still a deliberate decision the reviewer should confirm.**

---

## 7. Purge job

| Aspect | Finding |
|---|---|
| Scheduling | `@Scheduled(cron = "0 30 3 * * *", zone = "UTC")` — daily 03:30 UTC |
| Enable flag | `@ConditionalOnProperty("vakilconnect.identity.purge-enabled", matchIfMissing = true)` — bean absent when false |
| Retention | `IdentityProperties.tokenPurgeRetention()`, default `P30D` |
| Deletes | Only rows where `used_at < cutoff` **OR** `invalidated_at < cutoff` |
| Live rows | Never deleted, **regardless of age** — asserted by `keepsLive` |
| Batching | **None.** Single bulk `DELETE`. Fine at current volume; a very large backlog would mean one long transaction |
| Transaction | `@Transactional` on `purgeExpired()`; the job itself is not transactional |
| Multi-instance | **Runs on every instance.** Idempotent, so harmless, but wasteful. No leader election |
| Can it delete users? | **No.** The JPQL targets `EmailToken` only, and FK cascade runs `users → email_tokens`, never the reverse. Asserted by `doesNotTouchUsers` |

---

## 8. Configuration

| Key | Where | Notes |
|---|---|---|
| `TOKEN_PEPPER` | `application.yaml` → `${TOKEN_PEPPER:}` | Now `@NotBlank` — **empty = app will not start** |
| `JWT_SECRET` | `application.yaml` → `${JWT_SECRET}` | No default; fail-fast. Correct |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `application.yaml` | ⚠️ `DB_USERNAME` defaults to `arshraj`, `DB_PASSWORD` defaults to **empty** — a missing variable degrades to a guessable connection rather than failing |
| `application-prod.yaml` | present | `show-sql: false`, springdoc off, `org.springframework.security: INFO` |
| `application-test.yaml` | test only | Fixed test pepper, fixed test JWT secret, `reconciliation-ttl: PT0S` |
| Identity block | `application.yaml` | 10 keys under `vakilconnect.identity`, all `${ENV:default}` |

**Secret leakage: none found.**
- `backend/.env` — not on disk, not tracked, **ignored**
- `frontend/.env.local` — on disk, not tracked, **ignored**; contains only a localhost URL
- Tracked `.env.example` files contain **no** non-empty secret values
- ⚠️ `.gitignore` covers `.env` but **not** `.env.local` or `.env.production`
  at root or in `backend/` — verified with `git check-ignore`. One `git add -A`
  from committing a production secret file.

---

## 9. Test results — **real evidence**

Source: `backend/target/surefire-reports/*.xml`, 21 report files.
**The newest report (2026-08-16 23:43:19) is newer than every source and config
file in `src/`**, verified with `find -newer`. The run therefore reflects the
current tree including all Phase 2 code.

### TOTAL: 295 tests · 0 failures · 0 errors · 0 skipped

| Class | Tests | Result |
|---|---|---|
| `TokenConcurrencyIT` | 20 | ✅ **PASS** — all 20 repetitions individually confirmed |
| `EmailTokenLifecycleIT` | 17 | ✅ PASS |
| `TokenHasherTest` | 7 | ✅ PASS |
| `ErrorResponseCompatibilityIT` | 3 | ✅ PASS |
| `V7MigrationIT` | 18 | ✅ PASS |
| `AuthControllerIT` | 13 | ✅ PASS |
| `SecurityAuthorizationIT` | 9 | ✅ PASS |
| `ApiDocsDisabledIT` | 5 | ✅ PASS |
| `AppointmentLifecycleIT` | 42 | ✅ PASS |
| `AppointmentRepositoryIT` | 15 | ✅ PASS |
| `AdminLawyerVerificationIT` | 5 | ✅ PASS |
| `ReferenceApiIT` / `ReferenceDataIT` / `ReferenceBackfillIT` / `ReferenceDualWriteIT` / `ReferenceLinkageIT` / `ReferenceReadCutoverIT` / `ReferenceMigrationMetricsIT` | 124 | ✅ PASS |
| `TextNormalizerTest` / `ReferenceMigrationFreshnessTest` / `VakilconnectApplicationTests` | 17 | ✅ PASS |

`TokenConcurrencyIT` repetitions `[1]`–`[20]`, each 0.078–0.095 s, all passed.
**FAIL: none. NOT TESTED: none in the suite.**

**Not covered by any test:** Phases 3–7 (do not exist); real production
deployment; Render environment configuration.

---

## 10. Warnings

**I could not find any of the warnings asked about.** Searched every
`target/surefire-reports/*.txt` for `sun.misc.Unsafe`, `Mockito`, `ByteBuddy`,
`statistics`, `23505`, and `WARN` — **zero matches**.

Those warnings appear on Maven's console, which is not captured in the
repository, so I cannot assess the specific instances you saw. Generic
assessment, clearly marked as such:

| Warning | Typical meaning |
|---|---|
| `sun.misc.Unsafe` | JDK 21+ deprecation notice from Netty/ByteBuddy/Caffeine. Harmless test noise |
| Mockito dynamic agent | JDK 21+ self-attach deprecation. Harmless; will need `-javaagent` on a future JDK |
| ByteBuddy | Usually "experimental support for JDK N". Harmless |
| Caffeine statistics | Statistics not enabled — `CachingConfig` does not call `recordStats()`. Expected |
| SQLState 23505 in appointment tests | **Expected and correct.** `AppointmentLifecycleIT` deliberately triggers `uq_appointments_active_slot`. Hibernate logs the violation before the service converts it to a 409. Noise, not a failure — those 42 tests pass |

To classify the ones you actually saw, capture `./mvnw clean test > build.log 2>&1`.

---

## 11. Production risk

| Question | Answer |
|---|---|
| Can Phase 2 be safely deployed? | **Yes, with one precondition** — `TOKEN_PEPPER` must already be set in Render |
| Could the application fail to start? | **Yes, one way only:** blank `TOKEN_PEPPER` → `@NotBlank` → context failure. No other startup risk; the mapping is proven by 295 passing tests against real PostgreSQL with `validate` |
| Could existing users be affected? | **No.** Phase 2 never reads or writes `users`. Grandfathering untouched |
| Could existing authentication break? | **No.** `JwtService`, `JwtAuthenticationFilter`, `CustomUserDetailsService`, `SecurityConfig`, `AuthServiceImpl` are all unmodified — verified by `git status` |
| Could data be corrupted? | **No.** No migration, no schema change. Only new rows in `email_tokens`, and nothing calls the writer yet |
| Could tokens be leaked? | **No path identified.** Raw token never persisted, never logged, absent from `toString()` |
| Could the purge job cause damage? | **Low.** Deletes only terminal `email_tokens` rows past retention; cannot reach `users`. Unbatched — a huge backlog would be one long transaction |
| Could `GlobalExceptionHandler` change unrelated API behaviour? | **YES — flagged.** Uncaught `DataIntegrityViolationException` anywhere now returns 409 instead of 500. Intentional, more accurate, all tests pass, but it is a real behaviour change |
| Does Render need configuration first? | **YES. `TOKEN_PEPPER` must be set before deploying.** Reversing the order takes production down |
| Is `TOKEN_PEPPER` correctly configured? | **UNVERIFIABLE from the repository.** It was reported as set ahead of this change. **Confirm in the Render dashboard before merging.** |

**Also note:** deploying Phase 2 activates `@EnableScheduling`, so the purge job
begins running daily at 03:30 UTC on the live instance.

---

## 12. Git / commit status

### Modified (7)
```
README.md                                                          ← UNRELATED
backend/.env.example                                               ← Phase 2
backend/src/main/java/.../VakilconnectApplication.java             ← Phase 2
backend/src/main/java/.../common/exception/ErrorResponse.java      ← Phase 2
backend/src/main/java/.../common/exception/GlobalExceptionHandler.java ← Phase 2
backend/src/main/java/.../identity/config/IdentityProperties.java  ← Phase 2
backend/src/test/resources/application-test.yaml                   ← Phase 2
```

### Untracked (10)
```
backend/src/main/java/.../common/exception/TokenAlreadyUsedException.java
backend/src/main/java/.../common/exception/TokenExpiredException.java
backend/src/main/java/.../common/exception/TokenInvalidException.java
backend/src/main/java/.../identity/entity/          (EmailToken, EmailTokenType)
backend/src/main/java/.../identity/repository/      (EmailTokenRepository)
backend/src/main/java/.../identity/service/         (TokenHasher, VerificationTokenService, EmailTokenPurgeJob)
backend/src/test/java/.../common/                   (ErrorResponseCompatibilityIT)
backend/src/test/java/.../identity/EmailTokenLifecycleIT.java
backend/src/test/java/.../identity/TokenConcurrencyIT.java
backend/src/test/java/.../identity/TokenHasherTest.java
docs/INTERVIEW-GUIDE.md                                            ← UNRELATED
```

### Unrelated dirty files
- `README.md` — cosmetic: one line removed from the animated typing-SVG URL
  ("Building in Public — One Commit at a Time"). Not Phase 2.
- `docs/INTERVIEW-GUIDE.md` — untracked study document. Not Phase 2.

### Should be committed (Phase 2)
Everything above marked `← Phase 2`, plus all 10 identity/exception source and
test paths.

### Should NOT be committed with Phase 2
`README.md`, `docs/INTERVIEW-GUIDE.md` — separate commits if wanted.

### Do `.env` files contain secrets?
**No.** `backend/.env` absent and ignored; `frontend/.env.local` ignored and
contains only a localhost URL; tracked `.env.example` files have empty values.
⚠️ Gap: `.env.local` / `.env.production` are **not** covered by `.gitignore`.

### Recommended commit message

```
feat(identity): phase 2 — token core

The shared primitive behind email verification and password reset:
issue, consume, invalidate, purge. Nothing calls it yet.

- EmailToken mapped to the already-deployed V7 table. No BaseEntity:
  email_tokens has no updated_at and ddl-auto=validate would refuse to
  start.
- TokenHasher: SecureRandom 32 bytes, Base64-URL (43 chars);
  HMAC-SHA256 under TOKEN_PEPPER, 64-char lowercase hex. New Mac per
  call (Mac is not thread-safe).
- Consumption is ONE conditional UPDATE; the affected-row count is the
  decision. Classification runs only after a loss.
- K1: DataIntegrityViolationException propagates and is mapped to 409
  in GlobalExceptionHandler. Catching it in the service would leave the
  transaction aborted.
- TOKEN_PEPPER is now @NotBlank; must be set in every environment
  BEFORE deploying this commit.
- ErrorResponse gains a nullable `code` with @JsonInclude(NON_NULL) at
  FIELD level so existing bodies stay byte-identical.
- The 500 fallback now logs the exception; it previously vanished.
- @EnableScheduling for the daily email-token purge (03:30 UTC), gated
  by vakilconnect.identity.purge-enabled.

No migration. No schema change. No auth changes.
295 tests pass, including TokenConcurrencyIT x20.
```

---

# HANDOFF TO SENIOR ENGINEER

**Architecture.** Spring Boot 3.5.15 / Java 21 REST API, PostgreSQL 16 via
Flyway `V1`–`V7` with `ddl-auto: validate` and `open-in-view: false`. Stateless
JWT (HS256, claims `sub`/`iat`/`exp` only, 24 h, no refresh, no revocation).
Feature-packaged: `auth`, `user`, `lawyer`, `client`, `admin`, `appointment`,
`review`, `reference`, `identity`, `common`, `config`, `security`. URL-pattern
authorization in `SecurityConfig` with `anyRequest().authenticated()` default-deny.
Frontend is Next.js 15 on Vercel; backend on Render; database on Neon.

**Current phase.** Identity roadmap
(`backend/docs/IDENTITY-ROADMAP.md`, `IDENTITY-TDD.md`).
Phases 0 and 1 are **committed and deployed** (commit `fc15ce7`, mislabelled
"phase 0" but contains both). **Phase 2 is complete in the working tree and
UNCOMMITTED.** Phases 3–7 are **NOT STARTED** — verified by grep, not by document.

**What was just implemented (Phase 2 — token core).**
New: `identity/entity/EmailToken`, `identity/entity/EmailTokenType`,
`identity/repository/EmailTokenRepository`, `identity/service/TokenHasher`,
`identity/service/VerificationTokenService`,
`identity/service/EmailTokenPurgeJob`, and
`common/exception/{TokenInvalidException,TokenExpiredException,TokenAlreadyUsedException}`.
Modified: `IdentityProperties.tokenPepper` → `@NotBlank`; `ErrorResponse.code`
(nullable, `@JsonInclude(NON_NULL)`, field-level); `GlobalExceptionHandler`
(+3 token handlers, +`DataIntegrityViolationException` → 409, +ERROR logging in
the `RuntimeException` fallback); `VakilconnectApplication` (`@EnableScheduling`);
`application-test.yaml` (`vakilconnect.identity.token-pepper`).

Key methods: `TokenHasher.generateRawToken()` / `hash(String)`;
`VerificationTokenService.issue(User, EmailTokenType)` /
`consume(String, EmailTokenType)` / `invalidateAll(UUID, EmailTokenType)` /
`purgeExpired()`; `EmailTokenRepository.consume(...)` /
`invalidateLive(...)` / `findByTokenHashWithUser(...)` /
`deleteTerminalBefore(...)`.

**Tests — exact results.** `target/surefire-reports/`, run newer than all
sources: **295 tests, 0 failures, 0 errors, 0 skipped.**
`TokenConcurrencyIT` 20/20 repetitions pass. `EmailTokenLifecycleIT` 17/17.
`TokenHasherTest` 7/7. `ErrorResponseCompatibilityIT` 3/3. `V7MigrationIT`
18/18. `AuthControllerIT` 13/13. `SecurityAuthorizationIT` 9/9.

**Known risks.**
1. `TOKEN_PEPPER` must be set in Render **before** this commit deploys; blank =
   startup failure. Cannot be verified from the repo.
2. `DataIntegrityViolationException` is now globally 409 — a behaviour change on
   endpoints outside Phase 2.
3. Deploying activates `@EnableScheduling`; purge runs daily 03:30 UTC.
4. Purge is unbatched and runs on every replica (no leader election).
5. `.gitignore` does not cover `.env.local` / `.env.production`.
6. `DB_USERNAME` defaults to `arshraj`, `DB_PASSWORD` to empty — should fail
   fast instead.
7. Commit `fc15ce7` is mislabelled and contains two phases.

**Unresolved questions.**
- Is `TOKEN_PEPPER` set in Render, and with what entropy?
- Is the global 409 mapping accepted as an intentional cross-cutting change?
- Should `purgeExpired()` be batched before volume grows?
- Should `invalidateAll(UUID, EmailTokenType)` ship now with no caller, or wait
  for Phase 6?
- Existing production users are grandfathered `is_email_verified = true`
  without having verified. Policy confirmed as intentional; Phase 7 must not
  retroactively gate them.

**Recommended next action.**
1. Confirm `TOKEN_PEPPER` in Render.
2. Commit Phase 2 (exclude `README.md` and `docs/INTERVIEW-GUIDE.md`).
3. Deploy and confirm the app boots and `/api/lawyers` still answers.
4. Then Phase 3 (`email` package) — no database dependency, parallelisable with
   Phase 5.
