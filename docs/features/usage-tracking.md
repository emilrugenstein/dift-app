# Feature spec: usage tracking & dashboard (M1)

## Behavior

- The dashboard shows: today's total screen time, top apps by time (with per-app bars),
  blocks-today count (M2+), and a 7-day trend (M5: 30-day).
- Data source: Room `daily_usage` + `usage_sessions`, maintained by `UsageStatsIngester`
  (ADR-0005) — never `queryUsageStats` at display time.
- Ingestion cadence: every 15 min via WorkManager, plus on dashboard open, plus continuous
  in-memory tracking of the currently-open session for live "used today" (feeds limit rules).
- The Apps screen lists installed launchable apps with today's usage and a quick
  "block always" toggle (M2+).

## Acceptance (gates M4's limit rules)

- After 3 days of normal use, Dift's daily totals are within ~5% of Digital Wellbeing.
- A day boundary at local midnight splits sessions exactly (no usage leaking across days).
- Reboots and timezone changes do not corrupt daily aggregates.
