# On-device smoke test (Fairphone 6)

CI cannot exercise accessibility, overlays, or special permissions. Before tagging a release
(and after any change under `system/`), run the relevant items below on the real device.
Install via **Obtainium from a GitHub Release** — `adb install` bypasses the
restricted-settings gate and masks the most important failure mode.

Each item: ☐ = untested for this release. Expected result in *italics*.

## M0 (current)

1. ☐ **Overlay spike**: Home → grant overlay permission → "Start overlay spike".
   *Full-screen overlay appears; text field takes focus; soft keyboard opens; typing works;
   Close removes overlay and the notification disappears.*
2. ☐ **Install/update loop**: install release APK via Obtainium; later install the next
   version. *In-place update succeeds; no uninstall prompt; app data preserved.*

## M1 — usage tracking (activate once implemented)

3. ☐ Usage-access onboarding step opens the right Settings screen and detects the grant.
4. ☐ Dashboard's "today" total within ~5% of Digital Wellbeing after 3 days of use.

## M2 — blocking (activate once implemented)

5. ☐ Accessibility onboarding incl. the restricted-settings walkthrough works as scripted.
6. ☐ Blocked app shows the overlay within ~500 ms of opening (cold start, warm start, and
   immediate re-entry after home-kick).
7. ☐ Home-kick fires; overlay explains the block; denylisted apps (dialer) never blocked.
8. ☐ Blocking still active after reboot without opening Dift.
9. ☐ Disabling the accessibility service triggers the watchdog notification; fallback
   polling mode still blocks (overlay-only).

## M3 — night usage-debt (activate once implemented)

10. ☐ Inside the window: continuous use hits the 60 s cap → countdown overlay + home-kick.
11. ☐ Burst of X < 60 s, lock, unlock → countdown shows remaining debt ≈ X s.
12. ☐ Locking/rebooting mid-debt does not clear the debt.
13. ☐ While locked: SOS/emergency call, flashlight tile, lockscreen camera all work.
14. ☐ Dialer opens normally mid-debt (denylist hole in the overlay logic).

## M4+ — rules (activate once implemented)

15. ☐ FRICTION unblock: wait timer counts down, phrase must match, keyboard works in overlay.
16. ☐ HARD block: no unblock path; ends exactly at window end / midnight.
17. ☐ Daily limit fires mid-session (not only on app switch).

## Always

18. ☐ Battery: overnight drain with monitoring active is comparable to before (< a few %).
