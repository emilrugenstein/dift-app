# Feature spec: data export

Settings → **Data** → *Export data*. Everything Dift knows leaves as **one zip** the user saves
wherever they like (system file picker — Storage Access Framework, so no storage permission and
nothing is written anywhere the user did not choose). Dift still has no network permission; the
file goes nowhere unless the user shares it.

## Contents

| File | Columns |
|---|---|
| `meta.json` | `exportedAt` (ISO instant), `appVersion`, `databaseVersion`, `retentionDays`, `notBadApps`, a note on units |
| `usage_sessions.csv` | `id, packageName, startEpochMs, endEpochMs, dayLocal` — every per-app session, midnight-split |
| `daily_usage.csv` | `dayLocal, packageName, totalMs, sessionCount` — the per-app daily rollup |
| `blocks.csv` | `id, name, enabled, daysMask, startMinuteOfDay, endMinuteOfDay, maxBurstSeconds, exemptPackages (;-joined), createdAt, updatedAt` |
| `block_events.csv` | `id, packageName, ruleId, timestampEpochMs, reason, outcome` |
| `challenges.csv` | `id, name, startDay, endDay, goalMinutesPerDay, createdAt` |
| `challenge_entries.csv` | `id, challengeId, startEpochMs, endEpochMs, dayLocal, manual` |

Timestamps are epoch milliseconds (UTC); `dayLocal` / `startDay` / `endDay` are local calendar
days (`2026-11-01`). CSV is RFC 4180: a field is quoted only when it contains a comma, a quote or
a newline (`DataExporter.Csv`, tested). The file name is `dift-export-<date>.zip`.

## Why a zip of CSVs

Spreadsheets open them directly, every table keeps its own header, and the format is stable
across app versions — a column is only ever added. `meta.json` records which app and database
version wrote the file.

## Storage and retention (same Settings section)

The section shows the on-device database size. Usage history is **kept for all years**: the
nightly rollup no longer prunes `usage_sessions` / `daily_usage` unless `retentionDays` is set
(> 0; default `KEEP_FOREVER`). Growth is roughly 5–10 MB per year of sessions on a typical phone
— the per-app daily rollup is a few hundred KB per year. Only the block-event audit log is capped
(365 days). Clearing history is the user's explicit act: uninstalling, or a future retention
setting — never an update.
