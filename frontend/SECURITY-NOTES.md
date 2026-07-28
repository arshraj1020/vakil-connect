# Dependency Security Notes

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
