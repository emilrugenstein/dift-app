# Feature spec: permissions onboarding (M1, extended M2)

A sequential checklist screen; each item shows live status (granted/missing) via
`PermissionsChecker` (one `StateFlow<PermissionsState>`), a one-line why, and a button that
deep-links to the right Settings screen. Re-checked on every resume. The app is usable
read-only without blocking permissions — onboarding never dead-ends.

## Steps in order

1. **Usage access** (M1) — `ACTION_USAGE_ACCESS_SETTINGS`; checked via AppOps.
2. **Notifications** (M1) — runtime `POST_NOTIFICATIONS` prompt.
3. **Display over other apps** (M2) — `ACTION_MANAGE_OVERLAY_PERMISSION`.
4. **Accessibility service** (M2) — the critical, scripted step. Because sideloaded installs
   hit the Android 13+ restricted-settings gate, the UI must walk through *verbatim*:
   1. Tap "Open accessibility settings" and try to enable Dift → Android refuses
      ("Restricted setting").
   2. Back in Dift, tap "Open app info" → top-right ⋮ → **Allow restricted settings**
      (the menu item only exists after the failed attempt in step 1).
   3. Open accessibility settings again and enable Dift — now it works.
5. **Battery optimization exemption** (M2) — `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

`onboardingCompleted` flips when steps 1–2 are granted; steps 3–5 re-surface as a banner on
the dashboard while missing (blocking features stay disabled until granted).
