# Reference migration — production operations

**Phase 2G.6.** Deployment checklist, runbook and the Phase 2H go/no-go gate.

Metric definitions live in [MIGRATION-OBSERVABILITY.md](MIGRATION-OBSERVABILITY.md)
and are not repeated here. **This file is the authoritative Phase 2H gate.**

---

## 0. Read this first — the two fallback branches are separate populations

The DTO read path and the search path branch on **different columns**, and each
has its own meter:

| Path | Branches on | Meter |
|---|---|---|
| `cityOf` (DTO mapping) | `primaryCity == null` | `city_reads_total{source}`, `lawyers_missing_primary_city` |
| Search predicate | `practiceCities IS EMPTY` | `lawyers_missing_practice_cities` |

For every correctly-migrated row these agree, and V6 plus the 2E dual-write
maintain the invariant `primary ∈ practice`. They diverge only on a row that
violates it — `primary_city_id` set, no rows in `lawyer_practice_cities`.

Such a row used to be invisible to the gate: search served it from the legacy
branch with nothing counting that, its DTO read `primaryCity` and so incremented
`source="reference"`, and `lawyers_missing_primary_city` read zero for it.
**Phase 2G.7 closed that** by exposing `lawyers_missing_practice_cities`, which
`ReconciliationReport` was already computing.

Gate condition **G4** is now fully automated. There is no manual SQL step.

---

## 1. Production deployment checklist

### 1.1 Environment variables

| Variable | Required | Default | Notes |
|---|---|---|---|
| `JWT_SECRET` | **yes** | *none — app will not start* | Unchanged by this phase |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | yes | localhost defaults | Unchanged |
| `MANAGEMENT_PORT` | no | `9091` | Must not collide with the app port |
| `MANAGEMENT_ADDRESS` | no | `127.0.0.1` | **See 1.2 — the default is wrong for most Kubernetes setups** |
| `MIGRATION_RECONCILIATION_TTL` | no | `PT5M` | ISO-8601. Leave alone unless §5.3 applies |
| `ADMIN_EMAIL` | — | — | Unchanged |

### 1.2 Management port — the decision that actually matters

The default `MANAGEMENT_ADDRESS=127.0.0.1` binds actuator to loopback. That is
correct for a **sidecar** scraper sharing the network namespace, and **wrong for
Prometheus scraping the pod IP directly** — the port will simply be unreachable
and you will conclude the metrics are broken.

Pick one:

- **Sidecar scraper** — keep `127.0.0.1`. Nothing off-pod can reach it. Safest.
- **Direct pod scrape** — set `MANAGEMENT_ADDRESS=0.0.0.0`, declare port 9091 in
  the container spec, and **add a NetworkPolicy allowing ingress on 9091 only
  from the Prometheus namespace.** There is no authentication on this port; the
  NetworkPolicy is the entire access control.
- **VM / bare metal** — keep `127.0.0.1` and scrape from a local agent, or bind
  to the private interface and firewall the port.

- [ ] Chosen and applied
- [ ] Port 9091 **not** present in any public LB, Ingress or Service of type LoadBalancer
- [ ] Verified from outside the trust boundary that `:9091/actuator/prometheus` is refused

### 1.3 Prometheus scrape configuration

```yaml
scrape_configs:
  - job_name: vakilconnect
    scrape_interval: 30s          # see 5.4
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['vakilconnect:9091']
```

- [ ] Target shows **UP**
- [ ] `ReferenceReconciliationStale` alert rule loaded (§5.2)
- [ ] **Retention ≥ 21 days.** The gate queries `increase(...[7d])` across a
      14-day window; retention shorter than ~21d makes the gate unevaluable at
      the end of the window, which is the worst possible moment to find out.

### 1.4 Network and security

- [ ] Actuator not reachable on the application port (`curl <app>:8080/actuator/health` → 404)
- [ ] Application endpoints still require JWT — spot-check one authenticated route
- [ ] Exposure list is `health,info,prometheus` only; `/actuator/env` → 404 on 9091
- [ ] `management.endpoint.health.show-details: never` unchanged

### 1.5 Post-deployment health verification

Run in order. Each must pass before the next is meaningful.

```bash
# 1. app is up on the application port
curl -sf http://<app>:8080/api/reference/countries | head -c 80

# 2. management port responds
curl -sf http://<host>:9091/actuator/health          # {"status":"UP"}

# 3. all four migration meters are present
curl -s http://<host>:9091/actuator/prometheus | grep vakilconnect_reference

# 4. actuator is NOT on the app port
curl -s -o /dev/null -w '%{http_code}\n' http://<app>:8080/actuator/health   # 404
```

Step 3 must print four series. Anything fewer → §3.2.

---

## 2. Operations runbook

### 2.1 Expected values immediately after deployment

| Metric | Expected at T+0 | Expected within one hour of traffic |
|---|---|---|
| `city_reads_total{source="reference"}` | `0` | **rising** |
| `city_reads_total{source="legacy"}` | `0` | `0`, or rising if unmigrated rows are being browsed |
| `lawyers_missing_primary_city` | steady, ≥ 0 | unchanged unless profiles are edited |
| `unresolved_cities` | steady, ≥ 0 | unchanged unless profiles are edited |

Counters start at `0.0`, not absent — that is deliberate and tested. A **missing
series** and a **zero series** mean completely different things (§2.3).

### 2.2 Verifying the metrics actually work

Do not trust a zero you have not seen move. Before the observation window,
prove the instrument responds:

1. In a **non-production** environment, register a lawyer with a city that
   resolves to nothing (e.g. `Gotham`), then `GET /api/lawyers/{id}`.
2. `city_reads_total{source="legacy"}` must increase by exactly 1.
3. `unresolved_cities` must increase by 1 within `MIGRATION_RECONCILIATION_TTL`.

If step 2 does not move, the gate is measuring nothing and every subsequent zero
is meaningless. `ReferenceMigrationMetricsIT` asserts this, but assert it against
the deployed artifact too.

### 2.3 Distinguishing "no traffic" from "no fallback"

This is the single most important operational distinction in this phase, and the
reason the gate has more than one condition.

```promql
# Is the application serving city values at all?
increase(vakilconnect_reference_city_reads_total{source="reference"}[1h]) > 0
```

| reference reads | legacy reads | Meaning |
|---|---|---|
| rising | `0` | **Migrated and in use** — this is the target state |
| `0` | `0` | **No traffic.** Says nothing about the migration. Not evidence |
| rising | rising | Migration incomplete — expected early in the window |
| `0` | rising | Only unmigrated lawyers are being read — investigate |

A zero legacy count from an idle instance is indistinguishable from success
unless you also require the reference count to be rising. That is condition
**G2**, and it is not optional.

### 2.4 Abnormal behaviour

| Symptom | Meaning | Action |
|---|---|---|
| Value gauge reports `NaN`, age climbing | Reconciliation has **never** succeeded since startup | Check DB health. **Never read `NaN` as zero** |
| Value gauges numeric, age climbing | Cache is **stale** — refreshes are failing | Check logs for `Reference reconciliation query failed`. The numbers on screen are that old |
| Age exceeds ~3× TTL | Refreshes have stopped | Alert should already have fired — see below |
| `legacy` rises after reaching zero | Regression, or an unmigrated cohort just got traffic | **Stop the clock.** Restart the window |
| Both counters flat during known traffic | Metrics broken, or no city values served | Re-run §2.2 |
| Series disappears entirely | Scrape target down, or app restarted | Check target health; counters reset on restart, which is expected |
| `unresolved_cities` rises | New unresolvable free text is being written | A city needs seeding or an alias — see below |

### 2.5 Driving `unresolved_cities` to zero

It will not fall on its own. For each unresolved name, decide: typo to correct,
city to seed, or alias to add. There is no admin CRUD for reference data
(deliberately — out of scope for 2A–2G), so today this means a new Flyway
migration. Values move only when the data moves.

Retrieving the actual names currently requires calling
`ReferenceReconciliationService.report().unresolvedCityNames()` — the gauge
publishes only the count. See §5.5.

---

## 3. Phase 2H go/no-go gate

**Phase 2H may begin only if all of the following remain true throughout the
observation window.** Not "true at the end" — true *throughout*.

| | Condition | Check |
|---|---|---|
| **G1** | `cityFallbackReads` remains zero | `increase(vakilconnect_reference_city_reads_total{source="legacy"}[7d]) == 0` sustained for the full window |
| **G2** | Reference reads continue to occur | `increase(vakilconnect_reference_city_reads_total{source="reference"}[7d]) > 0` for every 7d slice |
| **G3** | Unresolved city names remain zero | `vakilconnect_reference_unresolved_cities == 0` continuously |
| **G4** | Reconciliation metrics remain healthy **and current** | §3.1 — all three gauges zero, none `NaN`, age below threshold |
| **G5** | No production errors indicate fallback dependence | §3.2 |

### 3.1 G4 — fully automated since Phase 2G.7

```promql
vakilconnect_reference_lawyers_missing_primary_city    == 0   # display axis
vakilconnect_reference_lawyers_missing_practice_cities == 0   # search axis
vakilconnect_reference_unresolved_cities               == 0
vakilconnect_reference_reconciliation_age_seconds      < 900  # the three above are current
```

All four continuously, and none of the first three reporting `NaN`.

**The age condition is not decoration.** The first three are cached values, and
a cached zero from six hours ago satisfies them exactly as well as a live one.
Zeros read while the age gauge is climbing are not evidence of anything. See
§2.4 for reading age against the value gauges.

The first two measure different populations and both are required — a row with
a primary city but no practice-city rows is served from the legacy **search**
branch while reading as migrated everywhere else. See §0.

No SQL step. If you want to confirm the gauge against the table directly during
initial verification, this is the equivalent query, but it is not part of the
gate:

```sql
SELECT count(*) FROM lawyers l
WHERE NOT EXISTS (SELECT 1 FROM lawyer_practice_cities p WHERE p.lawyer_id = l.id);
```

### 3.2 G5 — error evidence

- [ ] No `LazyInitializationException` mentioning `primaryCity` or `practiceCities`
- [ ] No 500s from `/api/lawyers` or `/api/lawyers/{id}` attributable to city resolution
- [ ] No support reports of a lawyer missing from search results for their own city
- [ ] `unresolvedCityNames()` returns an empty list (not merely a zero gauge, which could be stale)

### 3.3 Observation window

**Minimum 14 continuous days**, including:

- [ ] A full weekly traffic cycle — weekday and weekend browsing differ
- [ ] At least one deploy, proving the counters survive a restart
- [ ] Any monthly or batch job touching lawyer profiles

Counters are process-scoped and reset on restart, which is why the gate is
expressed as `increase(...)` over a scraped series rather than an absolute value.

**Do not shorten the window because the numbers look good early.** The risk this
guards against is a rarely-read cohort of unmigrated lawyers — exactly the
population a short window is least likely to sample.

**Restart the clock if:** G1 breaks at any point, the scrape target is down for
more than a few hours, or reference data changes materially (a city seeded, an
alias added, a bulk profile edit).

---

## 4. Sign-off

| | |
|---|---|
| Window start | ____________ |
| Window end (≥ start + 14d) | ____________ |
| G1 · legacy reads zero throughout | ☐ |
| G2 · reference reads occurring throughout | ☐ |
| G3 · unresolved cities zero throughout | ☐ |
| G4 · reconciliation healthy — primary-city, practice-cities and unresolved gauges all zero, none NaN, **and age below 900s throughout** | ☐ |
| G5 · no error evidence of fallback dependence | ☐ |
| Approved by / date | ____________ |

All five, or Phase 2H does not start.

---

## 5. Recommended operational improvements

Ordered by how much they affect the reliability of the gate. **None are
implemented** — Phase 2G.6 changed no code.

### 5.1 ~~Expose `lawyersMissingPracticeCities`~~ — done in Phase 2G.7

Implemented. `vakilconnect_reference_lawyers_missing_practice_cities` reads the
same cached report as the other gauges; no additional query. G4 is automated and
the legacy search branch is monitored.

### 5.2 ~~Publish reconciliation freshness~~ — done in Phase 2G.8

Implemented as `vakilconnect_reference_reconciliation_age_seconds`. Reuses the
existing cache timestamp; no second cache, no scheduler.

**Recommended alert — configure this before the observation window:**

```yaml
- alert: ReferenceReconciliationStale
  expr: vakilconnect_reference_reconciliation_age_seconds > 900   # 3 x default TTL
  for: 10m
  annotations:
    summary: >
      Reference reconciliation has not refreshed for over 15 minutes.
      The migration gauges are stale; treat their values as unverified
      and do not use them for a Phase 2H decision.
```

`900` is 3× the default `PT5M` TTL — high enough that one failed refresh does
not page, low enough that a persistent failure is caught the same hour. Scale it
with the TTL if you change it.

This alert also covers "reconciliation has never succeeded since boot", because
the gauge counts from application start in that state rather than reporting a
sentinel. `NaN` would have been consistent with the value gauges but silently
never fires a `> threshold` comparison.

### 5.3 Reconciliation query cost

Nine aggregates over `lawyers` and `users`, at most once per `PT5M`. Fine at
current volume. If `lawyers` grows past ~1M rows, raise the TTL rather than
adding indexes for a query that exists only until Phase 2H.

### 5.4 Scrape interval

**30s.** Faster gains nothing — gauges are TTL-cached at 5m and re-serve the same
value — while raising cardinality cost. Slower risks missing a short-lived
counter increment before a restart.

### 5.5 Surfacing unresolved city *names*

The gauge publishes a count; the actionable list is only reachable from Java.
A read-only admin endpoint under the existing `hasRole("ADMIN")` matcher would
need no new security decision. Deferred from 2F/2G deliberately — worth
reconsidering now that someone has to drive the number to zero.

### 5.6 HTTP observation filter — verify, don't assume

I previously told you "the application port serves exactly what it served
before." Response-wise that holds, but I overstated it: an HTTP observation
filter runs on the application port and its measurements are now exported as
`http.server.requests`. `micrometer-observation` was already on the classpath
before 2G.5, so the filter itself may well predate this phase — I have not
proven either way.

Check after deployment:

```bash
curl -s http://<host>:9091/actuator/prometheus | grep -c '^http_server_requests'
```

Confirm the `uri` label shows templated paths (`/api/lawyers/{lawyerId}`), not
raw UUIDs. Raw UUIDs would mean unbounded cardinality and should be fixed before
the 14-day window, not during it.

### 5.7 Health endpoint detail

`show-details: never` means `/actuator/health` cannot distinguish "app up,
database down". If you point Kubernetes probes at it, consider a readiness group
exposing the DB indicator to authorized callers only.
