# Feature spec: challenges (timed daily goals)

The third feature, and the first *positive* one: a **challenge** is a named period (a month, a
week, any range) with a **daily time goal** — "Mobility, November, 30 min per day". Dift times
the activity, keeps the running total, and shows the sessions next to screen time in the
overview, so the phone's ledger has an entry on the other side.

## A challenge

`Challenge` (`challenges` table, docs/DATA_MODEL.md):

- **name** — free text ("Mobility").
- **startDay / endDay** — inclusive local calendar days. Any length: the editor offers "Whole
  month" and "One week" presets from the start day, but the end is freely pickable.
- **goalMinutesPerDay** — default **30**, 5-minute steps, 5 min … 12 h.

Several challenges may exist (one per month, overlapping, back to back). The Challenge tab shows
the one that covers *today* (else the most recent) and lists the others; tapping one makes it
the shown challenge.

## Entries — timed or added in hindsight

Time is logged as `ChallengeEntry` rows (`challenge_entries`): `startEpochMs`, `endEpochMs`, the
local `day` the entry counts toward, and a `manual` flag.

- **Timer**: *Start* stores `(challengeId, startedAt)` in DataStore — nothing else — so a
  running timer survives process death and reboot. *Stop* writes one entry `[startedAt, now)`
  attributed to the start day. Only one timer runs at a time; while it runs for challenge A,
  challenge B's Start is disabled with a hint.
- **Add time** (hindsight): pick a day within the challenge and a number of minutes (default:
  the goal). Stored as a manual entry anchored at **noon** of that day with the given duration,
  so day totals and averages treat it exactly like timed minutes. Manual entries have no real
  clock position, so they appear in the totals bars but **not** as spans in the night view.
- Entries are listed newest-first under the challenge, each deletable (no confirmation — it is
  one tap to re-add).

## Progress — the goal is an *average*

`ChallengeProgress.snapshot(challenge, dayTotals, today)` (pure, tested) computes:

| Value | Definition |
|---|---|
| `todayMs` | today's logged time (incl. the running timer, added by the caller) |
| `totalMs` | all logged time inside the challenge period |
| `elapsedDays` | days from `startDay` to `min(today, endDay)` inclusive; 0 before the start |
| `targetSoFarMs` | `elapsedDays × goal` — where you *should* be |
| `balanceMs` | `totalMs − targetSoFarMs`; ≥ 0 means on track. **25 min one day, 35 the next is fine.** |
| `averagePerDayMs` | `totalMs / elapsedDays` |
| `remainingDays` | today … `endDay` inclusive (today counts — you can still log it) |
| `neededPerRemainingDayMs` | `max(0, targetTotal − total) / remainingDays` — what it takes from here |

The tab shows: the live timer, **Today** (vs goal, bar), **Total** (vs the whole period's
target, bar), **Average per day** with the signed balance, and **days left · needed per day**.

## In the usage overview

Timed sessions (and the running timer) are drawn in **light yellow** (`#FDE047` dark;
goldenrod `#B8860B` in light mode, where a light yellow has no contrast — chosen to stay far
from the teal night field, which already reads green-ish, and CVD-separable from both usage
colors):

- **Nights**: a narrow strip on the right edge of the column at the session's real time.
- **Daily totals**: a narrow yellow bar beside each day's usage stack (manual entries included).

The legend gains a tappable **Challenge** entry: tapping it hides/shows the yellow layer
(persisted: `overview_show_challenge`). Screen-time numbers never include challenge time.

## Out of scope (deliberately)

- No notifications/reminders, no streaks, no badges. The number is the feedback.
- Timed sessions crossing midnight count toward the day they started (no splitting).
