# Data model

Two stores with a strict split:

- **Room** (`data/db/`): anything that is a *list of things* — blocks, exempt apps, sessions,
  aggregates, events.
- **Preferences DataStore** (`data/datastore/`): *scalars only* — settings, the ingest
  checkpoint, the persisted cooldown. Never lists.

## v2 — usage-debt only

v1 shipped a general rule engine (always/limit/schedule blocks with tap/friction/hard bypasses).
v2 collapses blocking to a single mechanism, the **usage-debt block**. Rather than restructure the
tables (a structural Room migration cannot be verified without an on-device schema check — there
is none in CI), the v1 tables are kept **byte-identical** and `MIGRATION_1_2` only *deletes* the
retired rows (see `Migrations.kt`). The consequences below are the important part:

- `block_rules` now only ever holds `USAGE_DEBT` rows. The columns `type`, `strictness`,
  `limitMinutes`, `debtRatio`, `frictionDelaySeconds`, `frictionPhrase`, `grantMinutes`,
  `deviceWide` are **vestigial**: written with fixed constants by `BlockRepository`
  (`type = USAGE_DEBT`, `strictness = HARD`, `deviceWide = true`, the rest null/0) and ignored on
  read. The live columns are `name`, `enabled`, `scheduleStart/EndMinuteOfDay` (the window),
  `scheduleDaysMask` (start-day mask), `maxBurstSeconds`.
- `rule_apps` is **repurposed**: it now stores each block's **exempt** packages (apps usable
  during the block that never accrue debt), not "apps this rule blocks".
- `unblock_grants` is **unused** (no bypass path exists) but the table + DAO remain so the schema
  is unchanged. `GrantMethod`, `RuleType`, `Strictness`, `BlockReason` survive as persistence-only
  enums (StorageEnums.kt) — never rename a constant.

A genuine *structural* change from here still requires a real migration and a committed schema
JSON (CLAUDE.md invariant #7).

## Room entities

### `block_rules`
`id`, `name`, `enabled`, `scheduleStartMinuteOfDay`, `scheduleEndMinuteOfDay`, `scheduleDaysMask`
(bit 0 = Monday; a wrapping window belongs to its *start* day), `maxBurstSeconds` (default 60),
`createdAt`/`updatedAt`, plus the vestigial columns above.

### `rule_apps`
Composite PK (`ruleId`, `packageName`), FK → `block_rules` ON DELETE CASCADE. The exempt-package
set for a block. Empty ⇒ everything (minus the `SafetyDenylist`) is blocked while the window is
active.

### `usage_sessions`
id, packageName, startEpochMs, endEpochMs, dayLocal (`"2026-07-07"`). Sessions are **pre-split at
local midnight** by `SessionDeriver`. Index (dayLocal, packageName) and (endEpochMs). The overview
reads these back via `NightTimeline`.

### `daily_usage`
Composite PK (dayLocal, packageName): totalMs, sessionCount. Recomputed transactionally by the
ingester; finalized + pruned by the nightly rollup.

### `block_events`
id, packageName, ruleId?, timestampEpochMs, reason (always `USAGE_DEBT` in v2), outcome
(`SHOWN` when the lockout appears, `DEBT_SERVED` when a cooldown completes). Index
(timestampEpochMs).

### `unblock_grants`
Retained from v1, unused. Pruned by the nightly rollup (a no-op on an empty table).

## DataStore keys

| Key | Type | Purpose |
|---|---|---|
| onboardingCompleted | Boolean | skip onboarding after first run |
| lastIngestedEventTime | Long | `UsageStatsIngester` checkpoint (idempotent re-runs) |
| openSessions | String | encoded pending open sessions carried across ingest batches |
| cooldownStartedAt | Long? | usage-debt: current cooldown start (for the indicator drain) |
| cooldownUntil | Long? | usage-debt: cooldown deadline (epoch ms) — survives reboot |
| retentionDays | Int | usage/event history retention (default 365) |
| forcePollingMode | Boolean | force the polling detector instead of accessibility (debug aid) |

The **burst** (continuous-use accumulator) is deliberately *not* persisted — a process restart
breaks "continuous", which is the correct behaviour.

## Time conventions

- Persisted instants are epoch millis (`Long`).
- Day bucketing is always the **local** calendar day (`dayLocal` string), never UTC epoch-day.
- Domain code never reads the clock; `now` is always a parameter (enforced by `DomainPurityTest`).
