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
- **Usage spans** (solid) — every stretch the phone was actively used, drawn at its time in the
  column. **"Not bad" apps** (chosen in Settings) draw in the accent blue nudged darker;
  everything else draws in a plum-leaning **dark violet** (its own step per mode — pure violets
  are CVD-indistinguishable from the blue). Per-app sessions are merged into device-usage
  intervals per category (gaps ≤ 60 s are treated as continuous), so app-switching doesn't
  fragment the picture; a small legend below the chart names the two colors.
- **Night span** (bright turquoise field, fairly opaque, behind the usage spans; a darker teal
  step in light mode) — the **longest** no-use gap that **intersects 03:30–06:00**
  (`NIGHT_WINDOW_START/END_MINUTE`). A window rather than a single anchor makes the pick
  dynamic: use running past midnight only *delays* the night's start, and a brief 4 a.m.
  wake-up no longer erases the night — the longer gap around it wins. A night with use across
  the *whole* window has no clear span. Capped at the full 24 h column (past nights with no
  usage show a full-height span). **Future nights show nothing**: a column whose 04:30 has not
  yet happened has no night span (`buildWeek` takes `nowMs`).
- **Tap a night span** to select it: the span brightens and a detail block appears below the
  chart labelled "Night · Su → Mo", with the duration in large type and the start–end times.
  Tapping it again (or tapping empty space) dismisses.

`NightTimeline.buildWeek(...)` is pure: it takes merged device-usage intervals + the week's
Monday + zone and returns seven `NightColumn`s (usage spans + sleep span, all in
minutes-from-noon). No Android, no wall-clock — tested in `NightTimelineTest`.

## Totals view (toggle) — traditional daily totals

A switch flips to a **Monday-start** bar chart: total screen time per calendar day, Monday →
Sunday of the same ISO week, built from the `daily_usage` rollup.

- **Stacked by category**: the "Not bad" share (blue) sits on the baseline, everything else
  (violet) stacks above it with a 2 px surface gap; the day's total is labeled above each stack.
- **Comparable across weeks**: the y-scale is anchored at a **5 h** floor (it only grows when a
  day exceeds it), and the chart is taller than before, so paging through weeks keeps bar
  heights meaningful.
- **Average line**: a dotted horizontal line marks the week's usage per day. Below the bars the
  average is written out ("Ø 2h 41m per day") together with a signed percent change **vs last
  week** and **vs the previous 30 days** (`UsageAverages`, pure + tested). Denominators only
  count days that can have data: the current week stops at today, and days before the first
  recorded data are excluded, so a fresh install doesn't fake a drop.

## "Not bad" apps (Settings)

A searchable app list in Settings (below the permissions) marks apps as **"Not bad"** — the
usage you don't mind. The set is stored in DataStore (`not_bad_apps`), colors both chart views
(blue vs violet), and is **preselected as the exempt list when creating a new block**
(docs/features/usage-debt.md).

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
