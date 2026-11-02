# On-device smoke test (Fairphone 6)

CI cannot exercise accessibility, overlays, or special permissions. Before tagging a release
(and after any change under `system/`), run the relevant items below on the real device.
Install via **Obtainium from a GitHub Release** — `adb install` bypasses the
restricted-settings gate and masks the most important failure mode.

Each item: ☐ = untested for this release. Expected result in *italics*.

## Setup, migration & overview

1. ☐ **Install/update loop**: update from the previous version via Obtainium.
   *In-place update succeeds; no uninstall prompt; usage history preserved.*
2. ☐ **v1 → v2 migration**: a device that had night mode enabled shows one **Block** in the
   Blocks tab after updating; any old always/limit/schedule test rules are gone. *No crash on
   first launch (the migration ran).*
2a. ☐ **v2 → v3 migration (v0.4)**: updating in place keeps blocks, "Not bad" apps and usage
    history; the new Challenge tab opens empty. *No crash on first launch (AutoMigration added
    the two challenge tables).*
3. ☐ Usage-access onboarding opens the right Settings screen and detects the grant.
4. ☐ **Overview → Nights**: past columns show a bright turquoise night span — the longest
   no-use gap touching 03:30–06:00 (a brief 4 a.m. wake-up must NOT end the night; use past
   midnight only delays its start); future columns (04:30 not yet reached) show none; day
   pair labeled above/below each column (e.g. Su over Mo). Usage spans: "Not bad" apps in the
   darker accent blue, everything else violet; legend below the chart. Tapping a night span
   brightens it and shows "Night · Su → Mo" with duration and start–end below the chart;
   tapping again dismisses. Week pager: "Previous" stops at the oldest data, "Next" disabled
   on the current week.
5. ☐ **Overview → Daily totals** toggle: Monday-start stacked bars (blue "not bad" share below,
    violet above); y-scale anchored at 5 h (bars stay comparable when paging weeks); dotted
    average line with "Ø … per day" and % vs last week / vs last 30 days below the bars.
    Today's total within a few % of Digital Wellbeing *at the same moment* (Dift counts the
    in-progress session live). If a gap remains, compare per-app numbers to find the culprit.
5a. ☐ **Settings → "Not bad" apps**: search filters the list; ticking an app immediately
    recolors it blue in both chart views; creating a new block preselects the ticked apps as
    exempt (an existing block's exemptions stay untouched).

## Usage-debt blocking

6. ☐ Create a block whose window includes *now* (60 s cap). Use a normal app continuously.
   *Corner ring fills over ~60 s, then the full-screen lockout appears with a live countdown.*
7. ☐ Use for X < 60 s, then lock; unlock a few seconds later. *Corner shows the "blocked" lock
   and drains; opening an app shows the lockout with ≈ X s remaining (minus the time locked).*
8. ☐ Cooldown persists across a lock **and a reboot** without opening Dift.
9. ☐ During a cooldown (phone unlocked), press home and open recents. *The full-screen lockout
   covers both — no browsing app previews. Opening Dift itself lifts the overlay while inside;
   the dialer and a block's exempt apps stay usable; locking the phone ends the standoff (the
   cooldown keeps draining).*
9a. ☐ **Hang safety net**: with a block window active, use a normal app for ~6 min straight
    past several lockout cycles. *The corner ring never freezes mid-fill for more than ~5 min;
    if detection ever hiccups, it recovers by itself (the 5-minute resync).*
10. ☐ **Exemptions**: add an app to a block's exempt list; use it inside the window. *Not
    blocked, and the ring stays frozen (no debt accrues) — switching back to a normal app
    resumes filling from where it paused.*
11. ☐ **Strictest wins**: two overlapping enabled blocks (60 s and 30 s). *The 30 s cap applies.*
12. ☐ While the phone is **locked**: SOS/emergency call, flashlight tile, lockscreen camera all
    work (enforcement is unlock-gated).
13. ☐ Dialer, launcher, and keyboard are never blocked mid-cooldown (SafetyDenylist).
14. ☐ Disabling the accessibility service triggers the watchdog notification; fallback polling
    still raises the lockout (overlay-only; the corner indicator may lag by ~1 s).

## Data (v0.4.1)

2b. ☐ **Settings → Data**: the database size is shown; *Export data* opens the system file
    picker with `dift-export-<date>.zip` preselected; saving to Downloads shows "Export saved."
    and the zip opens with `meta.json` + 6 CSV files whose row counts match the app (e.g.
    `challenges.csv` lists every challenge). *Nothing else changes.*
2c. ☐ **History survives**: after the update, the Overview still pages back to the oldest week
    it showed before; a week older than one year (once you have one) is still there the morning
    after the nightly rollup.

## Challenges (v0.4)

16. ☐ **Create**: Challenge tab → New → name "Mobility", default period = this month, "One week"
    / "Whole month" presets adjust the end, goal −/+ in 5-min steps → Save. *The tab shows the
    challenge with 00:00, Today 0m of 30m, Total 0m of (days × 30 min).*
17. ☐ **Timer**: Start, lock the phone, wait a minute, unlock, kill Dift from recents, reopen.
    *The timer is still running with the right elapsed time; Stop logs one entry with the real
    start–end and Today/Total/Average update.*
18. ☐ **Average goal**: add 25 min to yesterday and 35 min to today via "Add time" (date picker
    is bounded to the period and today). *Average per day reads 30m · "0m ahead"; a day with 10
    min shows "behind" in red and a higher "per day to finish".*
19. ☐ **Second challenge**: create another one (a week); with a timer running for the first, the
    second's Start is disabled with a hint; "Other challenges" rows switch the shown challenge.
    Delete asks for confirmation and removes its entries.
20. ☐ **Overview**: timed sessions appear as a green strip in the Nights view and a green side
    bar in Daily totals (manual entries only in totals); screen-time numbers are unchanged; tapping
    the "Challenge" legend entry hides the green layer (dimmed legend) and survives a restart.

## Block editor & list

15. ☐ Editor: all seven day chips visible (no cutoff); −/+ nudge start/end by 15 min and
    tapping the time opens the time-picker dialog; the app search field filters the exempt
    list. Blocks list: Delete asks for confirmation; while an enabled block's window is
    running, both Delete and the enable switch are locked (a disabled block stays editable).

## Always

21. ☐ Battery: overnight drain with monitoring active is comparable to before (< a few %).
