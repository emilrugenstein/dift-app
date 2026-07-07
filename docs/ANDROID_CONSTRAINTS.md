# Android platform constraints (read before touching `system/`, the manifest, or permissions)

This file exists because LLMs reliably hallucinate outdated Android policy. Everything below
was verified against Android 15/16 (API 35/36) behavior. If code contradicts this document,
the code is wrong; if reality contradicts it, update the document in the same PR.

## Killing / blocking apps

- **You cannot kill another app's foreground process without root.**
  `ActivityManager.killBackgroundProcesses` only affects *your own* packages since API 34;
  `FORCE_STOP_PACKAGES` is signature-level. Do not try. The ceiling is:
  1. cover the app with a full-screen `TYPE_APPLICATION_OVERLAY` window that consumes touches;
  2. `performGlobalAction(GLOBAL_ACTION_HOME)` from the accessibility service.
- **Never launch an Activity to block (ADR-0003).** Background-activity-launch (BAL)
  restrictions make service-initiated `startActivity` unreliable on 15/16; the
  SYSTEM_ALERT_WINDOW exemption is being phased out. The same applies to the fallback
  (polling) mode: no home-kick there either — starting the HOME intent from a background
  service is itself a BAL. Fallback blocking = overlay only. Do not "fix" this.
- **Overlays lose on some system screens.** Settings sub-screens, permission dialogs, and the
  installer set `FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS`; another app's later-attached overlay
  can z-order above ours. Consequences: the accessibility toggle can never be blocked (the OS
  guarantees the owner an escape hatch — this is accepted product behavior, see
  docs/features/blocking.md), and home-kick is the primary weapon with the overlay as the
  visual explanation. Re-fire enforcement on every window event while a verdict holds.

## Foreground-app detection

- Primary: `AccessibilityService` with `typeWindowStateChanged` events.
  Keep the config minimal: `canRetrieveWindowContent="false"`, feedback generic. Dift never
  reads screen content (CLAUDE.md invariant #8).
- `TYPE_WINDOW_STATE_CHANGED` fires for non-app windows too: IMEs, `com.android.systemui`
  (shade, volume), dialogs, **our own overlay**. Filter before acting (see
  `ForegroundAppTracker` notes in ARCHITECTURE.md).
- Fallback: poll `UsageStatsManager.queryEvents()` (~1 s cadence) **only while the screen is
  interactive**; expect occasional 1–3 s event latency. Use `ACTIVITY_RESUMED` /
  `ACTIVITY_PAUSED` / `ACTIVITY_STOPPED` — `MOVE_TO_FOREGROUND`/`MOVE_TO_BACKGROUND` are
  deprecated.
- The system rebinds a crashed accessibility service, but the user can disable it in Settings.
  Detect via `onUnbind`/`onDestroy` + a periodic watchdog reading
  `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`; notify instead of fighting it.

## Permissions & special access

| Access | Mechanism | Notes |
|---|---|---|
| Usage stats | `PACKAGE_USAGE_STATS` (special) | Check `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS)`; grant via `Settings.ACTION_USAGE_ACCESS_SETTINGS`. Needs `tools:ignore="ProtectedPermissions"` in the manifest. |
| Overlay | `SYSTEM_ALERT_WINDOW` (special) | `Settings.canDrawOverlays()`; grant via `ACTION_MANAGE_OVERLAY_PERMISSION`. |
| Accessibility | user toggle in Settings | See restricted-settings gate below. |
| Notifications | `POST_NOTIFICATIONS` (runtime, 13+) | FGS runs even if denied; the notification is just invisible. |
| Battery | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Play-prohibited, irrelevant for sideload. Fairphone OS is near-AOSP: no vendor task killer. |
| App list | `QUERY_ALL_PACKAGES` | Without it package-visibility filtering empties the app picker on API 30+. |

## Restricted settings (Android 13+) — WILL hit this app

APKs installed by session-based installers (Obtainium, file managers) get accessibility
blocked with "Restricted setting". The unlock flow, which MUST be scripted verbatim in
onboarding because it is undiscoverable:

1. Try to enable the accessibility service → system refuses with "Restricted setting".
2. Open App info for Dift → top-right **⋮** menu → **Allow restricted settings**
   (this menu item only appears *after* step 1's failed attempt).
3. Enable the accessibility service again — now it works.

`adb install` bypasses the gate entirely — fine for development, but **release testing must
go through the real Obtainium install path** or the gate stays untested. Never enable
Android 16 Advanced Protection Mode on the device; it hard-blocks sideloaded accessibility.

## Foreground services (Android 14+/15)

- Type is mandatory. Dift uses `specialUse`: `FOREGROUND_SERVICE_SPECIAL_USE` permission +
  `android:foregroundServiceType="specialUse"` + a `<property
  android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="..."/>` child.
- Android 15 bans several FGS types from `BOOT_COMPLETED` starts; **`specialUse` is not
  banned** — the boot receiver may legally start the fallback monitor. The accessibility
  service needs no boot handling at all (the system rebinds it itself).

## Usage stats accuracy

- Derive sessions from `queryEvents`, never from `queryUsageStats` daily buckets (inaccurate).
- Consume `SCREEN_NON_INTERACTIVE` and `DEVICE_SHUTDOWN` to close dangling sessions.
- The system retains raw events for only a handful of days → ingest incrementally into Room
  with a persisted checkpoint; ingestion must be idempotent (WorkManager retries).
- Expect small deltas vs. Digital Wellbeing (different accounting); target within ~5%.

## API 35/36 UI notes

- Edge-to-edge is enforced at targetSdk 35: use Scaffold insets everywhere, including inside
  the overlay window (`safeDrawingPadding`).
- The overlay window must stay focusable (no `FLAG_NOT_FOCUSABLE`) so the FRICTION flow can
  open the soft keyboard; swallow BACK inside the overlay view.
