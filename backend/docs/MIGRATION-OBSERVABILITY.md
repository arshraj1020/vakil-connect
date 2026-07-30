# Reference migration observability

**Phase 2G.5.** How to watch the reference-data migration, and what has to be
true before the Phase 2H cleanup can start.

This document exists because the Phase 2H decision — dropping `lawyers.city`
and the fallback read path — is gated on evidence, and until this phase that
evidence lived in process memory where nobody could see it.

---

## Endpoint

```
GET http://<host>:9091/actuator/prometheus
```

| | |
|---|---|
| Port | `MANAGEMENT_PORT`, default **9091** — not the application port |
| Bind address | `MANAGEMENT_ADDRESS`, default **127.0.0.1** |
| Exposed endpoints | `health`, `info`, `prometheus` — nothing else |
| Authentication | **none on the management port** |

Actuator is not reachable on the application port at all. The JWT chain in
`SecurityConfig` is unchanged and has no `/actuator` matcher, because there is
nothing there to match.

> **The management port must not be publicly routable.** There is no
> authentication in front of it; the network is the control. Bind it to
> loopback for a sidecar scraper, or to the pod IP where Prometheus reaches it
> directly. Exposing 9091 to the internet publishes operational counters to
> anyone who asks. This is the deployment's responsibility and the application
> cannot enforce it.

`health` and `info` are on because a deployment needs them to be operable.
`env`, `beans`, `heapdump` and `threaddump` are off: once the port is
reachable, the exposure list *is* the security boundary.

---

## Metrics

### `vakilconnect_reference_city_reads_total{source="legacy"}`

Counter. A lawyer's city was served from the legacy `lawyers.city` column
because the lawyer has no `primary_city_id`.

**This is the number Phase 2H is gated on.** Incremented once per DTO mapped,
so a ten-result search page contributes ten — the unit is *values served*, not
*requests*.

### `vakilconnect_reference_city_reads_total{source="reference"}`

Counter. A lawyer's city was served from the reference model. The denominator:
without it, zero legacy reads is ambiguous between "migration complete" and
"nobody looked".

### `vakilconnect_reference_lawyers_missing_primary_city`

Gauge. Lawyers with a null `primary_city_id`. Traffic-independent — it says
whether unmigrated rows *exist*, not whether anyone read them.

### `vakilconnect_reference_lawyers_missing_practice_cities`

Gauge. Lawyers with no rows in `lawyer_practice_cities`.

**This is the search branch, and it is not the same population as the gauge
above.** The DTO read path falls back on `primaryCity == null`; the search
predicate falls back on `practiceCities IS EMPTY`. Those agree on every row that
honours the Option C invariant and diverge only on a row that violates it —
primary set, practice set empty. Such a row is served from the legacy *search*
branch while its DTO reads the reference model, so without this gauge it looks
fully migrated from every other angle.

Shares the same cached report as the other gauges — no extra query.

### `vakilconnect_reference_unresolved_cities`

Gauge. Distinct legacy city strings that resolve to no curated city. Each one is
a typo to correct, a city to seed, or an alias to add. This is the actionable
number: it tells you what work remains, not just how much.

**`NaN` on any of the three gauges above means the reconciliation query has
never succeeded** — a database problem, not a completed migration. Alert on it.
Never read `NaN` as zero.

### `vakilconnect_reference_reconciliation_age_seconds`

Gauge. Seconds since the reconciliation report last refreshed **successfully**.

The three gauges above serve a cached report, and on a query failure the cache
is served anyway — deliberately, so a brief database blip does not blank the
dashboard. Their values therefore look identical whether they are live or hours
old. This gauge is the only thing that tells them apart, and a stale
`unresolved_cities = 0` is exactly the reading that would wrongly open the
Phase 2H gate.

Expected to sit between 0 and `MIGRATION_RECONCILIATION_TTL` (default 300s),
sawtoothing as the cache expires and refreshes.

**Before the first successful refresh it counts from application start**, not
from the epoch and not as a sentinel. `NaN` would have matched the other gauges,
but `NaN > 900` is false in PromQL — so the state that most needs paging, a
reconciliation that has never worked since boot, would page nobody. Counting
from start is true, rises on its own, and trips the ordinary threshold alert.

Distinguishing the two unhealthy states:

| age | value gauges | Meaning |
|---|---|---|
| < TTL | numbers | Healthy |
| climbing | numbers | Cache is stale — refreshes are failing |
| climbing | `NaN` | Reconciliation has **never** succeeded since startup |

No `baseUnit` is set on this meter even though the values are seconds:
Micrometer's Prometheus naming convention appends the base unit to the name, and
the name already ends in `.seconds`.

### Reads per scrape

The counters are free: they read `LongAdder`s the application already
maintains. The gauges run nine aggregates over `lawyers` and `users`, so the
result is cached for `MIGRATION_RECONCILIATION_TTL` (ISO-8601, default `PT5M`).
Scrape as often as you like; the queries run at most once per TTL.

---

## The Phase 2H gate

> **The authoritative gate is in [MIGRATION-OPERATIONS.md](MIGRATION-OPERATIONS.md) §3**,
> which adds an error-evidence condition. What follows is the metric-level
> summary; where the two differ, the operations document wins.

All four must hold **simultaneously and continuously** across the observation
window:

| # | Condition | Query |
|---|---|---|
| 1 | No legacy reads | `increase(vakilconnect_reference_city_reads_total{source="legacy"}[7d]) == 0` |
| 2 | Reference reads are happening | `increase(vakilconnect_reference_city_reads_total{source="reference"}[7d]) > 0` |
| 3 | No unmigrated lawyers (display axis) | `vakilconnect_reference_lawyers_missing_primary_city == 0` |
| 4 | No unmigrated lawyers (search axis) | `vakilconnect_reference_lawyers_missing_practice_cities == 0` |
| 5 | Nothing unresolvable | `vakilconnect_reference_unresolved_cities == 0` |
| 6 | The gauges above are current | `vakilconnect_reference_reconciliation_age_seconds < 900` |

**Condition 2 is not redundant.** A process that has served no traffic reports
zero legacy reads, and so does a finished migration. Without a non-zero
denominator, condition 1 is satisfied by an idle instance — which is exactly the
mistake that makes a cleanup look safe when it is not. `cityLegacyReadsEliminated()`
on `FallbackReadSnapshot` encodes the same rule in Java.

**Conditions 3, 4 and 5 are not redundant either.** Fallback reads are
traffic-driven: a lawyer nobody browses never increments anything. The gauges
close that hole. Conditions 3 and 4 measure *different* populations — see the
`missing_practice_cities` entry above.

**Condition 6 qualifies 3, 4 and 5.** Those three are cached values, and a
cached zero from six hours ago satisfies them just as well as a live one. Zeros
read while the age gauge is climbing are not evidence.

### Getting to zero

Conditions 3 and 4 will not clear on their own. `unresolved_cities` lists the
strings that failed; for each one either seed the city, add an alias, or correct
the record. Then re-run the reconciliation. The values move only when the data
moves.

---

## Observation window

**Minimum 14 days**, and it must include:

- a full weekly traffic cycle — weekday and weekend browsing differ
- at least one deploy, so the counters demonstrably survive a restart
- any monthly or batch job that touches lawyer profiles

Counters are process-scoped and reset on restart, which is why the gate is
expressed as `increase(...[7d])` over a scraped series rather than as an
absolute value. Prometheus handles the resets; a single reading does not.

**Do not shorten the window because the numbers look good early.** The failure
this guards against is a rarely-read cohort of unmigrated lawyers — precisely
the population a short window is least likely to sample.

### Before starting the clock

- [ ] 2G deployed to production
- [ ] Prometheus scraping `:9091/actuator/prometheus`
- [ ] All four metrics visible with non-`NaN` values
- [ ] Alert on `vakilconnect_reference_city_reads_total{source="legacy"}` increasing after it first reaches zero

That last alert is the one that matters. Reaching zero is not the end state —
*staying* at zero is, and something re-introducing a legacy read after the fact
is exactly the regression Phase 2H would make permanent.

---

## What this phase did not change

No migration behaviour, no search, no write path, no DTO, no schema.
`ReferenceFallbackMetrics` is untouched and still owns the counters;
`ReferenceMigrationMetrics` only reads it. Delete that class and you lose the
observability and nothing else.
