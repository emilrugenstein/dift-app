# Feature spec: usage tracking

## Behavior

- Data source: Room `daily_usage` + `usage_sessions`, maintained by `UsageStatsIngester`
  (ADR-0005) — never `queryUsageStats` at display time.
- Ingestion cadence: every 15 min via WorkManager, plus on Overview open.
- **Class-aware sessions.** A package is "in session" while at least one of its activities is
  resumed. The `SessionDeriver` tracks the per-package set of RESUMED activity classes and only
  closes the session when the set empties (or on screen-off/shutdown). This is load-bearing:
  Android's within-app handoff is `PAUSED(act1) → RESUMED(act2) → STOPPED(act1)`, and a
  package-keyed model lets the trailing STOPPED kill the fresh session — silently dropping all
  foreground time until the next event, which showed up as a ~25% undercount vs Digital
  Wellbeing.
- **Live open sessions.** Sessions are only persisted when they close, so the Overview adds
  still-open sessions (`start → now`, clipped to midnight/columns) at display time. Nothing is
  written back — no double counting once the session closes.
- The launcher is *not* filtered: home-screen time counts, matching Digital Wellbeing.

## Acceptance

- Daily totals within a few % of Digital Wellbeing (exact parity is unattainable — DW uses its
  own internal tracker with slightly different attribution rules).
- A day boundary at local midnight splits sessions exactly (no usage leaking across days).
- Reboots, timezone changes, and re-ingestion (checkpoint) do not corrupt daily aggregates.
