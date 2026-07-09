# Feature spec: usage overview (night-aligned week chart)

The second core feature. One screen, two views over a selectable ISO week (Monday-anchored),
plus a week pager.

## Night view (default) — inferred sleep

The insight: line the day's usage **sessions up vertically** and place seven nights side by
side, so the dark gap in the middle of each column *is* the night's sleep.

- **Y axis = time of day, midnight in the centre.** Each column spans **noon → noon**
  (`[eveningDate 12:00, morningDate 12:00)`), so local midnight sits exactly halfway down
  (minute 720 of 1440).
- **X axis = the seven nights of the week**, left → right, starting with **Sunday → Monday**
  (the night whose morning is the ISO week's Monday) and ending Saturday → Sunday. Column *i*
  covers the night *before* weekday *i*; labels are the two-letter morning day.
- **Usage spans** (solid) — every stretch the phone was actively used, drawn at its time in the
  column. Per-app sessions are merged into device-usage intervals first (gaps ≤ 60 s are treated
  as continuous), so app-switching doesn't fragment the picture.
- **Sleep span** (translucent, behind the usage spans) — the **longest no-use gap** inside the
  column. This is the inferred sleep interval "from the last phone usage to the first". It works
  because the alarm app wakes the screen every morning, marking the first-use edge. Capped at the
  full 24 h column (nights with no usage show a full-height sleep span).

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
