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
