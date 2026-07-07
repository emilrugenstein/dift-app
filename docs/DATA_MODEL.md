# Data model

Two stores with a strict split:

- **Room** (`data/db/`): anything that is a *list of things* — rules, grants, sessions,
  aggregates, events. Schema JSONs are exported to `app/schemas/` and committed; every schema
  change ships with a migration (CLAUDE.md invariant #7).
- **Preferences DataStore** (`data/datastore/`): *scalars only* — settings, checkpoints,
  the persisted night-debt state. Never lists.

## Room entities (target state — introduced milestone by milestone)

### `block_rules` (M2+)
| Column | Type | Notes |
|---|---|---|
| id | Long PK autogen | |
| name | String | user-visible label |
| type | enum `RuleType` | `ALWAYS`, `DAILY_LIMIT`, `SCHEDULE`, `USAGE_DEBT` |
| enabled | Boolean | |
| strictness | enum `Strictness` | `TAP_THROUGH`, `FRICTION`, `HARD`. `USAGE_DEBT` is always `HARD`. |
| limitMinutes | Int? | `DAILY_LIMIT` only |
| scheduleStartMinuteOfDay | Int? | `SCHEDULE`/`USAGE_DEBT`; window may wrap midnight |
| scheduleEndMinuteOfDay | Int? | |
| scheduleDaysMask | Int? | bit 0 = Monday; a wrapping window belongs to its *start* day |
| maxBurstSeconds | Int? | `USAGE_DEBT` only (default 60) |
| debtRatio | Float? | `USAGE_DEBT` only (default 1.0 → X s use = X s debt) |
| deviceWide | Boolean | true for `USAGE_DEBT` (applies to everything minus SafetyDenylist) |
| frictionDelaySeconds | Int | `FRICTION` unblock wait (default 30) |
| frictionPhrase | String? | null = default phrase from settings |
| grantMinutes | Int | how long a successful unblock lasts (default 10) |
| createdAt / updatedAt | Long | epoch ms |

### `rule_apps` (M2+)
Composite PK (`ruleId`, `packageName`), FK → `block_rules` ON DELETE CASCADE.
Empty set + `deviceWide=true` ⇒ rule applies to all apps except the SafetyDenylist.

### `unblock_grants` (M4)
id, packageName, ruleId, grantedAtEpochMs, expiresAtEpochMs, method (`TAP`|`FRICTION`).
Index (packageName, expiresAtEpochMs). Grants never defeat `HARD` rules.

### `usage_sessions` (M1+)
id, packageName, startEpochMs, endEpochMs, dayLocal (`"2026-07-07"`).
Sessions are **pre-split at local midnight** by `SessionDeriver`, so `dayLocal` is exact.
Index (dayLocal, packageName) and (endEpochMs).

### `daily_usage` (M1+)
Composite PK (dayLocal, packageName): totalMs, sessionCount. Recomputed transactionally by
the ingester; finalized + pruned by the nightly rollup.

### `block_events` (M2+)
id, packageName, ruleId?, timestampEpochMs, reason (`ALWAYS`|`LIMIT_EXHAUSTED`|`IN_SCHEDULE`|
`USAGE_DEBT`), outcome (`SHOWN`|`UNBLOCKED_TAP`|`UNBLOCKED_FRICTION`|`ABANDONED`|
`HOME_KICKED`|`DEBT_SERVED`). Index (timestampEpochMs). Also stores detection latency ms
for dogfooding metrics.

## DataStore keys

| Key | Type | Purpose |
|---|---|---|
| onboardingCompleted | Boolean | skip onboarding after first run |
| lastIngestedEventTime | Long | `UsageStatsIngester` checkpoint (idempotent re-runs) |
| activeBurstStartedAt | Long? | night-debt: current usage burst start (epoch ms) |
| debtUntil | Long? | night-debt: lockout end (epoch ms) — survives reboot |
| defaultFrictionPhrase | String | typed phrase for FRICTION unblocks |
| retentionDays | Int | usage/event history retention (default 365) |
| detectorModeOverride | enum | `AUTO` / `FORCE_POLLING` (debug aid) |

## Time conventions

- Persisted instants are epoch millis (`Long`).
- Day bucketing is always the **local** calendar day (`dayLocal` string derived from
  `LocalDate`), never UTC epoch-day — otherwise a timezone change corrupts daily limits.
- Domain code never reads the clock; `now` is always a parameter (enforced by
  `DomainPurityTest`).
