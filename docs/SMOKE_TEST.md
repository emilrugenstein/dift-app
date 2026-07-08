# On-device smoke test (Fairphone 6)

CI cannot exercise accessibility, overlays, or special permissions. Before tagging a release
(and after any change under `system/`), run the relevant items below on the real device.
Install via **Obtainium from a GitHub Release** — `adb install` bypasses the
restricted-settings gate and masks the most important failure mode.

Each item: ☐ = untested for this release. Expected result in *italics*.

All of M1–M5 is implemented; every item below is now active and untested on-device.

## Setup & usage

1. ☐ **Install/update loop**: install the release APK via Obtainium; later install the next
   version. *In-place update succeeds; no uninstall prompt; app data preserved.*
2. ☐ Usage-access onboarding step opens the right Settings screen and detects the grant.
3. ☐ Dashboard's "today" total within ~5% of Digital Wellbeing after 3 days of use; the
   7/30-day toggle switches the trend range.

## Blocking

4. ☐ Accessibility onboarding incl. the restricted-settings walkthrough works as scripted.
5. ☐ **First blocking overlay** (replaces the old M0 spike): block an app from the Apps tab,
   open it. *Full-screen overlay appears within ~500 ms (cold, warm, and re-entry after
   home-kick); for a FRICTION rule the phrase field takes focus and the keyboard opens.*
6. ☐ Home-kick fires; overlay explains the block; denylisted apps (dialer) never blocked.
7. ☐ Blocking still active after reboot without opening Dift.
8. ☐ Disabling the accessibility service triggers the watchdog notification; fallback
   polling mode still blocks (overlay-only).

## Night usage-debt

9. ☐ Inside the window: continuous use hits the 60 s cap → countdown overlay + home-kick.
10. ☐ Burst of X < 60 s, lock, unlock → countdown shows remaining debt ≈ X s.
11. ☐ Locking/rebooting mid-debt does not clear the debt.
12. ☐ While locked: SOS/emergency call, flashlight tile, lockscreen camera all work.
13. ☐ Dialer opens normally mid-debt (denylist hole in the overlay logic).

## Rules

14. ☐ FRICTION unblock: wait timer counts down, phrase must match, keyboard works in overlay.
15. ☐ HARD block: no unblock path; live countdown; ends exactly at window end / midnight.
16. ☐ Daily limit fires mid-session (not only on app switch).
17. ☐ Rule editor: create/edit each type; SafetyDenylist apps are not offered in the picker.

## Always

18. ☐ Battery: overnight drain with monitoring active is comparable to before (< a few %).
