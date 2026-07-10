# ADR-0005: Usage is derived from UsageEvents into Room, not read from queryUsageStats

**Status**: accepted (2026-07)

## Decision

Per-app usage is computed by deriving sessions from `UsageStatsManager.queryEvents()`
(`ACTIVITY_RESUMED`/`ACTIVITY_PAUSED`/`ACTIVITY_STOPPED`, plus `SCREEN_NON_INTERACTIVE` and
`DEVICE_SHUTDOWN` to close dangling sessions) via the pure `SessionDeriver`, persisted into
Room (`usage_sessions`, `daily_usage`) by a checkpointed, idempotent `UsageStatsIngester`.

## Rationale

- `queryUsageStats` daily buckets are known-inaccurate (double counting, coarse intervals)
  and can't drive live limit enforcement.
- The system retains raw events only for a handful of days; Room ownership gives unlimited,
  prunable history.
- A DataStore checkpoint (`lastIngestedEventTime`) makes re-ingestion idempotent — WorkManager
  retries must not double-count.
- **Rejected: reading queryUsageStats at display time** (inaccurate, no history, no live data).

## Consequences

- Sessions are pre-split at local midnight so daily aggregates are exact.
- Acceptance gate (M1): totals within ~5% of Digital Wellbeing over 3 days before limit rules
  build on top.

## Amendment (2026-07): class-aware derivation

Package-keyed open sessions undercounted ~25% vs Digital Wellbeing: within-app navigation emits
`PAUSED(act1) → RESUMED(act2) → STOPPED(act1)`, and with STOPPED mapped to a package-level
PAUSE, the trailing event killed the fresh act2 session. The deriver now tracks the per-package
**set of resumed activity classes** and closes only when it empties; the set is carried in the
open-session checkpoint (`pkg|start|cls1;cls2`, backward-compatible with two-field entries).
STOPPED stays mapped as a safety net for missed PAUSED events.
