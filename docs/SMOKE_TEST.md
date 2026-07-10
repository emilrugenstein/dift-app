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
3. ☐ Usage-access onboarding opens the right Settings screen and detects the grant.
4. ☐ **Overview → Nights**: past columns show a bright turquoise night span around 04:30 (use
   past midnight only delays its start); future columns (04:30 not yet reached) show none; day
   pair labeled above/below each column (e.g. Su over Mo); usage spans in the darker accent
   blue. Tapping a night span brightens it and shows "Night · Su → Mo" with duration and
   start–end below the chart; tapping again dismisses. Week pager: "Previous" stops at the
   oldest data, "Next" disabled on the current week.
5. ☐ **Overview → Daily totals** toggle: Monday-start bars; today's total within a few % of
    Digital Wellbeing *at the same moment* (Dift now counts the in-progress session live).
    If a gap remains, compare per-app numbers to find the culprit app.

## Usage-debt blocking

6. ☐ Create a block whose window includes *now* (60 s cap). Use a normal app continuously.
   *Corner ring fills over ~60 s, then the full-screen lockout appears with a live countdown.*
7. ☐ Use for X < 60 s, then lock; unlock a few seconds later. *Corner shows the "blocked" lock
   and drains; opening an app shows the lockout with ≈ X s remaining (minus the time locked).*
8. ☐ Cooldown persists across a lock **and a reboot** without opening Dift.
9. ☐ During a cooldown, press home. *Home is reachable; the corner "blocked" pip is visible;
   opening any (non-exempt) app re-raises the full-screen lockout.*
10. ☐ **Exemptions**: add an app to a block's exempt list; use it inside the window. *Not
    blocked, and the ring stays frozen (no debt accrues) — switching back to a normal app
    resumes filling from where it paused.*
11. ☐ **Strictest wins**: two overlapping enabled blocks (60 s and 30 s). *The 30 s cap applies.*
12. ☐ While the phone is **locked**: SOS/emergency call, flashlight tile, lockscreen camera all
    work (enforcement is unlock-gated).
13. ☐ Dialer, launcher, and keyboard are never blocked mid-cooldown (SafetyDenylist).
14. ☐ Disabling the accessibility service triggers the watchdog notification; fallback polling
    still raises the lockout (overlay-only; the corner indicator may lag by ~1 s).

## Block editor & list

15. ☐ Editor: all seven day chips visible (no cutoff); −/+ nudge start/end by 15 min and
    tapping the time opens the time-picker dialog; the app search field filters the exempt
    list. Blocks list: Delete asks for confirmation; while an enabled block's window is
    running, both Delete and the enable switch are locked (a disabled block stays editable).

## Always

16. ☐ Battery: overnight drain with monitoring active is comparable to before (< a few %).
