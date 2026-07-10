# Feature spec: usage overview (night-aligned week chart)

The second core feature. One screen, two views over a selectable ISO week (Monday-anchored),
plus a week pager.

## Night view (default) — inferred sleep

The insight: line the day's usage **sessions up vertically** and place seven nights side by
side, so the dark gap in the middle of each column *is* the night's sleep.

- **Y axis = time of day, midnight in the centre.** Each column spans **noon → noon**
  (`[eveningDate 12:00, morningDate 12:00)`), so local midnight sits exactly halfway down
  (minute 720 of 1440) — marked by a dashed line.
- **X axis = the seven nights of the week**, left → right, starting with **Sunday → Monday**
  (the night whose morning is the ISO week's Monday) and ending Saturday → Sunday. Each column
  is labeled with **both days**: the evening day above (dimmed) and the morning day below, so a
  column reads "Su ↓ Mo".
- **Usage spans** (solid, accent color) — every stretch the phone was actively used, drawn at
  its time in the column. Per-app sessions are merged into device-usage intervals first (gaps
  ≤ 60 s are treated as continuous), so app-switching doesn't fragment the picture.
- **Sleep span** (translucent teal, behind the usage spans) — the no-use gap **containing
  04:30** (`SLEEP_ANCHOR_MINUTE`). The anchor is 04:30, not midnight: scrolling past 00:00 must
  only *delay* the sleep start, not erase the night, and nobody is deliberately on the phone at
  04:30. The gap runs from the last use before the anchor to the first use after (the morning
  alarm marks that edge); a night with use *across* 04:30 has no clear sleep. Capped at the full
  24 h column (nights with no usage show a full-height sleep span).
- **Tap a sleep span** to select it: the span brightens and a detail block appears below the
  chart with the night ("Su → Mo"), the start–end times, and the duration in large type.
  Tapping it again (or tapping empty space) dismisses.

`NightTimeline.buildWeek(...)` is pure: it takes merged device-usage intervals + the week's
Monday + zone and returns seven `NightColumn`s (usage spans + sleep span, all in
minutes-from-noon). No Android, no wall-clock — tested in `NightTimelineTest`.

## Totals view (toggle) — traditional daily totals

A switch flips to a plain **Monday-start** bar chart: total screen time per calendar day,
Monday → Sunday of the same ISO week. Reuses the `daily_usage` rollup.

## Week pager

Left/right arrows move one ISO week at a time. "Next" is disabled once the current week is
reached; "Previous" is disabled once there is no earlier data (`UsageDao.earliestSessionDay()`).
The header shows the week's date range.

## Data

- `UsageDao.sessionsInRange(fromMs, toMs)` — closed sessions overlapping the window, for the
  night view (already midnight-split by the `SessionDeriver`, but the night view re-places them
  by absolute time so splits are invisible).
- `UsageDao.observeDayTotals(fromDayLocal)` — existing rollup query, for the totals view.
- `UsageDao.earliestSessionDay()` — bounds the pager.
