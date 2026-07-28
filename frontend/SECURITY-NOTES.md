# Security Notes

Two sections: dependency advisories, and authorization invariants that the
frontend cannot enforce.

## Dependency advisories

`npm audit` reports advisories against the full dependency *graph*, including
build tooling that never reaches a browser. This note records which advisories
are accepted, and why.

Last reviewed: 2026-07-27 · Next.js 15.5.22

## Current state

| Scope | Command | Highs |
|---|---|---:|
| Production (shipped) | `npm audit --omit=dev` | 2 |
| Full graph | `npm audit` | 11 |

## Resolved

**PostCSS** — 3 advisories (XSS via unescaped `</style>`, path traversal and
arbitrary file read via `sourceMappingURL`).

Next.js pins `postcss@8.4.31` internally; the fix landed in `8.5.18`. Resolved
with an `overrides` entry forcing `^8.5.23` across the tree. PostCSS follows
semver strictly and 8.5 is backward-compatible with 8.4.

## Accepted

**sharp / libvips** — CVE-2026-33327, -33328, -35590, -35591. *Production, 2 highs.*

Not fixable by upgrading: every Next.js release through `16.2.12` declares
`sharp: ^0.34.5`, while the patch is in `0.35.0`. `npm audit fix --force`
"resolves" this by installing `next@14.2.35` — a major downgrade that is
strictly worse for security.

Not reachable in v1: `sharp` is used only by `next/image` when optimising
remote or uploaded images. VakilConnect v1 has no image upload (document
management is deferred to v2) and the API exposes no image endpoints, so the
vulnerable code path is never executed.

> **Revisit when:** `next/image` is introduced with remote sources (e.g. lawyer
> profile photos). At that point add `"sharp": "^0.35.3"` to `overrides` and
> verify image rendering, since that version sits outside Next's declared range.

**brace-expansion (via minimatch → eslint)** — DoS through unbounded expansion.
*Dev-only, 9 highs.*

`eslint` and `eslint-config-next` are `devDependencies`; this code never ships.
The modern path is already patched (`minimatch@10.2.5` → `brace-expansion@5.0.8`).
The flagged copy is `brace-expansion@1.x` inside the legacy `minimatch@3.1.5`
that ESLint 9 carries internally. Overriding would force a 1.x → 5.x major jump
into a dependency declaring `^1.1.7`, risking a broken lint setup for no
meaningful gain. Clears naturally when ESLint updates its internal minimatch.

## Policy

- Never run `npm audit fix --force` without reading the proposed change: for
  this project it downgrades Next.js by several major versions.
- Judge advisories by reachability, not count. A build-time advisory in a dev
  dependency is not equivalent to one in the runtime request path.
- Re-review on every dependency upgrade and before each release.

---

# Authorization invariants

Rules the product depends on that are **not enforced by the backend**. Each is
listed with whatever the frontend does about it, and why that is not sufficient.

The general principle: a check that runs in the browser is a usability feature.
It prevents mistakes, never misuse. Any client can issue the underlying request
directly, so an invariant is only enforced once the server refuses to violate it.

## 1. An administrator must not deactivate their own account

**Backend today:** `AdminServiceImpl.setUserActive(userId, active)` loads the
user by id, assigns the flag and saves. It does not compare `userId` against the
caller, and `AdminController` passes no principal to it. An admin can therefore
deactivate themselves through `PUT /api/admin/users/{ownId}/deactivate`.

**Consequence:** `CustomUserDetailsService` builds the principal with
`.disabled(!user.isActive())`, and since the integration-testing fix
`JwtAuthenticationFilter` checks `isEnabled()` on every request. The account is
therefore refused **immediately**, on the admin's very next API call — not at
their next sign-in. That makes this guard more valuable than when it was
written: the lockout is instant and the admin cannot reverse it, because
reversing it requires the admin portal they have just lost access to. Recovery
means either updating the row directly in the database, or the restart route
described in §2.

**Frontend today:** `canDeactivate()` in
`features/admin-user-management/lib/user-utils.ts` compares the row's id with
the signed-in user's and renders the control disabled, with the reason on the
button. This is a **UX safeguard only** — it prevents an accidental click and
nothing else. A direct API call from curl or the devtools console succeeds
exactly as before.

**Should be:** reject the request server-side when the target id equals the
authenticated principal's id — a `BusinessRuleException` returning 409, matching
how other business rules already answer.

## 2. The platform must always retain at least one active administrator

**Backend today:** nothing counts administrators before deactivating one.

**Consequence:** with two admin accounts, A can deactivate B and B can
deactivate A. The platform is then left with no administrator able to sign in.

Recovery is possible but manual. `AdminBootstrapRunner` skips when a user with
`ADMIN_EMAIL` already exists — it keys on that **email**, not on "an admin
exists" — so restarting with the original `ADMIN_EMAIL` does nothing, while
restarting with a *different* `ADMIN_EMAIL` creates a fresh active admin. That
is a deployment-config change under time pressure, not a fix.

**Frontend today:** nothing, and nothing is possible. The guard above happens to
cover the single-admin case — if exactly one admin exists, it is the one signed
in — but that is coincidence, not coverage. The browser cannot count *active*
admins: `GET /api/admin/users?role=ADMIN` is paginated, and analytics reports
`totalAdmins` by role without regard to the active flag.

**Should be:** count active admins inside the same transaction as the write and
refuse the last one. It has to be transactional — two concurrent requests each
seeing "two active admins" would otherwise both succeed and leave zero.

> **Revisit when:** the admin portal is hardened for real deployment, or before
> any environment gets more than one administrator account.
